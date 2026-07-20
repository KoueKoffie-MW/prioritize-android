package com.example.prioritize.ui.screens

import android.widget.Toast
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import android.net.Uri
import com.example.prioritize.ui.components.TranscriptionReviewDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prioritize.ui.viewmodel.ChatMessage
import com.example.prioritize.ui.viewmodel.MessageSender
import com.example.prioritize.ui.viewmodel.TaskViewModel
import com.example.prioritize.ui.viewmodel.AVAILABLE_MODELS
import com.example.prioritize.ui.viewmodel.EdgeModelSpec
import com.example.prioritize.ui.viewmodel.DeviceHardware
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

enum class LanguageSelectorType { RECORDING, UPLOAD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainScreen(viewModel: TaskViewModel) {
    val context = LocalContext.current
    // Read status bar height directly from View system, bypassing Compose inset consumption
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarTopDp = remember(view) {
        val px = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        with(density) { px.toDp() }
    }
    val userProfile by viewModel.userProfile.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatLoading by viewModel.isChatLoading.collectAsState()
    val chatAttachmentStatus by viewModel.chatAttachmentStatus.collectAsState()
    val onboardingPhase by viewModel.onboardingPhase.collectAsState()
    val userAccent by viewModel.userAccent.collectAsState()

    val isModelAvailable by viewModel.isModelAvailable.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val activeModelSpec by viewModel.activeModelSpec.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    val contextFillRatio by viewModel.contextFillRatio.collectAsState()
    val attachedDocUri by viewModel.attachedDocUri.collectAsState()
    val attachedDocName by viewModel.attachedDocName.collectAsState()

    var chatInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    
    // Transcription review states
    var showReviewDialog by remember { mutableStateOf(false) }
    var rawTranscriptText by remember { mutableStateOf("") }
    var refinedTranscriptText by remember { mutableStateOf("") }
    
    // Speech recording state
    var isRecordingSpeech by remember { mutableStateOf(false) }
    var partialTranscriptText by remember { mutableStateOf("") }

    val spokenLanguages = remember(userProfile) {
        val defaultLangs = listOf("English", "Afrikaans", "German")
        userProfile?.let { profile ->
            try {
                val json = org.json.JSONObject(profile.metadataJson)
                if (json.has("spoken_languages")) {
                    val arr = json.getJSONArray("spoken_languages")
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    if (list.isNotEmpty()) list else defaultLangs
                } else defaultLangs
            } catch (e: Exception) { defaultLangs }
        } ?: defaultLangs
    }
    
    var showLanguageSelectorFor by remember { mutableStateOf<LanguageSelectorType?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            com.example.prioritize.audio.SpeechTranscriber.cancel()
        }
    }

