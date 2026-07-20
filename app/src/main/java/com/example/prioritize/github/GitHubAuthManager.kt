package com.example.prioritize.github

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages GitHub authentication via the OAuth Device Flow.
 *
 * Why Device Flow and not Authorization Code + PKCE?
 * GitHub OAuth Apps require a client_secret for the code exchange step, which
 * cannot safely be embedded in an APK. The Device Flow achieves identical UX —
 * the verification_uri_complete URL pre-fills the user code so the user only
 * needs to tap "Authorize Prioritize" in their browser, with no manual code entry.
 * No client_secret is ever required or embedded.
 *
 * Token storage: stored in standard SharedPreferences. The token grants only
 * `public_repo` scope (create issues on public repos) — lowest viable privilege.
 */
class GitHubAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "GitHubAuthManager"
        const val CLIENT_ID = "Ov23liMNXO1UOTO68he4"
        private const val SCOPE = "public_repo"
        private const val PREFS_NAME = "github_auth_prefs"
        private const val KEY_TOKEN = "gh_access_token"
        private const val KEY_USERNAME = "gh_username"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val isLoggedIn: Boolean
        get() = prefs.getString(KEY_TOKEN, null) != null

    val username: String?
        get() = prefs.getString(KEY_USERNAME, null)

    val accessToken: String?
        get() = prefs.getString(KEY_TOKEN, null)

    /**
     * Initiates the GitHub Device Flow:
     * 1. Requests a device_code + user_code from GitHub.
     * 2. Opens the verification URI in the browser. If GitHub returns
     *    verification_uri_complete, the code is pre-filled. Otherwise the user
     *    must enter [onUserCodeReady]'s code manually at github.com/login/device.
     * 3. Polls GitHub until the user authorizes or the code expires.
     * 4. Fetches the authenticated username and stores token + username.
     *
     * @param onUserCodeReady Called immediately before the browser is opened,
     *   with the human-readable code (e.g. "AB12-CD34") the user must enter.
     *   Use this to show a snackbar / dialog in case the browser doesn't pre-fill.
     *
     * Should be called from a coroutine scope. Returns [DeviceFlowResult].
     */
    suspend fun startDeviceFlow(onUserCodeReady: (String) -> Unit = {}): DeviceFlowResult = withContext(Dispatchers.IO) {
        // Step 1: Request device and user codes
        val deviceCodeResponse = requestDeviceCode()
            ?: return@withContext DeviceFlowResult.Error("Failed to contact GitHub. Check network connection.")

        val deviceCode = deviceCodeResponse.optString("device_code")
        val userCode = deviceCodeResponse.optString("user_code")
        // GitHub may return either verification_uri_complete (pre-filled code) or just
        // verification_uri (user must enter the code manually). Fall back gracefully.
        val verificationUriComplete = deviceCodeResponse.optString("verification_uri_complete").ifBlank {
            deviceCodeResponse.optString("verification_uri") // fallback
        }
        val interval = deviceCodeResponse.optInt("interval", 5)
        val expiresIn = deviceCodeResponse.optInt("expires_in", 900)

        if (deviceCode.isBlank() || verificationUriComplete.isBlank()) {
            return@withContext DeviceFlowResult.Error("Invalid response from GitHub: missing device_code or verification_uri.")
        }

        // Notify caller of the user code before opening the browser
        withContext(Dispatchers.Main) { onUserCodeReady(userCode) }

        // Step 2: Open verification URL — if complete URL available, code is pre-filled.
        // Otherwise user must enter userCode manually at verification_uri.
        Log.i(TAG, "Opening GitHub verification URI. userCode=$userCode uri=$verificationUriComplete")
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(verificationUriComplete)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        // Step 3: Poll for token until user authorizes or code expires
        val startTime = System.currentTimeMillis()
        val timeoutMs = expiresIn * 1000L
        var currentInterval = interval

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(currentInterval * 1000L)
            when (val poll = pollForToken(deviceCode)) {
                is PollResult.Success -> {
                    val userInfo = fetchUserInfo(poll.token)
                    val resolvedUsername = userInfo?.optString("login") ?: ""
                    prefs.edit()
                        .putString(KEY_TOKEN, poll.token)
                        .putString(KEY_USERNAME, resolvedUsername)
                        .apply()
                    Log.i(TAG, "GitHub login successful for: $resolvedUsername")
                    return@withContext DeviceFlowResult.Success(resolvedUsername, userCode)
                }
                is PollResult.Pending    -> continue
                is PollResult.SlowDown   -> { currentInterval += 5 }
                // Transient network blip (DNS, timeout) — log and retry, don't abort
                is PollResult.NetworkError -> {
                    Log.w(TAG, "Retrying after network error: ${poll.cause}")
                    continue
                }
                is PollResult.Error      -> return@withContext DeviceFlowResult.Error(poll.message)
            }
        }
        DeviceFlowResult.Error("Authorization timed out. Please try again.")
    }

    /** Clears the stored token and username. */
    fun logout() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USERNAME).apply()
        Log.i(TAG, "GitHub account disconnected.")
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun requestDeviceCode(): JSONObject? {
        return try {
            val url = URL("https://github.com/login/device/code")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Prioritize-Android-App")
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
            }
            conn.outputStream.write("client_id=$CLIENT_ID&scope=$SCOPE".toByteArray())
            val code = conn.responseCode
            if (code == 200) JSONObject(conn.inputStream.bufferedReader().readText())
            else { Log.e(TAG, "Device code request returned HTTP $code"); null }
        } catch (e: Exception) {
            Log.e(TAG, "Device code request failed", e)
            null
        }
    }

    private fun pollForToken(deviceCode: String): PollResult {
        return try {
            val url = URL("https://github.com/login/oauth/access_token")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("User-Agent", "Prioritize-Android-App")
                connectTimeout = 15_000  // longer timeout for background network recovery
                readTimeout    = 20_000
                doOutput = true
            }
            val body = "client_id=$CLIENT_ID" +
                "&device_code=$deviceCode" +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
            conn.outputStream.write(body.toByteArray())

            val response = JSONObject(conn.inputStream.bufferedReader().readText())
            when {
                response.has("access_token") ->
                    PollResult.Success(response.getString("access_token"))
                response.optString("error") == "authorization_pending" ->
                    PollResult.Pending
                response.optString("error") == "slow_down" ->
                    PollResult.SlowDown
                response.optString("error") == "expired_token" ->
                    PollResult.Error("Authorization code expired. Please try again.")
                response.optString("error") == "access_denied" ->
                    PollResult.Error("Authorization was denied.")
                else ->
                    PollResult.Error(response.optString("error_description", "Unknown error during poll."))
            }
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Token poll DNS failure — will retry: ${e.message}")
            PollResult.NetworkError("DNS: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Token poll timed out — will retry: ${e.message}")
            PollResult.NetworkError("Timeout: ${e.message}")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Token poll IO error — will retry: ${e.message}")
            PollResult.NetworkError("IO: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Token poll unexpected error", e)
            PollResult.Error(e.message ?: "Unexpected error during authorization poll.")
        }
    }

    private fun fetchUserInfo(token: String): JSONObject? {
        return try {
            val url = URL("https://api.github.com/user")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Prioritize-Android-App")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (conn.responseCode == 200) JSONObject(conn.inputStream.bufferedReader().readText())
            else null
        } catch (e: Exception) {
            Log.e(TAG, "User info fetch failed", e)
            null
        }
    }

    // ── Result types ─────────────────────────────────────────────────────────

    sealed class DeviceFlowResult {
        data class Success(val username: String, val userCode: String = "") : DeviceFlowResult()
        data class Error(val message: String) : DeviceFlowResult()
    }

    private sealed class PollResult {
        data class Success(val token: String) : PollResult()
        object Pending : PollResult()
        object SlowDown : PollResult()
        /** Fatal GitHub API error — stop polling immediately. */
        data class Error(val message: String) : PollResult()
        /** Transient network failure (DNS blip, timeout) — retry silently. */
        data class NetworkError(val cause: String) : PollResult()
    }
}
