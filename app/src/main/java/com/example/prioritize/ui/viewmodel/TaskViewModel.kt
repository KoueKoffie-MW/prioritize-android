package com.example.prioritize.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.prioritize.ai.*
import com.example.prioritize.data.SubTask
import com.example.prioritize.data.Task
import com.example.prioritize.data.RepeatingTask
import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.UserProfile
import com.example.prioritize.data.MemoryProfile
import com.example.prioritize.data.ObservationLog
import com.example.prioritize.data.RecurrenceType
import com.example.prioritize.data.SpecialDateType
import com.example.prioritize.data.TaskRepository
import com.example.prioritize.data.ChatMessageEntity
import com.example.prioritize.worker.DreamingWorker
import com.example.prioritize.github.GitHubAuthManager
import com.example.prioritize.github.GitHubIssueService
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Calendar

// EdgeModelSpec and AVAILABLE_MODELS are defined in ModelRegistry.kt (same package)


class TaskViewModel(
    application: Application,
    private val repository: TaskRepository
) : AndroidViewModel(application) {

    val totalRamGb: Double by lazy {
        val actManager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    }

    // ── GitHub integration ───────────────────────────────────────────────────
    val gitHubAuth = GitHubAuthManager(application)
    private val gitHubIssueService = GitHubIssueService()

    private val _isGitHubLoggingIn = MutableStateFlow(false)
    val isGitHubLoggingIn = _isGitHubLoggingIn.asStateFlow()

    private val _gitHubUserCode = MutableStateFlow<String?>(null)
    val gitHubUserCode = _gitHubUserCode.asStateFlow()

    /** True when a GitHub OAuth token is stored on-device. Recomputed on demand. */
    private val _isGitHubLoggedIn = MutableStateFlow(gitHubAuth.isLoggedIn)
    val isGitHubLoggedIn = _isGitHubLoggedIn.asStateFlow()

    val gitHubUsername: String? get() = gitHubAuth.username

    private val _feedbackSubmitState = MutableStateFlow<FeedbackSubmitState>(FeedbackSubmitState.Idle)
    val feedbackSubmitState = _feedbackSubmitState.asStateFlow()

    /** Initiates the GitHub Device Flow. Opens the browser for the user to authorize. */
    fun startGitHubLogin() {
        if (_isGitHubLoggingIn.value) return
        _isGitHubLoggingIn.value = true
        viewModelScope.launch {
            val result = gitHubAuth.startDeviceFlow(
                onUserCodeReady = { code -> _gitHubUserCode.value = code }
            )
            _gitHubUserCode.value = null
            _isGitHubLoggedIn.value = gitHubAuth.isLoggedIn
            _isGitHubLoggingIn.value = false
            if (result is GitHubAuthManager.DeviceFlowResult.Error) {
                _aiErrorMsg.value = "GitHub login failed: ${result.message}"
            }
        }
    }

    fun gitHubLogout() {
        gitHubAuth.logout()
        _isGitHubLoggedIn.value = false
    }

    /**
     * Submits a feedback issue to the Prioritize GitHub repository.
     * Automatically appends device and model context to the issue body.
     */
    fun submitFeedback(title: String, body: String, labels: List<String>) {
        val token = gitHubAuth.accessToken ?: run {
            _feedbackSubmitState.value = FeedbackSubmitState.Error("Not connected to GitHub.")
            return
        }
        _feedbackSubmitState.value = FeedbackSubmitState.Submitting
        viewModelScope.launch {
            val enrichedBody = buildString {
                appendLine(body)
                appendLine()
                appendLine("---")
                appendLine("**Submitted from:** Prioritize Android App")
                appendLine("**Active model:** ${_activeModelSpec.value.name}")
                appendLine("**Backend:** $activeBackend")
                appendLine("**Submitted by:** @${gitHubAuth.username}")
            }
            val result = gitHubIssueService.createIssue(
                token = token,
                title = title,
                body = enrichedBody,
                labels = labels
            )
            _feedbackSubmitState.value = when (result) {
                is GitHubIssueService.IssueResult.Success ->
                    FeedbackSubmitState.Success(result.url, result.number)
                is GitHubIssueService.IssueResult.Error ->
                    FeedbackSubmitState.Error(result.message)
            }
        }
    }

    fun resetFeedbackState() {
        _feedbackSubmitState.value = FeedbackSubmitState.Idle
    }

    sealed class FeedbackSubmitState {
        object Idle : FeedbackSubmitState()
        object Submitting : FeedbackSubmitState()
        data class Success(val url: String, val number: Int) : FeedbackSubmitState()
        data class Error(val message: String) : FeedbackSubmitState()
    }

    val parser = Gemma4Parser(application).apply {
        actionListener = { action ->
            handleParserAction(action)
        }
    }

    val activeBackend: String
        get() = com.example.prioritize.ai.Gemma4Parser.activeBackend

    val lastNpuError: String?
        get() = com.example.prioritize.ai.Gemma4Parser.lastNpuError

    val lastGpuError: String?
        get() = com.example.prioritize.ai.Gemma4Parser.lastGpuError

    /** True when all backends have failed and the LiteRT engine is null.
     *  Used by BrainScreen to show the diagnostic card whenever the engine
     *  is broken — not only when activeBackend happens to read "CPU". */
    val isEngineNull: Boolean
        get() = com.example.prioritize.ai.Gemma4Parser.isEngineNull

    /**
     * Cached result of a LiteRT-LM version check against Google Maven.
     * Null until the first check completes. Observed by BrainScreen to show
     * an NPU fix notification banner when a new library version is available.
     */
    private val _liteRtUpdateResult =
        MutableStateFlow<com.example.prioritize.ai.LiteRtUpdateChecker.CheckResult?>(null)
    val liteRtUpdateResult: StateFlow<com.example.prioritize.ai.LiteRtUpdateChecker.CheckResult?> =
        _liteRtUpdateResult.asStateFlow()

    /** Trigger a Maven version check (network). Safe to call multiple times — debounced. */
    fun checkLiteRtUpdate() {
        if (_liteRtUpdateResult.value != null) return // already checked this session
        viewModelScope.launch {
            _liteRtUpdateResult.value =
                com.example.prioritize.ai.LiteRtUpdateChecker.check()
        }
    }

    // Flow of active sorted tasks
    val activeTasks: StateFlow<List<Task>> = repository.activeTasksFlow
        .map { tasks ->
            val currentTime = System.currentTimeMillis()
            tasks.sortedByDescending { it.getPriorityScore(currentTime) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow of scratchpad tasks
    val scratchPadTasks: StateFlow<List<Task>> = repository.scratchPadTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow of completed tasks
    val completedTasks: StateFlow<List<Task>> = repository.completedTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow of deleted tasks (Recycle Bin)
    val deletedTasks: StateFlow<List<Task>> = repository.deletedTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // User profile flow
    val userProfile: StateFlow<UserProfile?> = repository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userAccent: StateFlow<String> = repository.userProfileFlow
        .map { it?.userAccent ?: "South African Afrikaans" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "South African Afrikaans")

    val knownSpeakers: StateFlow<List<com.example.prioritize.data.KnownSpeaker>> = repository.userProfileFlow
        .map { profile ->
            val json = profile?.knownSpeakersJson ?: "[]"
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<com.example.prioritize.data.KnownSpeaker>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(com.example.prioritize.data.KnownSpeaker(obj.getString("name"), obj.getString("accent")))
                }
                list
            } catch(e: Exception) {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Repeating tasks flow
    val repeatingTasks: StateFlow<List<RepeatingTask>> = repository.repeatingTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Special dates flow
    val specialDates: StateFlow<List<SpecialDate>> = repository.specialDatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map of taskId -> List<SubTask>
    private val _subTasksMap = MutableStateFlow<Map<Long, List<SubTask>>>(emptyMap())
    val subTasksMap: StateFlow<Map<Long, List<SubTask>>> = _subTasksMap.asStateFlow()

    // Loading / Processing States
    private val _isAILoading = MutableStateFlow(false)
    val isAILoading: StateFlow<Boolean> = _isAILoading.asStateFlow()

    private val _aiSuggestion = MutableStateFlow<ParsedTaskSuggestion?>(null)
    val aiSuggestion: StateFlow<ParsedTaskSuggestion?> = _aiSuggestion.asStateFlow()

    // Brain Chat states
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Expose context size percentage meter
    val contextFillRatio: StateFlow<Float> = Gemma4Parser.contextFillRatio

    // Document attachment states
    private val _attachedDocUri = MutableStateFlow<android.net.Uri?>(null)
    val attachedDocUri: StateFlow<android.net.Uri?> = _attachedDocUri.asStateFlow()

    private val _attachedDocName = MutableStateFlow<String?>(null)
    val attachedDocName: StateFlow<String?> = _attachedDocName.asStateFlow()

    fun setAttachedDoc(uri: android.net.Uri?, name: String?) {
        _attachedDocUri.value = uri
        _attachedDocName.value = name
    }

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _chatAttachmentStatus = MutableStateFlow<String?>(null)
    val chatAttachmentStatus: StateFlow<String?> = _chatAttachmentStatus.asStateFlow()

    private val _onboardingPhase = MutableStateFlow(0) // 0 = idle brainstorming, 1 = dynamic interview active
    val onboardingPhase: StateFlow<Int> = _onboardingPhase.asStateFlow()

    // Model Downloader states
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _isModelAvailable = MutableStateFlow(false)
    val isModelAvailable: StateFlow<Boolean> = _isModelAvailable.asStateFlow()

    private val _aiErrorMsg = MutableStateFlow<String?>(null)
    val aiErrorMsg: StateFlow<String?> = _aiErrorMsg.asStateFlow()

    fun clearAiError() {
        _aiErrorMsg.value = null
    }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importProgress = MutableStateFlow(0)
    val importProgress: StateFlow<Int> = _importProgress.asStateFlow()

    // Dynamic model management flows
    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()

    private val _activeModelSpec = MutableStateFlow(
        AVAILABLE_MODELS.firstOrNull { it.id == DEFAULT_MODEL_ID } ?: AVAILABLE_MODELS[0]
    ) // Generic Gemma 4 E2B — GPU/CPU capable on all devices
    val activeModelSpec: StateFlow<EdgeModelSpec> = _activeModelSpec.asStateFlow()

    init {
        // Schedule periodic background Dreaming consolidation worker via WorkManager
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()

        val dreamingWorkRequest = PeriodicWorkRequestBuilder<DreamingWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        )
        .setConstraints(constraints)
        .build()

        WorkManager.getInstance(application)
            .enqueueUniquePeriodicWork(
                "DreamingWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                dreamingWorkRequest
            )

        // Delete legacy model files that are no longer in the AVAILABLE_MODELS allowlist.
        // Using an explicit allowlist prevents accidental deletion of future valid model files.
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val validFilenames = AVAILABLE_MODELS.map { it.filename }.toSet()
            val legacyFiles = dir.listFiles { _, name ->
                // Delete any .litertlm file that is NOT in the current AVAILABLE_MODELS list
                name.endsWith(".litertlm") && name !in validFilenames
            }
            legacyFiles?.forEach { file ->
                try {
                    Log.d("Downloader", "Deleting legacy/unlisted model file: ${file.name}")
                    file.delete()
                } catch (e: Exception) {
                    Log.e("Downloader", "Failed to delete legacy model file: ${file.name}", e)
                }
            }
            withContext(Dispatchers.Main) {
                refreshDownloadedModels()
            }
        }

        // Observe active tasks and batch-load all sub-tasks in a single IO operation.
        // Previously this used N individual queries (one per task); now we fetch
        // all task IDs and build the map in a single pass on the IO dispatcher.
        viewModelScope.launch(Dispatchers.IO) {
            repository.activeTasksFlow.collect { tasks ->
                val map = tasks.associate { task ->
                    task.id to repository.getSubTasksForTask(task.id)
                }
                _subTasksMap.value = map
            }
        }

        // Observe persistent chat messages from SQLite
        viewModelScope.launch(Dispatchers.IO) {
            repository.chatMessagesFlow.collect { entities ->
                val uiMsgs = entities.map { it.toChatMessage() }
                _chatMessages.value = uiMsgs
            }
        }

        // Initialize default user profile if empty
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile()
            if (profile == null) {
                repository.insertUserProfile(
                    UserProfile(
                        id = 1,
                        systemPrompt = "You are a supportive ADHD executive function assistant and second brain. Help the user clarify, break down, and prioritize tasks.",
                        metadataJson = "{}",
                        userAccent = "South African Afrikaans",
                        knownSpeakersJson = "[]"
                    )
                )
            } else {
                // If profile is bloated, trigger legacy system prompt migration.
                // Call directly instead of a nested launch to preserve structured concurrency.
                if (profile.systemPrompt.length > 800) {
                    migrateBloatedPrompt(profile)
                }

                // If profile has metadata specifying active model filename, restore it
                try {
                    val json = JSONObject(profile.metadataJson)
                    if (json.has("active_model_id")) {
                        val activeId = json.getString("active_model_id")
                        val match = AVAILABLE_MODELS.find { it.id == activeId }
                        if (match != null) {
                            _activeModelSpec.value = match
                            (parser as? Gemma4Parser)?.changeModel(match.filename)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Init", "Failed to restore active model preference", e)
                }
            }

            // Seed default family special dates if empty
            if (repository.getSpecialDates().isEmpty()) {
                repository.insertSpecialDate(SpecialDate(name = "Angelique's Birthday",  dateMonth = 9,  dateDay = 22, type = SpecialDateType.BIRTHDAY))
                repository.insertSpecialDate(SpecialDate(name = "Wedding Anniversary",   dateMonth = 4,  dateDay = 7,  type = SpecialDateType.ANNIVERSARY))
                repository.insertSpecialDate(SpecialDate(name = "Johan-Henry's Birthday",dateMonth = 11, dateDay = 20, type = SpecialDateType.BIRTHDAY))
                repository.insertSpecialDate(SpecialDate(name = "Ansunet's Birthday",    dateMonth = 11, dateDay = 8,  type = SpecialDateType.BIRTHDAY))
            }

            // Trigger reactive check
            withContext(Dispatchers.Main) {
                refreshDownloadedModels()
            }
        }
    }

    // Refresh set of currently downloaded model files on-device
    fun refreshDownloadedModels() {
        val dir = getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir
        val downloaded = AVAILABLE_MODELS.filter { File(dir, it.filename).exists() }.map { it.id }.toSet()
        _downloadedModels.value = downloaded

        // Update active availability status
        val currentSpec = _activeModelSpec.value
        _isModelAvailable.value = downloaded.contains(currentSpec.id)
    }

    // Delete a downloaded model file from device storage to reclaim space
    fun deleteModelFile(model: EdgeModelSpec) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = getApplication<Application>().getExternalFilesDir(null) ?: getApplication<Application>().filesDir
                val file = File(dir, model.filename)
                if (file.exists()) {
                    Log.d("Downloader", "Deleting model file: ${file.name}")
                    file.delete()
                }
                
                // If we deleted the active model, close the session
                val currentSpec = _activeModelSpec.value
                if (currentSpec.id == model.id) {
                    withContext(Dispatchers.Main) {
                        (parser as? com.example.prioritize.ai.Gemma4Parser)?.closeEngine()
                    }
                }
                
                withContext(Dispatchers.Main) {
                    refreshDownloadedModels()
                }
            } catch (e: Exception) {
                Log.e("Downloader", "Failed to delete model file: ${model.filename}", e)
            }
        }
    }

    // Programmatic legacy prompt migration to MemoryProfiles
    private suspend fun migrateBloatedPrompt(profile: UserProfile) {
        Log.i("Migration", "Starting legacy system prompt migration...")
        val prompt = """
            You are a database migration script. Read the bloated system prompt below.
            Extract any useful facts, birthdays, relationships, preferences, or chore routines.
            Respond ONLY with a valid JSON array matching this schema:
            [
              {
                "key": "unique_subject_key",
                "title": "Display Title",
                "keywords": "comma,separated,aliases",
                "new_facts": ["fact string 1", "fact string 2"]
              }
            ]
            
            Bloated System Prompt:
            "${profile.systemPrompt}"
        """.trimIndent()

        val response = parser.runRawInference(prompt) ?: ""
        try {
            val startIndex = response.indexOf('[')
            val endIndex = response.lastIndexOf(']')
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                val jsonStr = response.substring(startIndex, endIndex + 1)
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val key = obj.getString("key").trim().lowercase()
                    val title = obj.getString("title").trim()
                    val keywords = obj.getString("keywords").trim()
                    val newFactsArr = obj.getJSONArray("new_facts")

                    val existing = repository.getMemoryProfileByKey(key)
                    val mergedFacts = mutableListOf<String>()
                    if (existing != null) {
                        try {
                            val currentArr = JSONArray(existing.factsJson)
                            for (j in 0 until currentArr.length()) {
                                mergedFacts.add(currentArr.getString(j))
                            }
                        } catch (e: Exception) {
                            Log.w("Migration", "Failed to parse existing factsJson for key='$key': ${e.message}")
                        }
                    }

                    for (j in 0 until newFactsArr.length()) {
                        val fact = newFactsArr.getString(j).trim()
                        if (fact.isNotEmpty() && !mergedFacts.any { it.equals(fact, ignoreCase = true) }) {
                            mergedFacts.add(fact)
                        }
                    }

                    // Deduplicate keywords to prevent unbounded CSV growth
                    val mergedKeywords: String = buildSet {
                        existing?.keywordsCsv?.split(",")?.forEach { add(it.trim()) }
                        keywords.split(",").forEach { add(it.trim()) }
                    }.filter { it.isNotEmpty() }.joinToString(",")

                    val factsJsonStr = JSONArray(mergedFacts).toString()
                    val profileToSave = MemoryProfile(
                        id = existing?.id ?: 0,
                        key = key,
                        title = title,
                        keywordsCsv = mergedKeywords,
                        factsJson = factsJsonStr,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.insertMemoryProfile(profileToSave)
                }
                Log.i("Migration", "System prompt migrated successfully.")
            }
        } catch (e: Exception) {
            Log.e("Migration", "Failed to parse migrated facts JSON", e)
        }

        // Reset system prompt to clean baseline to restore speed
        val cleanBaseline = "You are Gemma, Jan's supportive ADHD executive function coach. Speak directly, candidly, and structure insights as bold visual anchors. Follow task suggestion formatting (###TASK_SUGGESTION### or ###REPEATING_TASK_SUGGESTION###). Use the provided context facts to answer questions contextually."
        repository.insertUserProfile(profile.copy(systemPrompt = cleanBaseline))
        Log.i("Migration", "System prompt reset to clean baseline.")
    }

    fun resetEngineBackendCrashFlags() {
        (parser as? Gemma4Parser)?.resetEngineBackendCrashFlags()
    }

    // Swaps active edge LLM dynamically
    fun selectActiveModel(spec: EdgeModelSpec) {
        viewModelScope.launch(Dispatchers.IO) {
            _activeModelSpec.value = spec
            (parser as? Gemma4Parser)?.changeModel(spec.filename)
            
            // Persist model selection to User Profile metadata
            val profile = repository.getUserProfile()
            if (profile != null) {
                val metadataObj = try {
                    JSONObject(profile.metadataJson)
                } catch(e: Exception) {
                    JSONObject()
                }
                metadataObj.put("active_model_id", spec.id)
                repository.insertUserProfile(profile.copy(metadataJson = metadataObj.toString()))
            }
            
            withContext(Dispatchers.Main) {
                refreshDownloadedModels()
            }
        }
    }

    // Download selected LLM model in background via native DownloadManager
    // Resolve redirect location for Hugging Face LFS S3 URLs
    private suspend fun getRedirectUrl(urlStr: String, hfToken: String?): String = withContext(Dispatchers.IO) {
        var currentUrl = urlStr
        try {
            val url = java.net.URL(currentUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = false // Prevent automatic redirects
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            
            if (!hfToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $hfToken")
            }
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            
            val responseCode = connection.responseCode
            if (responseCode == 301 || responseCode == 302 || responseCode == 303 || responseCode == 307 || responseCode == 308) {
                val redirectLocation = connection.getHeaderField("Location")
                if (!redirectLocation.isNullOrBlank()) {
                    currentUrl = redirectLocation
                }
            }
            // Drain any response body to allow socket reuse, then disconnect
            try { connection.inputStream?.close() } catch (_: Exception) {}
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("Downloader", "Failed to resolve redirect for URL: $urlStr", e)
        }
        currentUrl
    }

    // Persist HF Token to user profile metadata JSON
    fun updateHfToken(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile()
            if (profile != null) {
                val json = try {
                    JSONObject(profile.metadataJson)
                } catch(e: Exception) {
                    JSONObject()
                }
                json.put("hf_token", token.trim())
                repository.insertUserProfile(profile.copy(metadataJson = json.toString()))
            }
        }
    }

    // SAF File Importer to copy downloaded .litertlm file from public Downloads directory into internal storage.
    // Uses .use{} blocks to guarantee stream closure even if an exception occurs mid-copy.
    fun importModelFile(uri: android.net.Uri, targetFilename: String) {
        val context = getApplication<Application>().applicationContext
        _isImporting.value = true
        _importProgress.value = 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val totalSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val destFile = File(dir, targetFilename)

                contentResolver.openInputStream(uri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(16_384) // 16 KB buffer for fast copy
                        var bytesRead: Int
                        var totalRead = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalSize > 0) {
                                _importProgress.value = (totalRead * 100 / totalSize).toInt()
                            }
                        }
                    }
                } ?: throw Exception("Failed to open input stream from URI")

                withContext(Dispatchers.Main) {
                    refreshDownloadedModels()
                    Toast.makeText(context, "Model imported successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Importer", "Failed to import model: $targetFilename", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isImporting.value = false
            }
        }
    }

    // Force run background Dreaming consolidation worker now on demand
    fun runDreamingConsolidationNow() {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            val workManager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<DreamingWorker>().build()
            workManager.enqueue(request)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Consolidation background process started!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Download selected LLM model in background via native DownloadManager with resolved S3 redirect
    fun downloadModel(spec: EdgeModelSpec) {
        val context = getApplication<Application>().applicationContext
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager

        _isDownloading.value = true
        _downloadProgress.value = 0

        viewModelScope.launch(Dispatchers.IO) {
            // Retrieve HF Token from UserProfile metadata if present
            val profile = repository.getUserProfile()
            var hfToken: String? = null
            if (profile != null) {
                try {
                    val json = JSONObject(profile.metadataJson)
                    if (json.has("hf_token")) {
                        hfToken = json.getString("hf_token")
                    }
                } catch(e: Exception) {}
            }

            // Resolve LFS AWS S3 Redirect URL
            val finalUrl = getRedirectUrl(spec.downloadUrl, hfToken)
            Log.d("Downloader", "Resolved redirect download URL: $finalUrl")

            try {
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(finalUrl)).apply {
                    setTitle(spec.name)
                    setDescription("Downloading on-device intelligence...")
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalFilesDir(context, null, spec.filename)
                    // Note: We do NOT append hfToken here, because S3 signed URL has authorization embedded in query.
                }

                val downloadId = downloadManager.enqueue(request)

                var downloading = true
                while (downloading) {
                    val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                        val bytesDownloaded = if (bytesDownloadedIdx >= 0) cursor.getLong(bytesDownloadedIdx) else 0L
                        val bytesTotal = if (bytesTotalIdx >= 0) cursor.getLong(bytesTotalIdx) else 0L

                        if (bytesTotal > 0) {
                            val progress = (bytesDownloaded * 100 / bytesTotal).toInt()
                            _downloadProgress.value = progress
                        }

                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                            _isDownloading.value = false
                            withContext(Dispatchers.Main) {
                                refreshDownloadedModels()
                            }
                        } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                            downloading = false
                            _isDownloading.value = false
                            val reasonIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON)
                            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else -1
                            Log.e("Downloader", "Download failed. Reason code: $reason")
                        }
                    } else {
                        downloading = false
                        _isDownloading.value = false
                    }
                    cursor?.close()
                    kotlinx.coroutines.delay(1000)
                }
            } catch (e: Exception) {
                Log.e("Downloader", "Failed to start download of model: ${spec.id}", e)
                _isDownloading.value = false
            }
        }
    }

    // Web Search using DuckDuckGo HTML scraping.
    // Timeouts are set to prevent indefinite blocking on slow networks.
    suspend fun performWebSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://html.duckduckgo.com/html/?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            val html = connection.inputStream.bufferedReader().use { it.readText() }

            val matches = mutableListOf<String>()
            val regex = """<a class="result__snippet"[^>]*>(.*?)</a>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            regex.findAll(html).take(3).forEach { match ->
                val snippet = match.groupValues[1]
                    .replace(Regex("<[^>]*>"), "")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .trim()
                matches.add(snippet)
            }

            if (matches.isNotEmpty()) {
                matches.joinToString("\n\n")
            } else {
                "No relevant search snippets found."
            }
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    // Quick dump scratchpad task
    fun dumpToScratchPad(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val task = Task(title = text.trim(), isScratchPadItem = true)
            repository.insertTask(task)
            evolveProfileOnEvent("User added task: '${text.trim()}' to scratch pad.")
        }
    }

    // Trigger AI parsing for a scratchpad item.
    // IMPORTANT: The original task is only deleted AFTER the user confirms the suggestion
    // via ConfirmTaskDialog -> viewModel.saveTask(). If parsing fails, the item stays in
    // the scratch pad so no data is lost.
    fun processScratchPadItem(task: Task) {
        _isAILoading.value = true
        viewModelScope.launch {
            val suggestion = withContext(Dispatchers.Default) {
                parser.parseTaskFromText(task.title)
            }
            if (suggestion != null) {
                // Store suggestion; the actual delete happens inside clearSuggestion() below
                _aiSuggestion.value = suggestion.copy()
                // Mark the source scratch pad task ID so we can delete it on confirm
                _pendingScratchPadTask.value = task
            } else {
                // Parsing failed — leave the item in the scratch pad to prevent data loss
                Log.w("ScratchPad", "AI parsing returned null for: '${task.title}'. Item preserved.")
                val isNpuModel = _activeModelSpec.value.filename.contains("Tensor_G5")
                val isCpuOrGpu = activeBackend.startsWith("CPU") || activeBackend.startsWith("GPU")
                if (isNpuModel && isCpuOrGpu) {
                    _aiErrorMsg.value = "AI parsing failed. The Tensor G5 NPU precompiled model cannot run on CPU. Please switch to the standard 'Gemma 4 E2B (Thinking)' model in Settings (⚙️ in Brain tab)."
                } else {
                    _aiErrorMsg.value = "AI parsing failed. Please verify that your active model is initialized and compatible in Settings."
                }
            }
            _isAILoading.value = false
        }
    }

    // Scratch pad source task pending deletion (set when AI parsing succeeds)
    private val _pendingScratchPadTask = MutableStateFlow<Task?>(null)

    fun clearSuggestion() {
        // Delete the source scratch pad item now that the user has confirmed/dismissed
        _pendingScratchPadTask.value?.let { sourceTask ->
            viewModelScope.launch(Dispatchers.IO) {
                repository.deleteTask(sourceTask)
            }
        }
        _pendingScratchPadTask.value = null
        _aiSuggestion.value = null
    }

    // Confirm and save processed task
    fun saveTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTask(task)
            evolveProfileOnEvent("User saved task: '${task.title}' (Importance: ${task.importance}, Urgency: ${task.urgency}, Duration: ${task.estimatedMinutes}m).")
        }
    }

    // Complete / Toggle Task
    fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = task.copy(
                isCompleted = isCompleted,
                completedAt = if (isCompleted) System.currentTimeMillis() else null
            )
            repository.updateTask(updated)
            
            if (isCompleted) {
                evolveProfileOnEvent("User completed task: '${task.title}' (Importance: ${task.importance}, Urgency: ${task.urgency}, Duration: ${task.estimatedMinutes}m).")
                
                // Handle repeating task rescheduling
                task.repeatingTaskId?.let { repId ->
                    val parent = repository.getRepeatingTaskById(repId)
                    if (parent != null) {
                        val nextDue = calculateNextDueDate(
                            System.currentTimeMillis(),
                            parent.recurrenceType,
                            parent.intervalValue,
                            parent.preferredDaysOfWeek
                        )
                        val updatedParent = parent.copy(
                            lastCompletedAt = System.currentTimeMillis(),
                            nextDueDate = nextDue
                        )
                        repository.updateRepeatingTask(updatedParent)
                        
                        // Insert next task instance
                        val nextTask = Task(
                            title = parent.title,
                            description = parent.description,
                            importance = parent.importance,
                            urgency = parent.urgency,
                            estimatedMinutes = parent.estimatedMinutes,
                            deadline = nextDue,
                            isSoftDeadline = parent.isSoftDeadline,
                            graceDays = parent.graceDays,
                            repeatingTaskId = parent.id,
                            isScratchPadItem = false
                        )
                        repository.insertTask(nextTask)
                    }
                }
            } else {
                evolveProfileOnEvent("User marked task as incomplete: '${task.title}'.")
            }
        }
    }

    fun toggleSubTaskCompletion(subTask: SubTask, isCompleted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = subTask.copy(isCompleted = isCompleted)
            repository.updateSubTask(updated)
            if (isCompleted) {
                evolveProfileOnEvent("User completed subtask: '${subTask.title}'.")
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTask(task.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
            evolveProfileOnEvent("User deleted task: '${task.title}'.")
        }
    }

    fun deleteAllCompletedTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllCompletedTasks()
        }
    }

    fun saveSubTasks(subTasks: List<SubTask>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSubTasks(subTasks)
        }
    }

    // Save repeating task
    fun saveRepeatingTask(repTask: RepeatingTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val repId = repository.insertRepeatingTask(repTask)
            // Create first active instance
            val activeTask = Task(
                title = repTask.title,
                description = repTask.description,
                importance = repTask.importance,
                urgency = repTask.urgency,
                estimatedMinutes = repTask.estimatedMinutes,
                deadline = repTask.nextDueDate,
                isSoftDeadline = repTask.isSoftDeadline,
                graceDays = repTask.graceDays,
                repeatingTaskId = repId,
                isScratchPadItem = false
            )
            repository.insertTask(activeTask)
            evolveProfileOnEvent("User created repeating task: '${repTask.title}' (Recurrence: ${repTask.recurrenceType}, Interval: ${repTask.intervalValue}).")
        }
    }

    fun saveSpecialDate(specialDate: SpecialDate) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSpecialDate(specialDate)
        }
    }

    // Brain Chat Brainstorming / Onboarding
    fun startOnboardingInterview() {
        _onboardingPhase.value = 1
        _chatMessages.value = listOf(
            ChatMessage(MessageSender.AI, "Hi Jan! I am Gemma, your ADHD second brain. Let's start by talking about your daily routine. When do you feel you have the highest focus or energy, and what is your primary goal with Prioritize?")
        )
    }

    private fun copyAttachmentToInternal(context: Context, sourcePath: String): String {
        val srcFile = File(sourcePath)
        if (!srcFile.exists()) return sourcePath
        val destDir = File(context.filesDir, "chat_attachments")
        if (!destDir.exists()) destDir.mkdirs()
        val destFile = File(destDir, "${System.currentTimeMillis()}_${srcFile.name}")
        try {
            srcFile.copyTo(destFile, overwrite = true)
            return destFile.absolutePath
        } catch (e: Exception) {
            Log.e("TaskViewModel", "Failed to copy attachment: $sourcePath", e)
            return sourcePath
        }
    }

    fun sendMessageToBrain(
        text: String,
        attachedImagePath: android.net.Uri? = null,
        attachedAudioPath: android.net.Uri? = null,
        attachedDocPath: android.net.Uri? = null
    ) {
        if (text.isBlank() && attachedImagePath == null && attachedAudioPath == null && attachedDocPath == null) return
        
        _isChatLoading.value = true
        _chatAttachmentStatus.value = "Preparing message..."

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            
            // 1. Copy attachments to internal storage to persist them safely
            val savedImagePath = attachedImagePath?.let { uri ->
                withContext(Dispatchers.IO) {
                    val path = getPathFromUri(context, uri)
                    path?.let { copyAttachmentToInternal(context, it) }
                }
            }
            val savedAudioPath = attachedAudioPath?.let { uri ->
                withContext(Dispatchers.IO) {
                    val path = getPathFromUri(context, uri)
                    path?.let { copyAttachmentToInternal(context, it) }
                }
            }
            val savedDocPath = attachedDocPath?.let { uri ->
                withContext(Dispatchers.IO) {
                    val path = getPathFromUri(context, uri)
                    path?.let { copyAttachmentToInternal(context, it) }
                }
            }
            // Pre-flight RAM protection check
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val availMemMb = memInfo.availMem / (1024 * 1024)
            Log.d("TaskViewModel", "Inference pre-flight RAM: Avail = $availMemMb MB, lowMemory = ${memInfo.lowMemory}")
            if (memInfo.lowMemory || availMemMb < 200) {
                _aiErrorMsg.value = "Low memory warning! Available RAM ($availMemMb MB) is too low for reliable on-device inference. Please close background apps or tap 'Compact conversation'."
                _isChatLoading.value = false
                _chatAttachmentStatus.value = null
                return@launch
            }

            // 2. Insert user message to Room (observed asynchronously to update UI)
            withContext(Dispatchers.IO) {
                repository.insertChatMessage(
                    ChatMessageEntity(
                        sender = "USER",
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        imagePath = savedImagePath,
                        audioPath = savedAudioPath,
                        documentPath = savedDocPath
                    )
                )
            }

            val profile = repository.getUserProfile()
            val systemPrompt = profile?.systemPrompt ?: ""

            if (_onboardingPhase.value == 1) {
                // Dynamic Onboarding/Refinement Interview flow
                val historyList = repository.chatMessagesFlow.first().map { it.toChatMessage() }
                val prompt = """
                    You are Gemma, an executive function coach conducting an onboarding/refinement interview to build Jan's cognitive profile.
                    Jan is an engineer, husband to Angelique (anniversary Apr 7, birthday Sep 22), father to Ansunet (birthday Nov 8) and Johan-Henry (birthday Nov 20).
                    
                    Current System Prompt:
                    "$systemPrompt"
                    
                    Based on the interview history below, formulate the next highly relevant follow-up question.
                    Focus on identifying focus energy peaks, forgotten routines/chores, family dates, or ADHD coping styles.
                    Keep the response supportive, warm, and brief. Ask exactly ONE question.
                    
                    If you have collected sufficient details and are ready to compile the profile, read the Current System Prompt, update/add these new insights to it, and output:
                    ###INTERVIEW_COMPLETE### followed by the updated complete system prompt. Do not output anything else.
                    
                    Interview History (Last 5 messages):
                    ${historyList.takeLast(5).joinToString("\n") { "${it.sender.name}: ${it.text}" }}
                    
                    AI Response:
                """.trimIndent()

                val aiResponse = withContext(Dispatchers.Default) {
                    parser.runRawInference(prompt)
                } ?: "Could you tell me more about your daily routine?"

                if (aiResponse.contains("###INTERVIEW_COMPLETE###")) {
                    val finalPrompt = aiResponse.substringAfter("###INTERVIEW_COMPLETE###").trim()
                    updateSystemPrompt(finalPrompt)
                    _onboardingPhase.value = 0
                    
                    withContext(Dispatchers.IO) {
                        repository.insertChatMessage(
                            ChatMessageEntity(
                                sender = "AI",
                                text = "Awesome! Your personalized second brain system prompt has been generated and saved. You can read or edit it at the top of the screen. Let's brainstorm some tasks now!",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        repository.insertChatMessage(
                            ChatMessageEntity(
                                sender = "AI",
                                text = aiResponse,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
                _isChatLoading.value = false
                _chatAttachmentStatus.value = null
            } else {
                // 1. Fetch cloudApiKey
                var cloudApiKey: String? = null
                profile?.let { prof ->
                    try {
                        val meta = JSONObject(prof.metadataJson)
                        if (meta.has("gemini_api_key")) {
                            cloudApiKey = meta.getString("gemini_api_key").trim()
                        }
                    } catch (e: Exception) {}
                }

                val summarySection = try {
                    val json = JSONObject(profile?.metadataJson ?: "{}")
                    if (json.has("compaction_summary")) {
                        val s = json.getString("compaction_summary").trim()
                        if (s.isNotEmpty()) {
                            "### CONVERSATION SUMMARY ###\n$s\n"
                        } else ""
                    } else ""
                } catch (e: Exception) { "" }

                // 2. Resolve document type and read content
                var docPromptText = ""
                var pdfBitmapForLocalMultimodal: android.graphics.Bitmap? = null
                var isPdf = false
                savedDocPath?.let { path ->
                    val ext = path.substringAfterLast('.', "").lowercase()
                    isPdf = ext == "pdf"
                    val isTextFile = ext == "txt" || ext == "md" || ext == "log" || ext == "json" || ext == "csv"
                    if (isTextFile) {
                        try {
                            val fileContent = File(path).readText()
                            val filename = File(path).name.substringAfter('_')
                            docPromptText = "\n\n### ATTACHED FILE: $filename ###\n```text\n$fileContent\n```\n"
                            Log.d("TaskViewModel", "Injected text file attachment: $filename, size=${fileContent.length}")
                        } catch (e: Exception) {
                            Log.e("TaskViewModel", "Failed to read text attachment content", e)
                        }
                    } else if (isPdf && cloudApiKey.isNullOrBlank()) {
                        // Local mode PDF: render page 1 to bitmap
                        pdfBitmapForLocalMultimodal = renderPdfPageToBitmap(context, path, 0)
                        Log.d("TaskViewModel", "Rendered PDF first page as image bitmap for local OCR")
                    }
                }

                // 3. Keyword Scan RAG memory profiles
                val allProfiles = repository.getMemoryProfiles()
                val matchedFacts = mutableListOf<String>()
                allProfiles.forEach { mp ->
                    val aliases = mp.keywordsCsv.split(",").map { it.trim().lowercase() }
                    if (aliases.any { text.lowercase().contains(it) }) {
                        try {
                            val arr = JSONArray(mp.factsJson)
                            for (i in 0 until arr.length()) {
                                matchedFacts.add("- ${mp.title}: ${arr.getString(i)}")
                            }
                        } catch (e: Exception) {}
                    }
                }
                val matchedFactsSection = if (matchedFacts.isNotEmpty()) {
                    "### MATCHED OFFLINE MEMORIES ###\n" + matchedFacts.take(5).joinToString("\n")
                } else {
                    ""
                }

                // 4. Date and Deadline context
                val cal = Calendar.getInstance()
                val dateFormat = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale.getDefault())
                val todayStr = dateFormat.format(cal.time)

                val activeTasksList = repository.getActiveTasks()
                val upcomingDeadlines = activeTasksList
                    .filter { it.deadline != null }
                    .sortedBy { it.deadline }
                    .take(3)
                    .map { task ->
                        val deadlineStr = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(task.deadline)
                        "- ${task.title} (due $deadlineStr, Priority: ${task.importance})"
                    }
                val deadlineSection = if (upcomingDeadlines.isNotEmpty()) {
                    "### UPCOMING DEADLINES ###\n" + upcomingDeadlines.joinToString("\n")
                } else {
                    ""
                }

                // 5. Stateless context compilation (last 8 rolling + promoted messages)
                val allMsgs = withContext(Dispatchers.IO) { repository.chatMessagesFlow.first() }
                val promoted = allMsgs.filter { it.isPromoted }
                val last8 = allMsgs.takeLast(8)
                val contextMsgs = (promoted + last8).distinctBy { it.id }.sortedBy { it.timestamp }.map { it.toChatMessage() }
                
                val cappedHistory = contextMsgs.joinToString("\n") {
                    val contextPin = if (it.isPromoted) " [PINNED CONTEXT]" else ""
                    "${it.sender.name}: ${it.text}$contextPin"
                }

                val userPromptText = if (docPromptText.isNotEmpty()) {
                    "\"$text\"$docPromptText"
                } else {
                    "\"$text\""
                }

                val promptContext = """
                    $systemPrompt
                    
                    Today's Date: $todayStr
                    
                    $deadlineSection
                    
                    $matchedFactsSection
                    
                    $summarySection
                    The user says: $userPromptText
                    Brainstorm with them. Support them, reduce overwhelm, and suggest concrete actions.
                    Use the matched offline memories and deadlines context if relevant. Keep responses brief.
                    
                    Conversation History (Rolling 8 + Pinned contexts):
                    $cappedHistory
                    
                    If you need to search the web to answer the user's question (e.g. they ask about scheduling, local events, pickup dates, weather, or facts), respond ONLY with this line:
                    ###SEARCH_CALL### {"query": "search query here"}
                    
                    If you identify a task to add to their scratch pad, append at the end:
                    ###TASK_SUGGESTION### {"title": "Task title", "description": "Description", "importance": 1..10, "urgency": 1..10}