    // Pick image launcher
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedImageUri = uri
    }
    
    // Pick audio launcher
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedAudioUri = uri
    }

    // Pick document launcher
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = try {
                var displayName = "document"
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val disp = cursor.getString(nameIndex)
                            if (!disp.isNullOrBlank()) {
                                displayName = disp
                            }
                        }
                    }
                }
                displayName
            } catch (e: Exception) { "document" }
            viewModel.setAttachedDoc(it, name)
        }
    }

    val chatListState = rememberLazyListState()

    var showEditPromptDialog by remember { mutableStateOf(false) }
    var targetImportFilename by remember { mutableStateOf("") }
    var modelToConfirmActive by remember { mutableStateOf<EdgeModelSpec?>(null) }
    var showDiagDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showFeedbackSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // GitHub auth state
    val isGitHubLoggedIn by viewModel.isGitHubLoggedIn.collectAsState()
    val isGitHubLoggingIn by viewModel.isGitHubLoggingIn.collectAsState()
    val gitHubUserCode by viewModel.gitHubUserCode.collectAsState()
    val feedbackSubmitState by viewModel.feedbackSubmitState.collectAsState()

    val aiErrorMsg by viewModel.aiErrorMsg.collectAsState()
    LaunchedEffect(aiErrorMsg) {
        aiErrorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAiError()
        }
    }

    val modelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (targetImportFilename.isNotEmpty()) viewModel.importModelFile(it, targetImportFilename)
        }
    }

    LaunchedEffect(downloadedModels) {
        if (downloadedModels.isEmpty()) showSettingsSheet = true
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(it)
                if (outputStream != null) {
                    viewModel.exportDatabase(outputStream)
                    Toast.makeText(context, "Database exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.importDatabase(inputStream)
                    Toast.makeText(context, "Database imported. Restart app to verify.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val agentExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            try {
                val outputStream = context.contentResolver.openOutputStream(it)
                if (outputStream != null) {
                    viewModel.exportAgentHandbook(outputStream)
                    Toast.makeText(context, "Agent ZIP exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ZIP export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(chatMessages.size, isChatLoading) {
        val totalCount = chatMessages.size + if (isChatLoading) 1 else 0
        if (totalCount > 0) chatListState.animateScrollToItem(totalCount - 1)
    }

    // ROOT LAYOUT: imePadding() is the SOLE keyboard handler for this screen.
    // statusBarsPadding() in the top bar handles the system status bar.
    // MainDashboard Scaffold's innerPadding handles the bottom navigation bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .imePadding()
    ) {
        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151522))
                .padding(top = statusBarTopDp + 12.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.prioritize.R.drawable.logo_prioritize),
                    contentDescription = "Prioritize Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Brain",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeModelSpec.name}  •  ${viewModel.activeBackend}",
                        color = Color(0xFF03DAC6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = when (viewModel.activeBackend) {
                        "NPU" -> Color(0xFF03DAC6).copy(alpha = 0.2f)
                        "GPU" -> Color(0xFFBB86FC).copy(alpha = 0.2f)
                        else  -> Color.Gray.copy(alpha = 0.2f)
                    },
                    contentColor = when (viewModel.activeBackend) {
                        "NPU" -> Color(0xFF03DAC6)
                        "GPU" -> Color(0xFFBB86FC)
                        else  -> Color.LightGray
                    },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .clickable { showSettingsSheet = true }
                        .widthIn(max = 64.dp)   // cap badge width so it never crushes the title
                ) {
                    Text(
                        text = viewModel.activeBackend,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Brain Settings",
                        tint = Color(0xFF03DAC6), modifier = Modifier.size(20.dp))
                }
            }
        }

        HorizontalDivider(color = Color(0xFF28283C), thickness = 1.dp)

        AnimatedVisibility(visible = contextFillRatio > 0.01f) {
            Surface(
                color = Color(0xFF151522),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Second Brain Context Usage",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val pct = (contextFillRatio * 100).toInt()
                            Text(
                                text = "$pct%",
                                color = when {
                                    contextFillRatio > 0.90f -> Color(0xFFCF6679)
                                    contextFillRatio > 0.75f -> Color(0xFFFFB74D)
                                    else -> Color(0xFF03DAC6)
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { contextFillRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = when {
                                contextFillRatio > 0.90f -> Color(0xFFCF6679)
                                contextFillRatio > 0.75f -> Color(0xFFFFB74D)
                                else -> Color(0xFF03DAC6)
                            },
                            trackColor = Color(0xFF28283C)
                        )
                    }
                    if (contextFillRatio > 0.75f) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.compactConversation() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (contextFillRatio > 0.90f) Color(0xFFCF6679) else Color(0xFFFFB74D),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = "Compact",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // MESSAGES AREA (weight=1f compresses cleanly when keyboard appears)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (!isModelAvailable) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Active LLM Unavailable", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap the gear icon above to download and activate a local edge model.",
                        color = Color.LightGray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showSettingsSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black)) {
                        Text("Open Settings", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧠", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Brainstorming Space",
                        color = Color(0xFF9999BB),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ask the AI to plan tasks, structure ideas,\nor reason through your schedule.",
                        color = Color(0xFF555577),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(state = chatListState, modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(chatMessages, key = { "${it.sender}_${it.timestamp}" }) { message ->
                        ChatMessageBubble(message = message, viewModel = viewModel)
                    }
                    if (isChatLoading) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(8.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color(0xFFBB86FC), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF28283C), thickness = 1.dp)

        // ATTACHMENT PREVIEW ROW
        if (selectedImageUri != null || selectedAudioUri != null || attachedDocUri != null || isRecordingSpeech) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF1E1E2C), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedImageUri != null) {
                    val bitmap = remember(selectedImageUri) {
                        try {
                            context.contentResolver.openInputStream(selectedImageUri!!)?.use {
                                android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                            }
                        } catch(e: Exception) { null }
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = "Preview Image",
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Photo Attached", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    IconButton(onClick = { selectedImageUri = null }) {
                        Text("×", color = Color.Red, fontSize = 20.sp)
                    }
                }
                if (selectedAudioUri != null) {
                    Text("🎵 Audio file attached", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    IconButton(onClick = { selectedAudioUri = null }) {
                        Text("×", color = Color.Red, fontSize = 20.sp)
                    }
                }
                if (attachedDocUri != null) {
                    val isPdf = attachedDocName?.endsWith(".pdf", ignoreCase = true) == true
                    Text(
                        text = if (isPdf) "📄 $attachedDocName" else "📝 $attachedDocName",
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { viewModel.setAttachedDoc(null, null) }) {
                        Text("×", color = Color.Red, fontSize = 20.sp)
                    }
                }
                if (isRecordingSpeech) {
                    CircularProgressIndicator(color = Color(0xFF03DAC6), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (partialTranscriptText.isNotBlank()) partialTranscriptText else "Hearing...",
                        color = Color(0xFF03DAC6),
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp
                    )
                    Button(
                        onClick = {
                            isRecordingSpeech = false
                            com.example.prioritize.audio.SpeechTranscriber.stopListening()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.White)
                    ) {
                        Text("Stop")
                    }
                }
            }
        }

        // INPUT ROW
        if (isModelAvailable) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                
                IconButton(onClick = {
                    imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.size(36.dp)) {
                    Text("📷", fontSize = 18.sp, color = Color.White)
                }

                IconButton(onClick = {
                    audioPicker.launch("audio/*")
                }, modifier = Modifier.size(36.dp)) {
                    Text("🎵", fontSize = 18.sp, color = Color.White)
                }

                IconButton(onClick = {
                    docPicker.launch(arrayOf("application/pdf", "text/plain"))
                }, modifier = Modifier.size(36.dp)) {
                    Text("📄", fontSize = 18.sp, color = Color.White)
                }

                IconButton(
                    onClick = {
                        if (isRecordingSpeech) {
                            isRecordingSpeech = false
                            partialTranscriptText = ""
                            com.example.prioritize.audio.SpeechTranscriber.stopListening()
                        } else {
                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.RECORD_AUDIO
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            
                            if (hasPermission) {
                                showLanguageSelectorFor = LanguageSelectorType.RECORDING
                            } else {
                                Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(if (isRecordingSpeech) "🛑" else "🎙️", fontSize = 18.sp, color = Color.White)
                }

                Spacer(Modifier.width(4.dp))

                OutlinedTextField(value = chatInput, onValueChange = { chatInput = it },
                    placeholder = { Text("Brainstorm task ideas...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF03DAC6)),
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                        if (selectedAudioUri != null) {
                            showLanguageSelectorFor = LanguageSelectorType.UPLOAD
                        } else if (chatInput.isNotBlank() || selectedImageUri != null || attachedDocUri != null) {
                            viewModel.sendMessageToBrain(chatInput, selectedImageUri, null, attachedDocUri)
                            chatInput = ""
                            selectedImageUri = null
                            viewModel.setAttachedDoc(null, null)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                    modifier = Modifier.height(56.dp)) { Text("Send") }
            }
        }
    }

    if (showReviewDialog) {
        TranscriptionReviewDialog(
            rawText = rawTranscriptText,
            refinedText = refinedTranscriptText,
            viewModel = viewModel,
            onDismiss = { showReviewDialog = false },
            onConfirm = { editedText ->
                showReviewDialog = false
                viewModel.sendMessageToBrain(editedText, selectedImageUri, null, attachedDocUri)
                selectedImageUri = null
                selectedAudioUri = null
                viewModel.setAttachedDoc(null, null)
            }
        )
    }

    if (showLanguageSelectorFor != null) {
        AlertDialog(
            onDismissRequest = { showLanguageSelectorFor = null },
            title = { Text("Select Spoken Language", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Which language did you speak in this recording?", color = Color.Gray, fontSize = 13.sp)
                    spokenLanguages.forEach { language ->
                        Button(
                            onClick = {
                                val targetType = showLanguageSelectorFor
                                showLanguageSelectorFor = null
                                if (targetType == LanguageSelectorType.RECORDING) {
                                    isRecordingSpeech = true
                                    partialTranscriptText = ""
                                    val langCode = when (language.trim().lowercase()) {
                                        "afrikaans" -> "af-ZA"
                                        "german", "deutsch" -> "de-DE"
                                        else -> "en-US"
                                    }
                                    com.example.prioritize.audio.SpeechTranscriber.startListening(
                                        context = context,
                                        languageCode = langCode,
                                        continuous = true,
                                        onPartialResult = { text ->
                                            partialTranscriptText = text
                                        },
                                        onFinalResult = { text ->
                                            isRecordingSpeech = false
                                            partialTranscriptText = ""
                                            if (text.isNotBlank()) {
                                                rawTranscriptText = text
                                                viewModel.refineTranscription(text, language) { refined ->
                                                    refinedTranscriptText = refined
                                                    showReviewDialog = true
                                                }
                                            }
                                        },
                                        onError = { err ->
                                            isRecordingSpeech = false
                                            partialTranscriptText = ""
                                            Toast.makeText(context, "Speech Error: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                } else if (targetType == LanguageSelectorType.UPLOAD) {
                                    viewModel.transcribeAudioFile(selectedAudioUri!!, language) { raw, refined ->
                                        rawTranscriptText = raw
                                        refinedTranscriptText = refined
                                        showReviewDialog = true
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C), contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(language)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageSelectorFor = null }) {
                    Text("Cancel", color = Color(0xFFCF6679))
                }
            },
            containerColor = Color(0xFF151522),
            textContentColor = Color.White
        )
    }

    // SETTINGS BOTTOM SHEET
    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }, sheetState = sheetState,
            containerColor = Color(0xFF151522), contentColor = Color.White) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {

                Text("Brain Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                Text("Active: ${activeModelSpec.name}  \u2022  Backend: ${viewModel.activeBackend}",
                    color = Color(0xFF03DAC6), fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
                HorizontalDivider(color = Color(0xFF28283C), modifier = Modifier.padding(bottom = 16.dp))

                if (viewModel.activeBackend == "CPU" || viewModel.isEngineNull) {
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (viewModel.isEngineNull) {
                                Text("\u26a0\ufe0f Engine failed to start", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("All backends (NPU, GPU, CPU) failed. A previous crash may have set stale guard flags. Reset them to retry.",
                                    color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            } else {
                                Text("\u26a0\ufe0f Running on CPU", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("NPU/GPU unavailable. Known issue with LiteRT-LM 0.13.1 on Tensor G5.",
                                    color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                            TextButton(onClick = { showDiagDialog = true }) { Text("View Diagnostics & Reset", color = Color(0xFF03DAC6)) }
                        }
                    }
                }

                Text("Executive Profile", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showEditPromptDialog = true }.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val promptPreview = userProfile?.systemPrompt
                        Text(
                            text = if (promptPreview.isNullOrEmpty()) "No profile set."
                                   else promptPreview.take(80) + if (promptPreview.length > 80) "\u2026" else "",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = Color(0xFF03DAC6), modifier = Modifier.size(16.dp))
                    }
                }

                Button(onClick = { viewModel.startOnboardingInterview(); showSettingsSheet = false },
                    enabled = isModelAvailable,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C), contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(if (onboardingPhase > 0) "Restart Interview" else "Start Onboarding Interview")
                }

                Text("Voice & Accents", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = userAccent,
                            onValueChange = { viewModel.updateUserAccent(it) },
                            label = { Text("Your Accent / Pronunciation context", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF03DAC6)
                            )
                        )
                        
                        var languagesText by remember(userProfile) {
                            val defaultLangs = "English, Afrikaans, German"
                            val current = userProfile?.let {
                                try {
                                    val json = org.json.JSONObject(it.metadataJson)
                                    if (json.has("spoken_languages")) {
                                        val arr = json.getJSONArray("spoken_languages")
                                        val list = mutableListOf<String>()
                                        for (i in 0 until arr.length()) {
                                            list.add(arr.getString(i))
                                        }
                                        list.joinToString(", ")
                                    } else defaultLangs
                                } catch (e: Exception) { defaultLangs }
                            } ?: defaultLangs
                            mutableStateOf(current)
                        }

                        OutlinedTextField(
                            value = languagesText,
                            onValueChange = { newVal ->
                                languagesText = newVal
                                val list = newVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                viewModel.updateSpokenLanguages(list)
                            },
                            label = { Text("Spoken Languages (comma-separated)", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF03DAC6)
                            )
                        )
                    }
                }

                Text("Cloud API Settings (Optional)", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        var apiKeyText by remember(userProfile) {
                            val current = userProfile?.let {
                                try {
                                    val json = org.json.JSONObject(it.metadataJson)
                                    if (json.has("gemini_api_key")) {
                                        json.getString("gemini_api_key")
                                    } else ""
                                } catch (e: Exception) { "" }
                            } ?: ""
                            mutableStateOf(current)
                        }

                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { newVal ->
                                apiKeyText = newVal
                                viewModel.updateGeminiApiKey(newVal)
                            },
                            label = { Text("Gemini API Key (for Image & Audio)", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF03DAC6)
                            )
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF28283C), modifier = Modifier.padding(bottom = 16.dp))

                // ── GitHub Feedback ────────────────────────────────────────
                Text("Feedback & Feature Requests", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (isGitHubLoggedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("🟢 Connected as", color = Color(0xFF03DAC6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("@${viewModel.gitHubUsername}", color = Color.White, fontSize = 13.sp)
                                }
                                TextButton(onClick = { viewModel.gitHubLogout() }) {
                                    Text("Disconnect", color = Color(0xFFCF6679), fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showFeedbackSheet = true; showSettingsSheet = false },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C), contentColor = Color.White)
                            ) {
                                Text("💡 Submit Feedback or Feature Request")
                            }
                        } else {
                            Text(
                                "Connect your GitHub account to submit feature requests and bug reports directly to the Prioritize repository.",
                                color = Color.Gray, fontSize = 11.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.startGitHubLogin() },
                                enabled = !isGitHubLoggingIn,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636), contentColor = Color.White)
                            ) {
                                if (isGitHubLoggingIn) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Waiting for authorization...")
                                } else {
                                    Text("🔗 Connect GitHub Account")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF28283C), modifier = Modifier.padding(bottom = 16.dp))
                Text("Available Edge LLM Models", color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))

                var hfTokenText by remember { mutableStateOf("") }
                LaunchedEffect(userProfile) {
                    userProfile?.let {
                        try {
                            val json = org.json.JSONObject(it.metadataJson)
                            if (json.has("hf_token")) hfTokenText = json.getString("hf_token")
                        } catch (e: Exception) {}
                    }
                }
                LaunchedEffect(hfTokenText) {
                    kotlinx.coroutines.delay(600)
                    viewModel.updateHfToken(hfTokenText)
                }

                OutlinedTextField(value = hfTokenText, onValueChange = { hfTokenText = it },
                    label = { Text("Hugging Face Access Token (Gated)") },
                    placeholder = { Text("hf_...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF03DAC6), unfocusedLabelColor = Color.Gray),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))

                Button(onClick = { viewModel.runDreamingConsolidationNow() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(8.dp)) {
                    Text("Consolidate Memories (Dream Now)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                AVAILABLE_MODELS.forEach { model ->
                    val isDownloaded = downloadedModels.contains(model.id)
                    val isActive = activeModelSpec.id == model.id
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(model.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Size: ${model.sizeLabel} \u2022 Rec. RAM: ${model.recommendedRamGb.toInt()}GB",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        if (viewModel.totalRamGb < model.recommendedRamGb) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "\u26a0\ufe0f Low RAM",
                                                color = Color(0xFFCF6679),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                        if (model.isNpuOnly && !DeviceHardware.isTensorG5()) {
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "\u26a0\ufe0f NPU Required (Non-G5)",
                                                color = Color(0xFFFFB74D),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                when {
                                    isActive && isModelAvailable -> Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = Color(0xFF03DAC6).copy(alpha = 0.1f),
                                            contentColor = Color(0xFF03DAC6),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                        Button(onClick = { viewModel.deleteModelFile(model) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679), contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)) { Text("Delete", fontSize = 11.sp) }
                                    }
                                    isImporting && targetImportFilename == model.filename ->
                                        Text("Importing $importProgress%", color = Color(0xFFBB86FC), fontSize = 11.sp)
                                    isDownloading && activeModelSpec.id == model.id ->
                                        Text("Downloading $downloadProgress%", color = Color(0xFF03DAC6), fontSize = 11.sp)
                                    isDownloaded -> Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = {
                                                if (viewModel.totalRamGb < model.recommendedRamGb) modelToConfirmActive = model
                                                else viewModel.selectActiveModel(model)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF28283C)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)) { Text("Set Active", fontSize = 11.sp) }
                                        Button(onClick = { viewModel.deleteModelFile(model) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679), contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)) { Text("Delete", fontSize = 11.sp) }
                                    }
                                    else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = {
                                                context.startActivity(android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(model.downloadUrl)))
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)) { Text("Get File", fontSize = 11.sp) }
                                        Button(onClick = { targetImportFilename = model.filename; modelImportLauncher.launch(arrayOf("*/*")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)) { Text("Import", fontSize = 11.sp) }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(model.description, color = Color.LightGray, fontSize = 11.sp)
                            if (isDownloading && activeModelSpec.id == model.id) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { downloadProgress / 100f },
                                    color = Color(0xFF03DAC6), trackColor = Color(0xFF28283C),
                                    modifier = Modifier.fillMaxWidth().height(4.dp))
                            }
                            if (isImporting && targetImportFilename == model.filename) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(progress = { importProgress / 100f },
                                    color = Color(0xFFBB86FC), trackColor = Color(0xFF28283C),
                                    modifier = Modifier.fillMaxWidth().height(4.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF28283C), modifier = Modifier.padding(vertical = 16.dp))
                Text("Data Management", color = Color.LightGray, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportLauncher.launch("prioritize_backup.json") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF03DAC6)),
                        modifier = Modifier.weight(1f)) { Text("Export") }
                    TextButton(onClick = { agentExportLauncher.launch("prioritize_agent_handbook.zip") },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF03DAC6)),
                        modifier = Modifier.weight(1f)) { Text("Agent ZIP") }
                    TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBB86FC)),
                        modifier = Modifier.weight(1f)) { Text("Import") }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // DIALOGS
    if (showFeedbackSheet) {
        FeedbackDialog(
            viewModel = viewModel,
            onDismiss = { showFeedbackSheet = false }
        )
    }

    if (showDiagDialog) {
        AlertDialog(onDismissRequest = { showDiagDialog = false },
            title = { Text("Acceleration Diagnostics", color = Color.White) },
            text = {
                Column {
                    Text("NPU Error:", fontWeight = FontWeight.Bold, color = Color(0xFFCF6679), fontSize = 12.sp)
                    Text(viewModel.lastNpuError ?: "None logged.", color = Color.LightGray, fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 8.dp))
                    Text("GPU Error:", fontWeight = FontWeight.Bold, color = Color(0xFFCF6679), fontSize = 12.sp)
                    Text(viewModel.lastGpuError ?: "None logged.", color = Color.LightGray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagDialog = false }) {
                    Text("Close", color = Color(0xFF03DAC6))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.resetEngineBackendCrashFlags()
                    Toast.makeText(context, "Crash flags cleared. Re-select your active model to re-try acceleration.", Toast.LENGTH_LONG).show()
                    showDiagDialog = false
                }) {
                    Text("Reset Crash Flags", color = Color(0xFFCF6679))
                }
            },
            containerColor = Color(0xFF1E1E2C))
    }

    if (showEditPromptDialog) {
        var editedPrompt by remember { mutableStateOf(userProfile?.systemPrompt ?: "") }
        Dialog(onDismissRequest = { showEditPromptDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151522))) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Edit Executive Profile Prompt", color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                    OutlinedTextField(value = editedPrompt, onValueChange = { editedPrompt = it },
                        label = { Text("System Prompt") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray),
                        modifier = Modifier.fillMaxWidth().height(260.dp))
                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEditPromptDialog = false },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { viewModel.updateSystemPrompt(editedPrompt); showEditPromptDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black)) { Text("Save") }
                    }
                }
            }
        }
    }

    modelToConfirmActive?.let { model ->
        AlertDialog(onDismissRequest = { modelToConfirmActive = null },
            title = { Text("Low RAM Warning", color = Color.White) },
            text = {
                Text("This model recommends ${model.recommendedRamGb.toInt()}GB RAM but your device has ${"%.1f".format(viewModel.totalRamGb)}GB. Proceed?",
                    color = Color.LightGray)
            },
            confirmButton = { TextButton(onClick = { viewModel.selectActiveModel(model); modelToConfirmActive = null }) {
                Text("Proceed", color = Color(0xFF03DAC6)) } },
            dismissButton = { TextButton(onClick = { modelToConfirmActive = null }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color(0xFF1E1E2C))
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) Color(0xFF28283C) else Color(0xFF1E1E2C)
    val textColor = Color.White

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 0.dp,
                        bottomEnd = if (isUser) 0.dp else 12.dp
                    )
                )
                .background(bubbleColor)
                .padding(12.dp)
        ) {
            Column {
                if (message.imagePath != null) {
                    val bitmap = remember(message.imagePath) {
                        try {
                            android.graphics.BitmapFactory.decodeFile(message.imagePath)?.asImageBitmap()
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = "Chat Image Attachment",
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .widthIn(max = 240.dp)
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
                if (message.text.isNotEmpty()) {
                    Text(text = message.text, color = textColor, fontSize = 13.sp)
                } else if (message.imagePath != null) {
                    Text(
                        text = "Photo Attachment",
                        color = textColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                }
            }
        }

        if (message.audioPath != null) {
            AudioPlayerWidget(audioPath = message.audioPath)
        }

        if (message.documentPath != null) {
            val docName = message.documentPath.substringAfterLast('_').substringAfterLast('/')
            val isPdf = docName.endsWith(".pdf", ignoreCase = true)
            Text(
                text = if (isPdf) "📄 Attached PDF: $docName" else "📝 Attached Text: $docName",
                color = Color.LightGray.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }

        // Action chips
        if (message.actionTask != null) {
            var isAdded by remember { mutableStateOf(false) }
            AnimatedVisibility(visible = !isAdded) {
                Button(
                    onClick = {
                        viewModel.saveTask(message.actionTask)
                        isAdded = true
                        Toast.makeText(context, "Added task to Scratch Pad!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "➕ Add task: \"${message.actionTask.title}\" to Scratch Pad", fontSize = 12.sp)
                }
            }
        }
        
        if (message.actionRepeatingTask != null) {
            var isAdded by remember { mutableStateOf(false) }
            AnimatedVisibility(visible = !isAdded) {
                Button(
                    onClick = {
                        viewModel.saveRepeatingTask(message.actionRepeatingTask)
                        isAdded = true
                        Toast.makeText(context, "Saved repeating task!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "➕ Save repeating task: \"${message.actionRepeatingTask.title}\"", fontSize = 12.sp)
                }
            }
        }
        
        if (message.actionSpecialDate != null) {
            var isAdded by remember { mutableStateOf(false) }
            AnimatedVisibility(visible = !isAdded) {
                Button(
                    onClick = {
                        viewModel.saveSpecialDate(message.actionSpecialDate)
                        isAdded = true
                        Toast.makeText(context, "Saved special date!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(text = "➕ Save date: \"${message.actionSpecialDate.name}\"", fontSize = 12.sp)
                }
            }
        }
    }
}

// ── Feedback Bottom Sheet ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val feedbackSubmitState by viewModel.feedbackSubmitState.collectAsState()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("enhancement") }

    val labels = listOf(
        "enhancement" to "💡 Feature Request",
        "bug" to "🐛 Bug Report",
        "question" to "❓ Question",
        "improvement" to "⚡ Improvement"
    )

    // Auto-dismiss and show link on success
    LaunchedEffect(feedbackSubmitState) {
        if (feedbackSubmitState is TaskViewModel.FeedbackSubmitState.Success) {
            val state = feedbackSubmitState as TaskViewModel.FeedbackSubmitState.Success
            Toast.makeText(context, "Issue #${state.number} submitted! ✅", Toast.LENGTH_LONG).show()
            viewModel.resetFeedbackState()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.resetFeedbackState(); onDismiss() },
        containerColor = Color(0xFF151522),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF444466)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Submit Feedback",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                "Submits a GitHub issue to KoueKoffie-MW/prioritize-android as @${viewModel.gitHubUsername}",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Label selector
            Text("Type", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                labels.forEach { (key, display) ->
                    val isSelected = selectedLabel == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedLabel = key },
                        label = { Text(display, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3700B3),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E1E2C),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("Short summary of the request", color = Color.DarkGray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFBB86FC), unfocusedBorderColor = Color(0xFF444466),
                    focusedLabelColor = Color(0xFFBB86FC), unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            // Body
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Description") },
                placeholder = { Text("Describe what you'd like or what went wrong...", color = Color.DarkGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFBB86FC), unfocusedBorderColor = Color(0xFF444466),
                    focusedLabelColor = Color(0xFFBB86FC), unfocusedLabelColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth().height(140.dp).padding(bottom = 16.dp)
            )

            // Error message
            if (feedbackSubmitState is TaskViewModel.FeedbackSubmitState.Error) {
                Text(
                    (feedbackSubmitState as TaskViewModel.FeedbackSubmitState.Error).message,
                    color = Color(0xFFCF6679),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Submit button
            Button(
                onClick = {
                    if (title.isNotBlank() && body.isNotBlank()) {
                        viewModel.submitFeedback(title.trim(), body.trim(), listOf(selectedLabel))
                    } else {
                        Toast.makeText(context, "Please fill in both title and description.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = feedbackSubmitState !is TaskViewModel.FeedbackSubmitState.Submitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black)
            ) {
                if (feedbackSubmitState is TaskViewModel.FeedbackSubmitState.Submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Submitting...")
                } else {
                    Text("Submit to GitHub", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AudioPlayerWidget(audioPath: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val context = LocalContext.current

    DisposableEffect(audioPath) {
        onDispose {
            player?.stop()
            player?.release()
            player = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(Color(0xFF28283C), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player?.pause()
                    isPlaying = false
                } else {
                    val mp = player ?: android.media.MediaPlayer().apply {
                        try {
                            setDataSource(audioPath)
                            prepare()
                            setOnCompletionListener {
                                isPlaying = false
                            }
                        } catch (e: Exception) {
                            Log.e("AudioPlayerWidget", "Error preparing media player for path: $audioPath", e)
                            Toast.makeText(context, "Error playing audio file", Toast.LENGTH_SHORT).show()
                        }
                    }.also { player = it }
                    
                    try {
                        mp.start()
                        isPlaying = true
                    } catch (e: Exception) {
                        Log.e("AudioPlayerWidget", "Error starting media player", e)
                    }
                }
            },
            modifier = Modifier.size(28.dp)
        ) {
            Text(if (isPlaying) "⏸️" else "▶️", fontSize = 14.sp, color = Color.White)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Voice Note Attachment",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
