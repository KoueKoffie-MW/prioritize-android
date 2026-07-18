package com.example.prioritize.github

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Creates GitHub issues on the Prioritize repository on behalf of an
 * authenticated user. Requires a valid OAuth token from [GitHubAuthManager].
 *
 * Scope required: public_repo (to create issues on a public repository).
 */
class GitHubIssueService {

    companion object {
        private const val TAG = "GitHubIssueService"
        const val REPO_OWNER = "KoueKoffie-MW"
        const val REPO_NAME = "prioritize-android"
    }

    /**
     * Creates a GitHub issue on the Prioritize repo.
     *
     * @param token  OAuth access token from [GitHubAuthManager].
     * @param title  Issue title.
     * @param body   Issue body (supports Markdown).
     * @param labels List of label names to apply (must already exist on the repo).
     * @return The HTML URL of the created issue, or null if the request failed.
     */
    suspend fun createIssue(
        token: String,
        title: String,
        body: String,
        labels: List<String> = emptyList()
    ): IssueResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/issues")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
            }

            val payload = JSONObject().apply {
                put("title", title)
                put("body", body)
                if (labels.isNotEmpty()) put("labels", JSONArray(labels))
            }
            conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))

            val responseCode = conn.responseCode
            return@withContext if (responseCode == 201) {
                val response = JSONObject(conn.inputStream.bufferedReader().readText())
                val issueUrl = response.getString("html_url")
                val issueNumber = response.getInt("number")
                Log.i(TAG, "Issue #$issueNumber created: $issueUrl")
                IssueResult.Success(issueUrl, issueNumber)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                Log.e(TAG, "Issue creation failed ($responseCode): $error")
                IssueResult.Error("GitHub returned HTTP $responseCode. Check your connection or token.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Issue creation exception", e)
            IssueResult.Error(e.message ?: "Network error. Please try again.")
        }
    }

    sealed class IssueResult {
        data class Success(val url: String, val number: Int) : IssueResult()
        data class Error(val message: String) : IssueResult()
    }
}