For repeating/recurring tasks, append:
###REPEATING_TASK_SUGGESTION### {"title": "Task title", "description": "Desc", "importance": 1..10, "urgency": 1..10, "recurrenceType": "DAILY/WEEKLY/MONTHLY/YEARLY", "intervalValue": 1}
                """.trimIndent()

                Log.d("RAG_Telemetry", "Final chat prompt compiled size: ${promptContext.length} chars (~${promptContext.length / 4} tokens)")

                var aiResponse = "I'm listening, tell me more."
                try {
                    if (!cloudApiKey.isNullOrBlank() && (savedImagePath != null || savedAudioPath != null || (savedDocPath != null && isPdf))) {
                        _chatAttachmentStatus.value = "Sending attachments to cloud..."
                        val imageBitmap = savedImagePath?.let {
                            android.graphics.BitmapFactory.decodeFile(it)
                        }
                        val audioBytes = savedAudioPath?.let {
                            withContext(Dispatchers.IO) { File(it).readBytes() }
                        }
                        val docBytes = if (savedDocPath != null && isPdf) {
                            withContext(Dispatchers.IO) { File(savedDocPath).readBytes() }
                        } else null
                        
                        val attachmentBytes = audioBytes ?: docBytes
                        val attachmentMime = if (audioBytes != null) {
                            val ext = savedAudioPath?.substringAfterLast('.', "mp3")?.lowercase() ?: "mp3"
                            when (ext) {
                                "mp3" -> "audio/mp3"
                                "m4a", "mp4" -> "audio/m4a"
                                "wav" -> "audio/wav"
                                "ogg" -> "audio/ogg"
                                "aac" -> "audio/aac"
                                else -> "audio/mp3"
                            }
                        } else {
                            "application/pdf"
                        }
                        
                        aiResponse = runGeminiCloudInference(cloudApiKey!!, promptContext, imageBitmap, attachmentBytes, attachmentMime)
                            ?: "No response from Cloud Gemini."
                    } else {
                        val imageBitmap = (savedImagePath?.let {
                            try {
                                android.graphics.BitmapFactory.decodeFile(it)
                            } catch (e: Exception) {
                                Log.e("BrainInference", "Failed to decode image $it", e)
                                null
                            }
                        }) ?: pdfBitmapForLocalMultimodal
                        
                        val audioBytes = savedAudioPath?.let {
                            try {
                                _chatAttachmentStatus.value = "Decoding audio file..."
                                withContext(Dispatchers.IO) {
                                    com.example.prioritize.audio.AudioDecoder.decodeToPcm(it)
                                }
                            } catch (e: Exception) {
                                Log.e("BrainInference", "Failed to decode audio PCM $it", e)
                                null
                            }
                        }

                        _chatAttachmentStatus.value = "Thinking..."
                        val rawResult = withContext(Dispatchers.Default) {
                            parser.runMultimodalInference(promptContext, imageBitmap, audioBytes)
                        }
                        if (rawResult == null) {
                            val isNpuModel = _activeModelSpec.value.filename.contains("Tensor_G5")
                            val isCpuOrGpu = activeBackend.startsWith("CPU") || activeBackend.startsWith("GPU")
                            if (isNpuModel && isCpuOrGpu) {
                                _aiErrorMsg.value = "Chat fallback triggered. The Tensor G5 NPU precompiled model cannot run on CPU. Please switch to the standard 'Gemma 4 E2B (Thinking)' model in Settings."
                            } else {
                                _aiErrorMsg.value = "Chat fallback triggered. Please verify your active model in Settings."
                            }
                        }
                        aiResponse = rawResult ?: "I'm listening, tell me more."
                    }

                    // RAG Agent Web Search check
                    if (aiResponse.contains("###SEARCH_CALL###")) {
                        val parts = aiResponse.split("###SEARCH_CALL###")
                        val json = JSONObject(parts[1].trim())
                        val searchQuery = json.getString("query")
                        
                        _chatAttachmentStatus.value = "Searching..."
                        val searchIndicatorId = withContext(Dispatchers.IO) {
                            repository.insertChatMessage(
                                ChatMessageEntity(
                                    sender = "AI",
                                    text = "🔍 Searching the web for: \"$searchQuery\"...",
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                        
                        val searchResults = performWebSearch(searchQuery)
                        
                        val ragPrompt = """
                            Current system profile:
                            $systemPrompt
                            
                            The user asked: "$text"
                            Here are the web search results for "$searchQuery":
                            $searchResults
                            
                            Answer the user's question factually and directly based on the search results.
                            If you identify a task to add, append at the end:
                            ###TASK_SUGGESTION### {"title": "Task title", "description": "Description", "importance": 1..10, "urgency": 1..10}
