package com.example.prioritize.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Gemma4Parser(private val context: Context) : TaskParser {
    
    companion object {
        private const val TAG = "Gemma4Parser"
        private const val PREFS_NAME = "gemma4_engine_crash_guard"
        private val inferenceMutex = Mutex()

        @Volatile
        private var engine: Engine? = null

        @Volatile
        private var isInitialized = false

        @Volatile
        private var activeModelFilename = "gemma-4-E2B-it_Google_Tensor_G5.litertlm"

        @Volatile
        private var activeConversation: com.google.ai.edge.litertlm.Conversation? = null

        var activeBackend: String = "CPU"
        var lastNpuError: String? = null
        var lastGpuError: String? = null

        /** True when all backends have failed and the engine is not initialized.
         *  Exposed so the UI can show the diagnostic/reset card even when the
         *  activeBackend string has not changed from a previous session. */
        val isEngineNull: Boolean
            get() = engine == null
    }

    fun closeEngine() {
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing conversation", e)
        }
        activeConversation = null
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing engine", e)
        }
        engine = null
        isInitialized = false
        lastNpuError = null
        lastGpuError = null
        Log.d(TAG, "Engine closed manually.")
    }

    fun changeModel(filename: String) {
        if (activeModelFilename != filename) {
            activeModelFilename = filename
            try {
                activeConversation?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing conversation during model change", e)
            }
            activeConversation = null
            try {
                engine?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing engine during model change", e)
            }
            engine = null
            isInitialized = false
            lastNpuError = null
            lastGpuError = null
            Log.d(TAG, "Active model changed to: $filename. Engine reset.")
        }
    }

    private fun getModelFile(): File {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, activeModelFilename)
    }

    fun isModelAvailable(): Boolean {
        return getModelFile().exists()
    }

    fun isModelAvailable(filename: String): Boolean {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(dir, filename).exists()
    }

    /**
     * Resets all stored native-crash flags for the current model.
     * Call this from the diagnostic UI to let the user retry previously
     * crashing backends after updating the app or swapping the model file.
     */
    fun resetEngineBackendCrashFlags() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Remove per-path NPU crash guards
        for (path in listOf(
            context.applicationInfo.nativeLibraryDir,
            "/vendor/lib64",
            "/system/lib64"
        )) {
            val key = "native_crash_${activeModelFilename}_npu_${path.replace('/', '_')}"
            editor.remove(key)
        }
        editor.remove(gpuCrashKey())
        editor.commit()
        Log.i(TAG, "All backend crash flags cleared for $activeModelFilename")
    }

    private fun npuCrashKey() = "native_crash_${activeModelFilename}_npu"
    private fun gpuCrashKey() = "native_crash_${activeModelFilename}_gpu"

    private suspend fun initializeEngine(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true
        val modelFile = getModelFile()
        if (!modelFile.exists()) {
            Log.w(TAG, "Model file not found at ${modelFile.absolutePath}")
            return@withContext false
        }

        // SharedPreferences used as a crash guard for native backend initialization.
        // SIGABRT (thrown by the LiteRT native library on fatal errors) cannot be caught
        // by a Kotlin try-catch — it is an OS-level signal that kills the process.
        // Strategy:
        //   1. Write a synchronous commit() flag BEFORE each dangerous native call.
        //   2. Clear the flag AFTER successful initialization.
        //   3. If the process is killed by SIGABRT, the flag remains set on next launch.
        //   4. On next launch, the crashing backend is skipped automatically.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // ── Attempt 1: NPU — try multiple dispatch library search paths ────────
        // Root cause diagnostic: LiteRT-LM 0.13.1 does not bundle
        // libLiteRtDispatch_GoogleTensor.so in the APK. On Tensor G5 devices
        // (Pixel 10 Pro / blazer) the EdgeTPU dispatch library lives in
        // /vendor/lib64/libedgetpu_litert.so. We try multiple search paths so
        // that LiteRT can locate it via dlopen(). Each path has its own crash
        // guard key so a SIGABRT on one path still allows the next path.
        val npuDirCandidates = listOf(
            context.applicationInfo.nativeLibraryDir,   // App's extracted .so dir
            "/vendor/lib64",                             // Tensor G5 EdgeTPU vendor libs
            "/system/lib64",                             // System libs fallback
        )

        var npuSucceeded = false
        for (candidateDir in npuDirCandidates) {
            val candidateKey = "native_crash_${activeModelFilename}_npu_${candidateDir.replace('/', '_')}"
            val candidateCrashed = prefs.getBoolean(candidateKey, false)
            if (candidateCrashed) {
                Log.w(TAG, "Skipping NPU with dir='$candidateDir' — previous SIGABRT detected")
                continue
            }

            try {
                // Pre-load public vendor EdgeTPU lib so the linker can satisfy dlopen()
                try {
                    System.loadLibrary("edgetpu_litert")
                    Log.i(TAG, "Successfully pre-loaded public library edgetpu_litert")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to pre-load public library edgetpu_litert: ${t.message}")
                }

                Log.d(TAG, "Attempting LiteRT-LM NPU init with nativeLibraryDir='$candidateDir'")
                prefs.edit().putBoolean(candidateKey, true).commit() // Guard BEFORE native call
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = candidateDir)
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                isInitialized = true
                activeBackend = "NPU"
                prefs.edit().putBoolean(candidateKey, false).commit() // Clear on success
                Log.i(TAG, "LiteRT-LM NPU engine initialized! dir='$candidateDir'")
                npuSucceeded = true
                return@withContext true
            } catch (e: Exception) {
                lastNpuError = e.toString()
                prefs.edit().putBoolean(candidateKey, false).commit() // Java exception — not SIGABRT
                Log.w(TAG, "NPU init failed for dir='$candidateDir': ${e.message}", e)
            }
        }

        if (!npuSucceeded) {
            lastNpuError = lastNpuError ?: "All NPU nativeLibraryDir candidates failed or were crash-guarded."
            Log.w(TAG, "NPU backend exhausted all candidates. Falling through to GPU...")
        }

        // ── Attempt 2: GPU ─────────────────────────────────────────────────────
        val gpuCrashed = prefs.getBoolean(gpuCrashKey(), false)
        if (gpuCrashed) {
            lastGpuError = "GPU backend caused a native crash (SIGABRT) on a previous attempt " +
                "for model '$activeModelFilename'. Skipped for stability."
            Log.w(TAG, "Skipping GPU backend — previous native crash detected for $activeModelFilename")
        } else {
            try {
                Log.d(TAG, "Attempting LiteRT-LM GPU init...")
                prefs.edit().putBoolean(gpuCrashKey(), true).commit()
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU()
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                isInitialized = true
                activeBackend = "GPU"
                prefs.edit().putBoolean(gpuCrashKey(), false).commit()
                Log.i(TAG, "LiteRT-LM GPU engine initialized successfully!")
                return@withContext true
            } catch (e: Exception) {
                lastGpuError = e.toString()
                prefs.edit().putBoolean(gpuCrashKey(), false).commit()
                Log.w(TAG, "GPU backend initialization failed: ${e.message}. Falling back to CPU...", e)
            }
        }

        // ── Attempt 3: CPU (safe fallback — no crash guard needed, CPU never SIGABRTs) ──
        try {
            Log.d(TAG, "Attempting LiteRT-LM CPU init (Fallback)...")
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU()
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            isInitialized = true
            activeBackend = "CPU"
            Log.i(TAG, "LiteRT-LM CPU engine initialized successfully (CPU Fallback)")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "All LiteRT-LM engine backends failed to initialize", e)
            false
        }
    }


    private suspend fun runInference(prompt: String): String? {
        return inferenceMutex.withLock {
            withContext(Dispatchers.Default) {
                if (!initializeEngine()) return@withContext null
                val currentEngine = engine ?: return@withContext null
                try {
                    var fullResponse = ""
                    // Warm KV-cache reuse conversation
                    val conversation = activeConversation ?: currentEngine.createConversation().also {
                        activeConversation = it
                    }
                    // NOTE: sendMessageAsync crashes on litertlm:0.14.0 due to a binary
                    // incompatibility: it uses Kotlin 2.x bytecode that calls
                    // SendChannel.close$default as a static interface method, but
                    // kotlinx-coroutines 1.9.0 was compiled with an older compiler that puts
                    // this method in SendChannel$DefaultImpls instead.
                    // Workaround: use the synchronous sendMessage API which calls
                    // nativeSendMessage JNI directly with no coroutines channels involved.
                    val response = conversation.sendMessage(prompt)
                    Log.d(TAG, "sendMessage returned. toString=${response.toString().take(200)}")
                    Log.d(TAG, "contents count=${response.contents.contents.size}")
                    response.contents.contents.forEachIndexed { i, c ->
                        Log.d(TAG, "content[$i] type=${c::class.java.simpleName} toString=${c.toString().take(100)}")
                    }
                    // Try Content.Text first
                    val textParts = response.contents.contents
                        .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                    fullResponse = if (textParts.isNotEmpty()) {
                        textParts.joinToString("") { it.text }
                    } else {
                        // Fallback: join all content toString()
                        response.contents.contents.joinToString("") { it.toString() }
                            .ifEmpty { response.toString() }
                    }
                    Log.d(TAG, "Inference result: '${fullResponse.take(200)}'")
                    fullResponse
                } catch (e: Exception) {
                    Log.e(TAG, "Inference error", e)
                    try {
                        activeConversation?.close()
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error closing conversation after inference failure", ex)
                    }
                    activeConversation = null
                    null
                }
            }
        }
    }

    override suspend fun parseTaskFromText(inputText: String): ParsedTaskSuggestion? {
        val prompt = """
            You are a task planner. Extract a task from this input text.
            Respond ONLY with a valid JSON object matching this schema:
            {
              "title": "Short title",
              "description": "Short description",
              "importance": 1 to 10,
              "urgency": 1 to 10,
              "estimated_minutes": integer,
              "days_until_due": integer_or_null
            }
            Do not output any markdown formatting, backticks, or comments.
            Input text: $inputText
        """.trimIndent()

        val response = runInference(prompt) ?: return null
        return try {
            val jsonString = cleanJsonResponse(response)
            val json = JSONObject(jsonString)
            val title = json.getString("title")
            val desc = json.optString("description", "")
            val imp = json.optInt("importance", 5).coerceIn(1, 10)
            val urg = json.optInt("urgency", 5).coerceIn(1, 10)
            val estMin = json.optInt("estimated_minutes", 15)
            val days = if (json.isNull("days_until_due")) null else json.getInt("days_until_due")
            
            val deadline = days?.let {
                System.currentTimeMillis() + (it.toLong() * 24L * 60L * 60L * 1000L)
            }
            val recurrenceType = if (json.isNull("recurrence_type")) null else json.optString("recurrence_type")

            ParsedTaskSuggestion(title, desc, imp, urg, estMin, deadline, recurrenceType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON task response: $response", e)
            null
        }
    }

    override suspend fun generateBreakdownPrompt(taskTitle: String): String {
        return """
            Break down the task '$taskTitle' into 3 to 5 tiny, extremely low-friction sequential steps so that getting started is easy. For each step, suggest an estimated completion time in minutes. Output your response strictly as a JSON array of objects with keys 'title' (string) and 'estimatedMinutes' (integer). Example output: [{"title": "Open document template", "estimatedMinutes": 5}]
        """.trimIndent()
    }

    override suspend fun parseSubTasksFromResponse(pastedText: String): List<ParsedSubTaskSuggestion> {
        val prompt = """
            Extract a list of sub-tasks from the text below. 
            Respond ONLY with a valid JSON array of objects, each containing:
            'title' (string) and 'estimatedMinutes' (integer).
            Do not include markdown or backticks.
            Text: $pastedText
        """.trimIndent()

        val response = runInference(prompt) ?: return emptyList()
        return parseSubTaskJson(response)
    }

    override suspend fun generateSubTasksLocally(taskTitle: String): List<ParsedSubTaskSuggestion> {
        val prompt = """
            Break down the task '$taskTitle' into 3 to 5 small, concrete, progressive steps.
            Respond ONLY with a valid JSON array of objects containing 'title' and 'estimatedMinutes' (integer).
            Example: [{"title": "Open editor", "estimatedMinutes": 5}]
            Do not output markdown code blocks.
        """.trimIndent()

        val response = runInference(prompt) ?: return emptyList()
        return parseSubTaskJson(response)
    }

    private fun parseSubTaskJson(response: String): List<ParsedSubTaskSuggestion> {
        return try {
            val jsonString = cleanJsonResponse(response)
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<ParsedSubTaskSuggestion>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.getString("title")
                val minutes = obj.optInt("estimatedMinutes", 15)
                list.add(ParsedSubTaskSuggestion(title, minutes))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse sub-task JSON: $response", e)
            emptyList()
        }
    }

    override suspend fun runRawInference(prompt: String): String? {
        return runInference(prompt)
    }

    private fun cleanJsonResponse(raw: String): String {
        val jsonPattern = """(\{|\[)[\s\S]*(\}|\])""".toRegex()
        val match = jsonPattern.find(raw)
        return match?.value ?: raw.trim()
    }
}
