package com.example.prioritize.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the Google Maven repository for the latest published version of the
 * LiteRT-LM library and reports whether it is newer than the version currently
 * bundled with this build.
 *
 * This is used to notify the user when Google ships a fix for the Tensor G5
 * NPU crash (tracked as GitHub issue #2566 in google-ai-edge/LiteRT-LM).
 *
 * Usage:
 *   val result = LiteRtUpdateChecker.check()
 *   if (result.updateAvailable) {
 *       showNpuUpdateBanner(result.latestVersion)
 *   }
 */
object LiteRtUpdateChecker {

    private const val TAG = "LiteRtUpdateChecker"

    /**
     * The version of LiteRT-LM that is bundled into this APK.
     * Update this constant whenever the Gradle dependency is bumped.
     */
    const val BUNDLED_VERSION = "0.13.1"

    /**
     * The first LiteRT-LM version expected to fully resolve the Tensor G5 NPU
     * crash (libLiteRtDispatch_GoogleTensor.so missing, issue #2566).
     * Update this when Google publishes a confirmed fix.
     */
    const val NPU_FIX_TARGET_VERSION = "0.14.0"

    /** Google Maven metadata XML for litertlm-android. */
    private const val MAVEN_METADATA_URL =
        "https://dl.google.com/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml"

    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS    = 6_000

    /**
     * Result of a version check. All fields are safe to read from any thread
     * after [check] returns.
     */
    data class CheckResult(
        /** Latest version string found on Maven (e.g. "0.14.0"), or null if unreachable. */
        val latestVersion: String?,
        /** True when [latestVersion] is strictly newer than [BUNDLED_VERSION]. */
        val updateAvailable: Boolean,
        /** True when [latestVersion] meets or exceeds [NPU_FIX_TARGET_VERSION]. */
        val npuFixAvailable: Boolean,
        /** Human-readable description of the outcome (for logs / diagnostics UI). */
        val statusMessage: String,
    )

    /**
     * Performs the network request on [Dispatchers.IO].
     * Safe to call from a coroutine on any dispatcher.
     */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val latestVersion = fetchLatestVersion() ?: return@withContext CheckResult(
                latestVersion  = null,
                updateAvailable = false,
                npuFixAvailable = false,
                statusMessage  = "Could not reach Google Maven. Check your internet connection.",
            )

            val updateAvailable = isNewer(latestVersion, BUNDLED_VERSION)
            val npuFixAvailable = !isNewer(NPU_FIX_TARGET_VERSION, latestVersion) // latest >= target

            Log.i(TAG, "Bundled=$BUNDLED_VERSION  Latest=$latestVersion  " +
                "updateAvailable=$updateAvailable  npuFixAvailable=$npuFixAvailable")

            CheckResult(
                latestVersion   = latestVersion,
                updateAvailable = updateAvailable,
                npuFixAvailable = npuFixAvailable,
                statusMessage   = when {
                    npuFixAvailable  -> "LiteRT-LM $latestVersion is available — NPU fix included! " +
                        "Update the app to enable Tensor G5 NPU acceleration."
                    updateAvailable  -> "LiteRT-LM $latestVersion is available — update for bug fixes. " +
                        "NPU fix not yet confirmed in this release."
                    else             -> "LiteRT-LM $BUNDLED_VERSION is the current stable release. " +
                        "No update available yet. NPU fix pending (issue #2566)."
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Version check failed: ${e.message}", e)
            CheckResult(
                latestVersion   = null,
                updateAvailable = false,
                npuFixAvailable = false,
                statusMessage   = "Version check error: ${e.message}",
            )
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun fetchLatestVersion(): String? {
        val conn = (URL(MAVEN_METADATA_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout    = READ_TIMEOUT_MS
            requestMethod  = "GET"
        }
        return try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Maven metadata returned HTTP ${conn.responseCode}")
                return null
            }
            parseLatestFromXml(conn.inputStream)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parses the `<latest>` or `<release>` element from a Maven metadata XML
     * stream without pulling in any third-party XML library.
     *
     * Example Maven metadata shape:
     * ```xml
     * <metadata>
     *   <versioning>
     *     <latest>0.14.0</latest>
     *     <release>0.13.1</release>
     *     <versions> ... </versions>
     *   </versioning>
     * </metadata>
     * ```
     */
    private fun parseLatestFromXml(stream: InputStream): String? {
        val factory = XmlPullParserFactory.newInstance()
        val parser  = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var insideVersioning = false
        var latestText: String? = null
        var releaseText: String? = null
        var currentTag = ""

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name ?: ""
                    if (currentTag == "versioning") insideVersioning = true
                }
                XmlPullParser.TEXT -> {
                    if (insideVersioning) {
                        when (currentTag) {
                            "latest"  -> latestText  = parser.text?.trim()
                            "release" -> releaseText = parser.text?.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "versioning") insideVersioning = false
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        // Prefer <latest>, fall back to <release>
        return latestText?.takeIf { it.isNotBlank() }
            ?: releaseText?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns true if [candidate] is strictly newer than [baseline] using
     * standard semantic versioning (major.minor.patch) comparison.
     * Pre-release suffixes (e.g. ".dev20260626") are stripped before comparing.
     */
    internal fun isNewer(candidate: String, baseline: String): Boolean {
        fun parseVersion(v: String): IntArray {
            // Strip pre-release suffixes like ".dev20260626"
            val clean = v.replace(Regex("[^\\d.].*$"), "").trimEnd('.')
            return clean.split(".").map { it.toIntOrNull() ?: 0 }.toIntArray()
        }
        val cParts = parseVersion(candidate)
        val bParts = parseVersion(baseline)
        val maxLen = maxOf(cParts.size, bParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val b = bParts.getOrElse(i) { 0 }
            if (c != b) return c > b
        }
        return false // equal versions
    }
}