For repeating/recurring tasks, append:
###REPEATING_TASK_SUGGESTION### {"title": "Task title", "description": "Desc", "importance": 1..10, "urgency": 1..10, "recurrenceType": "DAILY/WEEKLY/MONTHLY/YEARLY", "intervalValue": 1}
                        """.trimIndent()
                        
                        if (!cloudApiKey.isNullOrBlank() && (savedImagePath != null || savedAudioPath != null || (savedDocPath != null && isPdf))) {
                            val imageBitmap = savedImagePath?.let { android.graphics.BitmapFactory.decodeFile(it) }
                            val audioBytes = savedAudioPath?.let { withContext(Dispatchers.IO) { File(it).readBytes() } }
                            val docBytes = if (savedDocPath != null && isPdf) {
                                withContext(Dispatchers.IO) { File(savedDocPath).readBytes() }
                            } else null
                            
                            val attachmentBytes = audioBytes ?: docBytes
                            val attachmentMime = if (audioBytes != null) {
                                val ext = savedAudioPath?.substringAfterLast('.', "mp3")?.lowercase() ?: "mp3"
                                when (ext) {
                                    "mp3" -> "audio/mp3"
                                    "m4a", "mp4" -> "audio/m4a"
                                    "wav" -> "audio/wav"
                                    "ogg" -> "audio/ogg"
                                    "aac" -> "audio/aac"
                                    else -> "audio/mp3"
                                }
                            } else {
                                "application/pdf"
                            }
                            aiResponse = runGeminiCloudInference(cloudApiKey!!, ragPrompt, imageBitmap, attachmentBytes, attachmentMime)
                                ?: "Failed to retrieve search response."
                        } else {
                            val imageBitmap = (savedImagePath?.let { android.graphics.BitmapFactory.decodeFile(it) }) ?: pdfBitmapForLocalMultimodal
                            val audioBytes = savedAudioPath?.let {
                                withContext(Dispatchers.IO) { com.example.prioritize.audio.AudioDecoder.decodeToPcm(it) }
                            }
                            aiResponse = withContext(Dispatchers.Default) {
                                parser.runMultimodalInference(ragPrompt, imageBitmap, audioBytes)
                            } ?: "Failed to retrieve search response."
                        }
                    }

                    val aiMsg = parseChatMessage(MessageSender.AI, aiResponse)
                    withContext(Dispatchers.IO) {
                        repository.insertChatMessage(
                            aiMsg.toEntity().copy(timestamp = System.currentTimeMillis())
                        )
                    }
                } catch (e: Exception) {
                    Log.e("TaskViewModel", "Error in chat generation", e)
                    _aiErrorMsg.value = "Failed to process message: ${e.message}"
                    withContext(Dispatchers.IO) {
                        repository.insertChatMessage(
                            ChatMessageEntity(
                                sender = "SYSTEM_ERROR",
                                text = "Error: ${e.message ?: "Failed to process image/audio attachment."}",
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                } finally {
                    _isChatLoading.value = false
                    _chatAttachmentStatus.value = null
                }
            }
        }
    }

    // AI Profile Evolving logic (Queues action to ObservationLog for background dreaming).
    // Wrapped in try/catch so a DB failure here never crashes the calling operation.
    private suspend fun evolveProfileOnEvent(eventDescription: String) {
        try {
            val log = ObservationLog(
                timestamp = System.currentTimeMillis(),
                description = eventDescription
            )
            repository.insertObservationLog(log)
            Log.d("Observation", "Logged action: $eventDescription")
        } catch (e: Exception) {
            Log.e("Observation", "Failed to log observation: $eventDescription", e)
        }
    }

    fun updateSystemPrompt(newPrompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProfile = repository.getUserProfile()
            val updated = currentProfile?.copy(systemPrompt = newPrompt) ?: UserProfile(id = 1, systemPrompt = newPrompt, metadataJson = "{}")
            repository.insertUserProfile(updated)
        }
    }

    // Repeating Task date calculator
    private fun calculateNextDueDate(fromTime: Long, recurrenceType: RecurrenceType, interval: Int, preferredDays: String?): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fromTime
        
        when (recurrenceType) {
            RecurrenceType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, interval)
            // WEEKLY and CUSTOM_DAYS share the same day-of-week navigation logic
            RecurrenceType.WEEKLY, RecurrenceType.CUSTOM_DAYS -> {
                if (!preferredDays.isNullOrBlank()) {
                    val daysToAdd = nextDayFromPreferredList(
                        cal.get(Calendar.DAY_OF_WEEK),
                        preferredDays.split(",").map { it.toInt() }.sorted()
                    )
                    cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                } else {
                    // Fallback when no preferred days set
                    if (recurrenceType == RecurrenceType.WEEKLY) cal.add(Calendar.WEEK_OF_YEAR, interval)
                    else cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            RecurrenceType.MONTHLY -> cal.add(Calendar.MONTH, interval)
            RecurrenceType.YEARLY  -> cal.add(Calendar.YEAR, interval)
        }
        return cal.timeInMillis
    }

    /**
     * Returns the number of days from [currentDay] (Calendar.DAY_OF_WEEK) until the
     * next day in [targetDays] (sorted list of Calendar day-of-week integers).
     * Wraps around to next week if no future day is found this week.
     */
    private fun nextDayFromPreferredList(currentDay: Int, targetDays: List<Int>): Int {
        for (day in targetDays) {
            if (day > currentDay) return day - currentDay
        }
        // Wrap around: distance from currentDay to end of week + first target day next week
        return (7 - currentDay) + targetDays.first()
    }

    // ─────────────────────────────────────────────────────────────
    //  Backup / Export / Import — delegates to BackupManager
    //  All serialisation logic lives in BackupManager.kt
    // ─────────────────────────────────────────────────────────────

    fun exportDatabase(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.exportDatabase(outputStream, repository)
        }
    }

    fun exportAgentHandbook(outputStream: OutputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.exportAgentHandbook(outputStream, repository)
        }
    }

    fun importDatabase(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupManager.importDatabase(inputStream, repository)
        }
    }

    private fun handleParserAction(action: Action) {
        viewModelScope.launch {
            when (action) {
                is CreateTaskAction -> {
                    val deadline = action.daysUntilDue?.let {
                        System.currentTimeMillis() + (it.toLong() * 24L * 60L * 60L * 1000L)
                    }
                    val task = Task(
                        title = action.title,
                        description = action.description,
                        importance = action.importance,
                        urgency = action.urgency,
                        estimatedMinutes = action.estimatedMinutes,
                        deadline = deadline,
                        isScratchPadItem = true // Place in ScratchPad/Inbox for review
                    )
                    saveTask(task)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "✶ Task Suggestion Created: ${action.title}", Toast.LENGTH_LONG).show()
                    }
                }
                is CreateRepeatingTaskAction -> {
                    val repeatingTask = RepeatingTask(
                        title = action.title,
                        description = action.description,
                        recurrenceType = RecurrenceType.entries.find { it.name == action.recurrenceType } ?: RecurrenceType.WEEKLY,
                        intervalValue = action.intervalValue,
                        importance = action.importance,
                        urgency = action.urgency,
                        nextDueDate = System.currentTimeMillis() + (24L * 60 * 60 * 1000)
                    )
                    saveRepeatingTask(repeatingTask)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "🔁 Repeating Task Created: ${action.title}", Toast.LENGTH_LONG).show()
                    }
                }
                is CreateSpecialDateAction -> {
                    val specialDate = SpecialDate(
                        name = action.name,
                        dateMonth = action.month,
                        dateDay = action.day,
                        type = SpecialDateType.entries.find { it.name == action.type } ?: SpecialDateType.BIRTHDAY
                    )
                    saveSpecialDate(specialDate)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(getApplication(), "📅 Special Date Created: ${action.name}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun toggleMessagePromotion(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateChatMessagePromotion(message.id, !message.isPromoted)
        }
    }

    fun clearAllChatHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllChatHistory()
        }
    }

    fun updateUserAccent(accent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile()
            if (profile != null) {
                repository.updateUserProfile(profile.copy(userAccent = accent))
            }
        }
    }

    fun addKnownSpeaker(name: String, accent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile() ?: return@launch
            try {
                val arr = JSONArray(profile.knownSpeakersJson)
                var exists = false
                for (i in 0 until arr.length()) {
                    if (arr.getJSONObject(i).getString("name").equals(name, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    val obj = JSONObject().apply {
                        put("name", name)
                        put("accent", accent)
                    }
                    arr.put(obj)
                    repository.updateUserProfile(profile.copy(knownSpeakersJson = arr.toString()))
                }
            } catch(e: Exception) {
                Log.e("ViewModel", "Failed to add speaker", e)
            }
        }
    }

    fun refineTranscription(rawText: String, spokenLanguage: String, onResult: (String) -> Unit) {
        _isChatLoading.value = true
        _chatAttachmentStatus.value = "Refining transcription..."
        viewModelScope.launch {
            val profile = repository.getUserProfile()
            val accent = profile?.userAccent ?: "South African Afrikaans"
            
            val speakersJson = profile?.knownSpeakersJson ?: "[]"
            val speakersList = mutableListOf<String>()
            try {
                val arr = JSONArray(speakersJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    speakersList.add("- ${obj.getString("name")} (Accent: ${obj.getString("accent")})")
                }
            } catch(e: Exception) {}
            val speakersSection = if (speakersList.isNotEmpty()) {
                "### KNOWN SPEAKERS ###\n" + speakersList.joinToString("\n")
            } else {
                ""
            }

            val cal = Calendar.getInstance()
            val dateFormat = java.text.SimpleDateFormat("EEEE, dd MMMM yyyy", java.util.Locale.getDefault())
            val todayStr = dateFormat.format(cal.time)

            val activeTasksList = repository.getActiveTasks()
            val upcomingDeadlines = activeTasksList
                .filter { it.deadline != null }
                .sortedBy { it.deadline }
                .take(3)
                .map { "- ${it.title}" }
            val deadlineSection = if (upcomingDeadlines.isNotEmpty()) {
                "### ACTIVE TASKS CONTEXT ###\n" + upcomingDeadlines.joinToString("\n")
            } else {
                ""
            }

            val prompt = """
                You are a transcription refinement assistant for Jan (Accent: $accent).
                The spoken language is $spokenLanguage.
                $speakersSection
                
                $deadlineSection
                
                Today's Date: $todayStr
                
                Raw Speech Recognition output:
                "$rawText"
                
                Refine this transcription to improve the quality:
                1. Correct spelling of names and terms based on the context and accents (e.g., correct "An Sunette" to "Ansunet", "Ann Gelleek" to "Angelique", "testament" to "testament").
                2. If multiple speakers are clearly speaking in the text, insert speaker labels (e.g., "Jan: ... \n Angelique: ...").
                3. Maintain the original meaning. Do not summarize. Return ONLY the refined transcript.
            """.trimIndent()

            val refinedResult = withContext(Dispatchers.Default) {
                parser.runRawInference(prompt)
            } ?: rawText
            
            _isChatLoading.value = false
            _chatAttachmentStatus.value = null
            withContext(Dispatchers.Main) {
                onResult(refinedResult)
            }
        }
    }

    fun transcribeAudioFile(audioUri: android.net.Uri, spokenLanguage: String, onResult: (String, String) -> Unit) {
        _isChatLoading.value = true
        _chatAttachmentStatus.value = "Resolving audio file..."
        viewModelScope.launch {
            var cloudApiKey: String? = null
            try {
                val context = getApplication<Application>().applicationContext
                val attachedAudioPath = withContext(Dispatchers.IO) { getPathFromUri(context, audioUri) }
                if (attachedAudioPath == null) {
                    _aiErrorMsg.value = "Failed to resolve audio file path."
                    return@launch
                }
                
                _chatAttachmentStatus.value = "Decoding audio file..."
                val savedAudioPath = withContext(Dispatchers.IO) { copyAttachmentToInternal(context, attachedAudioPath) }
                
                val profile = repository.getUserProfile()
                val accent = profile?.userAccent ?: "South African Afrikaans"
                val prompt = "Transcribe the spoken words in this audio recording. The language spoken is $spokenLanguage and the speaker has a $accent accent. Output only the transcription, do not summarize or add commentary."

                profile?.let { prof ->
                    try {
                        val meta = JSONObject(prof.metadataJson)
                        if (meta.has("gemini_api_key")) {
                            cloudApiKey = meta.getString("gemini_api_key").trim()
                        }
                    } catch (e: Exception) {}
                }

                val transcriptResult = if (!cloudApiKey.isNullOrBlank()) {
                    _chatAttachmentStatus.value = "Transcribing via Gemini Cloud..."
                    val audioBytes = withContext(Dispatchers.IO) { File(savedAudioPath).readBytes() }
                    val ext = savedAudioPath.substringAfterLast('.', "mp3").lowercase()
                    val mimeType = when (ext) {
                        "mp3" -> "audio/mp3"
                        "m4a", "mp4" -> "audio/m4a"
                        "wav" -> "audio/wav"
                        "ogg" -> "audio/ogg"
                        "aac" -> "audio/aac"
                        else -> "audio/mp3"
                    }
                    runGeminiCloudInference(cloudApiKey!!, prompt, null, audioBytes, mimeType)
                } else {
                    _chatAttachmentStatus.value = "Decoding audio file..."
                    val localPcmBytes = try {
                        withContext(Dispatchers.IO) {
                            com.example.prioritize.audio.AudioDecoder.decodeToPcm(savedAudioPath)
                        }
                    } catch(e: Exception) {
                        throw IllegalStateException("Failed to decode/resample attached audio file.", e)
                    }
                    _chatAttachmentStatus.value = "Transcribing audio locally..."
                    withContext(Dispatchers.Default) {
                        parser.runMultimodalInference(prompt, null, localPcmBytes)
                    }
                } ?: "Could not transcribe audio."

                _chatAttachmentStatus.value = "Refining transcription..."
                refineTranscription(transcriptResult, spokenLanguage) { refined ->
                    onResult(transcriptResult, refined)
                }
            } catch (e: Exception) {
                Log.e("TaskViewModel", "Error transcribing audio file", e)
                val errMsg = if (cloudApiKey.isNullOrBlank()) {
                    "Local audio transcription failed: ${e.message ?: "Unsupported model/backend"}. Please switch to cloud mode by entering a Gemini API Key under 'Brain Settings' inside the settings sheet."
                } else {
                    "Audio transcription failed. Please make sure your Gemini API Key is correct under 'Brain Settings' in Settings."
                }
                _aiErrorMsg.value = errMsg
            } finally {
                _isChatLoading.value = false
                _chatAttachmentStatus.value = null
            }
        }
    }

    fun updateSpokenLanguages(languages: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile() ?: return@launch
            try {
                val json = JSONObject(profile.metadataJson)
                val arr = JSONArray()
                languages.forEach { arr.put(it) }
                json.put("spoken_languages", arr)
                repository.updateUserProfile(profile.copy(metadataJson = json.toString()))
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to update spoken languages", e)
            }
        }
    }

    fun compactConversation() {
        viewModelScope.launch {
            _isChatLoading.value = true
            _chatAttachmentStatus.value = "Compacting context..."
            try {
                val profile = withContext(Dispatchers.IO) { repository.getUserProfile() } ?: return@launch
                val allMsgs = withContext(Dispatchers.IO) { repository.chatMessagesFlow.first() }
                
                val historyToSummarize = allMsgs
                    .filter { it.sender == "User" || it.sender == "AI" }
                    .filter { !it.text.startsWith("♻️") && !it.text.startsWith("🔍") }
                    .takeLast(15)
                
                if (historyToSummarize.isNotEmpty()) {
                    var cloudApiKey: String? = null
                    try {
                        val meta = JSONObject(profile.metadataJson)
                        if (meta.has("gemini_api_key")) {
                            cloudApiKey = meta.getString("gemini_api_key").trim()
                        }
                    } catch (e: Exception) {}

                    val previousSummary = try {
                        val json = JSONObject(profile.metadataJson)
                        if (json.has("compaction_summary")) json.getString("compaction_summary") else ""
                    } catch (e: Exception) { "" }

                    val historyText = historyToSummarize.joinToString("\n") { "${it.sender}: ${it.text}" }

                    val summarizationPrompt = """
                        You are a helpful AI assistant.
                        Below is a summary of the conversation so far, followed by the latest messages.
                        Write a brand new, updated summary of the conversation in 2 to 3 sentences.
                        Focus on key decisions, task agreements, and personal contexts the user has shared.
                        Do not add any meta-commentary, introductory text, or signatures. Only output the 2-3 sentence summary.
                        
                        Previous Summary:
                        $previousSummary
                        
                        Latest Messages:
                        $historyText
                        
                        Updated Summary:
                    """.trimIndent()

                    val summaryResult = if (!cloudApiKey.isNullOrBlank()) {
                        runGeminiCloudInference(cloudApiKey, summarizationPrompt, null, null, "text/plain")
                    } else {
                        withContext(Dispatchers.Default) {
                            parser.runRawInference(summarizationPrompt)
                        }
                    }

                    val cleanSummary = summaryResult?.trim()?.removeSurrounding("\"")
                    if (!cleanSummary.isNullOrBlank()) {
                        Log.d("TaskViewModel", "New compaction summary generated: $cleanSummary")
                        try {
                            val json = JSONObject(profile.metadataJson)
                            json.put("compaction_summary", cleanSummary)
                            withContext(Dispatchers.IO) {
                                repository.updateUserProfile(profile.copy(metadataJson = json.toString()))
                            }
                        } catch (e: Exception) {
                            Log.e("TaskViewModel", "Failed to save compaction summary", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TaskViewModel", "Error during compaction summarization", e)
            } finally {
                withContext(Dispatchers.Default) {
                    com.example.prioritize.ai.Gemma4Parser.resetContextCounter()
                }
                withContext(Dispatchers.IO) {
                    repository.insertChatMessage(
                        ChatMessageEntity(
                            sender = "AI",
                            text = "♻️ *Conversation context compacted.* The second brain's memory cache has been cleared to prevent slowdowns and memory errors. The message history remains visible above, but the AI starts with a fresh context window.",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                _isChatLoading.value = false
                _chatAttachmentStatus.value = null
            }
        }
    }

    fun updateGeminiApiKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = repository.getUserProfile() ?: return@launch
            try {
                val json = JSONObject(profile.metadataJson)
                json.put("gemini_api_key", key)
                repository.updateUserProfile(profile.copy(metadataJson = json.toString()))
            } catch (e: Exception) {
                Log.e("ViewModel", "Failed to update API key", e)
            }
        }
    }

    private suspend fun runGeminiCloudInference(
        apiKey: String,
        prompt: String,
        imageBitmap: android.graphics.Bitmap? = null,
        audioBytes: ByteArray? = null,
        audioMimeType: String = "audio/mp3"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val root = org.json.JSONObject()
            val contentsArray = org.json.JSONArray()
            val contentObj = org.json.JSONObject()
            contentObj.put("role", "user")
            
            val partsArray = org.json.JSONArray()
            
            val textPart = org.json.JSONObject()
            textPart.put("text", prompt)
            partsArray.put(textPart)
            
            if (imageBitmap != null) {
                val bos = java.io.ByteArrayOutputStream()
                imageBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bos)
                val base64Image = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
                
                val imagePart = org.json.JSONObject()
                val inlineData = org.json.JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", base64Image)
                imagePart.put("inlineData", inlineData)
                partsArray.put(imagePart)
            }
            
            if (audioBytes != null) {
                val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
                val audioPart = org.json.JSONObject()
                val inlineData = org.json.JSONObject()
                inlineData.put("mimeType", audioMimeType)
                inlineData.put("data", base64Audio)
                audioPart.put("inlineData", inlineData)
                partsArray.put(audioPart)
            }
            
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)
            
            val requestBody = root.toString()
            conn.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }
            
            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = org.json.JSONObject(responseText)
                val candidates = responseJson.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).getString("text")
                    }
                }
                null
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e("GeminiCloud", "Error response: $responseCode - $errorText")
                throw IllegalStateException("Cloud API error: $responseCode")
            }
        } catch (e: Exception) {
            Log.e("GeminiCloud", "Request failed", e)
            throw e
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTask(task.copy(isDeleted = false, deletedAt = null))
            evolveProfileOnEvent("User restored task: '${task.title}'.")
        }
    }

    fun permanentlyDeleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(task)
            repository.deleteSubTasksForTask(task.id)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.emptyRecycleBin()
        }
    }

    private fun getPathFromUri(context: Context, uri: android.net.Uri): String? {
        if (uri.scheme == "file") return uri.path
        
        try {
            val contentResolver = context.contentResolver
            var fileName = "temp_upload_${System.currentTimeMillis()}.bin"
            
            // Query for original file name and extension
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val displayName = it.getString(nameIndex)
                        if (!displayName.isNullOrBlank()) {
                            fileName = displayName
                        }
                    }
                }
            }
            
            val tempFile = File(context.cacheDir, "${System.currentTimeMillis()}_$fileName")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile.absolutePath
        } catch (e: Exception) {
            Log.e("TaskViewModel", "Failed to resolve path from URI: $uri", e)
            return null
        }
    }

    private fun renderPdfPageToBitmap(context: Context, pdfFilePath: String, pageIndex: Int = 0): android.graphics.Bitmap? {
        var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
        var parcelFileDescriptor: android.os.ParcelFileDescriptor? = null
        var page: android.graphics.pdf.PdfRenderer.Page? = null
        try {
            val file = File(pdfFilePath)
            if (!file.exists()) return null
            parcelFileDescriptor = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
            if (pdfRenderer.pageCount <= pageIndex) return null
            page = pdfRenderer.openPage(pageIndex)
            val maxWidth = 1200
            val maxHeight = 1600
            val aspectRatio = page.width.toFloat() / page.height.toFloat()
            var width = page.width
            var height = page.height
            if (width > maxWidth || height > maxHeight) {
                if (aspectRatio > 1) {
                    width = maxWidth
                    height = (maxWidth / aspectRatio).toInt()
                } else {
                    height = maxHeight
                    width = (maxHeight * aspectRatio).toInt()
                }
            }
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } catch (e: Exception) {
            Log.e("TaskViewModel", "Failed to render PDF page to Bitmap", e)
            return null
        } finally {
            try { page?.close() } catch (e: Exception) {}
            try { pdfRenderer?.close() } catch (e: Exception) {}
            try { parcelFileDescriptor?.close() } catch (e: Exception) {}
        }
    }

    class Factory(
        private val application: Application,
        private val repository: TaskRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
                return TaskViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
