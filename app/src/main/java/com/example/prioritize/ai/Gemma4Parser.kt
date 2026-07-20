package com.example.prioritize.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import com.google.ai.edge.litertlm.Backend
import com.example.prioritize.ui.viewmodel.AVAILABLE_MODELS
import com.example.prioritize.ui.viewmodel.EdgeModelSpec
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Gemma4Parser(private val context: Context) : TaskParser {

    var actionListener: ((Action) -> Unit)? = null
    
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

        private val _contextFillRatio = MutableStateFlow(0f)
        val contextFillRatio: StateFlow<Float> = _contextFillRatio.asStateFlow()

        @Volatile
        private var accumulatedTokens = 0

        /** True when all backends have failed and the engine is not initialized.
         *  Exposed so the UI can show the diagnostic/reset card even when the
         *  activeBackend string has not changed from a previous session. */
        val isEngineNull: Boolean
            get() = engine == null

        private fun resetContextCounterInternal() {
            accumulatedTokens = 0
            _contextFillRatio.value = 0f
            try {
                activeConversation?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing activeConversation in resetContextCounter", e)
            }
            activeConversation = null
            Log.d(TAG, "Context counter reset and active conversation closed.")
        }

        suspend fun resetContextCounter() {
            inferenceMutex.withLock {
                resetContextCounterInternal()
            }
        }
    }

    suspend fun closeEngine() {
        inferenceMutex.withLock {
            resetContextCounterInternal()
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
    }

    suspend fun changeModel(filename: String) {
        inferenceMutex.withLock {
            if (activeModelFilename != filename) {
                activeModelFilename = filename
                resetContextCounterInternal()
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
                try {
                    System.loadLibrary("edgetpu_litert")
                } catch (t: Throwable) {}

                Log.d(TAG, "Attempting LiteRT-LM NPU init with nativeLibraryDir='$candidateDir'")
                prefs.edit().putBoolean(candidateKey, true).commit()
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.NPU(nativeLibraryDir = candidateDir),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU()
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                isInitialized = true
                activeBackend = "NPU"
                prefs.edit().putBoolean(candidateKey, false).commit()
                npuSucceeded = true
                return@withContext true
            } catch (e: Exception) {
                lastNpuError = e.toString()
                prefs.edit().putBoolean(candidateKey, false).commit()
            }
        }

        val gpuCrashed = prefs.getBoolean(gpuCrashKey(), false)
        if (!gpuCrashed) {
            try {
                Log.d(TAG, "Attempting LiteRT-LM GPU init...")
                prefs.edit().putBoolean(gpuCrashKey(), true).commit()
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU()
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                isInitialized = true
                activeBackend = "GPU"
                prefs.edit().putBoolean(gpuCrashKey(), false).commit()
                return@withContext true
            } catch (e: Exception) {
                lastGpuError = e.toString()
                prefs.edit().putBoolean(gpuCrashKey(), false).commit()
            }
        }

        try {
            Log.d(TAG, "Attempting LiteRT-LM CPU init (Fallback)...")
            val config = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(),
                visionBackend = Backend.GPU(),
                audioBackend = Backend.CPU()
            )
            val newEngine = Engine(config)
            newEngine.initialize()
            engine = newEngine
            isInitialized = true
            activeBackend = "CPU"
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "All LiteRT-LM engine backends failed to initialize", e)
            false
        }
    }


    private suspend fun runInference(prompt: String): String? {
        return inferenceMutex.withLock {
            runInferenceInternal(prompt)
        }
    }
    private suspend fun runInferenceInternal(prompt: String): String? = withContext(Dispatchers.Default) {
        if (!initializeEngine()) return@withContext null
        val currentEngine = engine ?: return@withContext null
        try {
            var fullResponse = ""
            
            // Guard: If adding this prompt will exceed 90% of context capacity, proactively reset
            val modelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
            val limit = modelSpec?.contextTokens ?: 8192
            val estimatedPromptTokens = prompt.length / 4
            if (accumulatedTokens + estimatedPromptTokens > limit * 0.9) {
                Log.w(TAG, "Approaching context limit: accumulated=$accumulatedTokens, prompt=$estimatedPromptTokens, limit=$limit. Proactively resetting conversation.")
                resetContextCounterInternal()
            }

            // Warm KV-cache reuse conversation
            val conversation = activeConversation ?: run {
                val modelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
                val config = if (modelSpec?.supportsTools == true) {
                    ConversationConfig(
                        tools = listOf(tool(PrioritizeTools { action ->
                            actionListener?.invoke(action)
                        })),
                        automaticToolCalling = true,
                        samplerConfig = if (activeBackend == "NPU") null else SamplerConfig(
                            topK = 64,
                            topP = 0.95,
                            temperature = 1.0
                        )
                    )
                } else {
                    ConversationConfig(
                        samplerConfig = if (activeBackend == "NPU") null else SamplerConfig(
                            topK = 64,
                            topP = 0.95,
                            temperature = 1.0
                        )
                    )
                }
                currentEngine.createConversation(config).also {
                    activeConversation = it
                }
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

            // Estimate tokens: characters / 4
            val finalModelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
            val finalLimit = finalModelSpec?.contextTokens ?: 8192
            val promptTokens = prompt.length / 4
            val responseTokens = fullResponse.length / 4
            accumulatedTokens += promptTokens + responseTokens
            _contextFillRatio.value = (accumulatedTokens.toFloat() / finalLimit).coerceAtMost(1f)
            Log.d(TAG, "Context usage updated: $accumulatedTokens / $finalLimit (${_contextFillRatio.value * 100}%)")

            fullResponse
        } catch (t: Throwable) {
            Log.e(TAG, "Inference error (potential OOM or runtime crash)", t)
            if (t is OutOfMemoryError || t.message?.contains("OutOfMemory", ignoreCase = true) == true) {
                Log.e(TAG, "CRITICAL: OutOfMemoryError caught during inference! Resetting conversation context.")
            }
            resetContextCounterInternal()
            null
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

    override suspend fun runMultimodalInference(
        prompt: String,
        imageBitmap: Bitmap?,
        audioBytes: ByteArray?
    ): String? {
        return runInferenceMultimodal(prompt, imageBitmap, audioBytes)
    }

    private suspend fun runInferenceMultimodal(prompt: String, image: Bitmap?, audio: ByteArray?): String? {
        return inferenceMutex.withLock {
            withContext(Dispatchers.Default) {
                if (!initializeEngine()) return@withContext null
                val currentEngine = engine ?: return@withContext null
                try {
                    var fullResponse = ""

                    // Guard: If adding this prompt + media will exceed 90% of context capacity, proactively reset
                    val modelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
                    val limit = modelSpec?.contextTokens ?: 8192
                    val estimatedPromptTokens = prompt.length / 4
                    val imageOverhead = if (image != null) 1024 else 0
                    val audioOverhead = if (audio != null) (audio.size / 32000) * 100 else 0
                    val estimatedTotalNewTokens = estimatedPromptTokens + imageOverhead + audioOverhead
                    if (accumulatedTokens + estimatedTotalNewTokens > limit * 0.9) {
                        Log.w(TAG, "Approaching context limit in multimodal: accumulated=$accumulatedTokens, new=$estimatedTotalNewTokens, limit=$limit. Proactively resetting conversation.")
                        resetContextCounterInternal()
                    }

                    val conversation = activeConversation ?: run {
                        val innerModelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
                        val config = if (innerModelSpec?.supportsTools == true) {
                            ConversationConfig(
                                tools = listOf(tool(PrioritizeTools { action ->
                                    actionListener?.invoke(action)
                                })),
                                automaticToolCalling = true,
                                samplerConfig = if (activeBackend == "NPU") null else SamplerConfig(
                                    topK = 64,
                                    topP = 0.95,
                                    temperature = 1.0
                                )
                            )
                        } else {
                            ConversationConfig(
                                samplerConfig = if (activeBackend == "NPU") null else SamplerConfig(
                                    topK = 64,
                                    topP = 0.95,
                                    temperature = 1.0
                                )
                            )
                        }
                        currentEngine.createConversation(config).also {
                            activeConversation = it
                        }
                    }

                    // Build contents list
                    val contents = mutableListOf<com.google.ai.edge.litertlm.Content>()
                    
                    if (image != null) {
                        val scaled = scaleBitmap(image, 1600)
                        contents.add(com.google.ai.edge.litertlm.Content.ImageBytes(scaled.toPngByteArray()))
                    }
                    if (audio != null) {
                        val wavAudio = ensureWavHeader(audio)
                        contents.add(com.google.ai.edge.litertlm.Content.AudioBytes(wavAudio))
                    }
                    contents.add(com.google.ai.edge.litertlm.Content.Text(prompt))

                    val contentsObj = com.google.ai.edge.litertlm.Contents.of(contents)
                    val response = conversation.sendMessage(contentsObj)
                    
                    val textParts = response.contents.contents
                        .filterIsInstance<com.google.ai.edge.litertlm.Content.Text>()
                    fullResponse = if (textParts.isNotEmpty()) {
                        textParts.joinToString("\n") { it.text }
                    } else {
                        response.contents.contents.joinToString("") { it.toString() }
                    }

                    // Estimate tokens: prompt, response + image/audio overhead
                    val finalModelSpec = AVAILABLE_MODELS.find { it.filename == activeModelFilename }
                    val finalLimit = finalModelSpec?.contextTokens ?: 8192
                    val promptTokens = prompt.length / 4
                    val responseTokens = fullResponse.length / 4
                    val finalImageOverhead = if (image != null) 1024 else 0
                    val finalAudioOverhead = if (audio != null) (audio.size / 32000) * 100 else 0
                    accumulatedTokens += promptTokens + responseTokens + finalImageOverhead + finalAudioOverhead
                    _contextFillRatio.value = (accumulatedTokens.toFloat() / finalLimit).coerceAtMost(1f)
                    Log.d(TAG, "Multimodal Context usage updated: $accumulatedTokens / $finalLimit (${_contextFillRatio.value * 100}%)")

                    fullResponse
                } catch (t: Throwable) {
                    Log.e(TAG, "Multimodal inference run failed (potential OOM or runtime crash)", t)
                    if (t is OutOfMemoryError || t.message?.contains("OutOfMemory", ignoreCase = true) == true) {
                        Log.e(TAG, "CRITICAL: OutOfMemoryError caught during multimodal inference! Resetting conversation context.")
                    }
                    resetContextCounterInternal()
                    
                    if (image != null || audio != null) {
                        throw Exception("Multimodal inference failed: ${t.message ?: "OOM/Native Error"}", t)
                    } else {
                        val fallbackPrompt = prompt
                        runInferenceInternal(fallbackPrompt)
                    }
                }
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun cleanJsonResponse(raw: String): String {
        val jsonPattern = """(\{|\[)[\s\S]*(\}|\])""".toRegex()
        val match = jsonPattern.find(raw)
        return match?.value ?: raw.trim()
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun ensureWavHeader(audioBytes: ByteArray): ByteArray {
        if (audioBytes.size > 44 && 
            audioBytes[0] == 'R'.code.toByte() && 
            audioBytes[1] == 'I'.code.toByte() && 
            audioBytes[2] == 'F'.code.toByte() && 
            audioBytes[3] == 'F'.code.toByte()) {
            return audioBytes
        }
        
        val pcmDataSize = audioBytes.size
        val wavFileSize = pcmDataSize + 36
        val sampleRate = 16000
        val channels = 1
        val bitsPerSample: Short = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (wavFileSize and 0xff).toByte()
        header[5] = (wavFileSize shr 8 and 0xff).toByte()
        header[6] = (wavFileSize shr 16 and 0xff).toByte()
        header[7] = (wavFileSize shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = (pcmDataSize shr 8 and 0xff).toByte()
        header[42] = (pcmDataSize shr 16 and 0xff).toByte()
        header[43] = (pcmDataSize shr 24 and 0xff).toByte()
        
        val wavBytes = ByteArray(44 + pcmDataSize)
        System.arraycopy(header, 0, wavBytes, 0, 44)
        System.arraycopy(audioBytes, 0, wavBytes, 44, pcmDataSize)
        return wavBytes
    }
}
