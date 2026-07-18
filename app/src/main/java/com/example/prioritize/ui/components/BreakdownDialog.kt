package com.example.prioritize.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.ai.ParsedSubTaskSuggestion
import com.example.prioritize.data.SubTask
import com.example.prioritize.data.Task
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownDialog(
    task: Task,
    isModelAvailable: Boolean,
    onGenerateLocal: suspend () -> List<ParsedSubTaskSuggestion>,
    onGenerateCloudPrompt: suspend () -> String,
    onParsePastedText: suspend (String) -> List<ParsedSubTaskSuggestion>,
    onSaveSubTasks: (List<SubTask>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var cloudPrompt by remember { mutableStateOf("") }
    var pastedText by remember { mutableStateOf("") }
    var subTaskSuggestions by remember { mutableStateOf<List<ParsedSubTaskSuggestion>>(emptyList()) }
    var statusText by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val currentOnGenerateLocal by rememberUpdatedState(onGenerateLocal)
    val currentOnGenerateCloudPrompt by rememberUpdatedState(onGenerateCloudPrompt)
    val currentOnParsePastedText by rememberUpdatedState(onParsePastedText)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151522))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Break Down Task",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Task: ${task.title}",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    CircularProgressIndicator(color = Color(0xFFBB86FC))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusText, color = Color.LightGray, fontSize = 13.sp)
                } else {
                    if (subTaskSuggestions.isEmpty()) {
                        // Options view: Local vs Cloud Fallback
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Method 1: Local On-Device AI
                            Button(
                                onClick = {
                                    isLoading = true
                                    statusText = "Running on-device Gemma 4..."
                                    coroutineScope.launch {
                                        try {
                                            val results = currentOnGenerateLocal()
                                            if (results.isNotEmpty()) {
                                                subTaskSuggestions = results
                                            } else {
                                                Toast.makeText(context, "Local generation failed or no model found.", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                enabled = isModelAvailable,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Generate Steps Locally (Gemma 4)")
                            }
                            if (!isModelAvailable) {
                                Text(
                                    // Model filename must match the active AVAILABLE_MODELS entry in ModelRegistry
                                    "Note: No Gemma model file found. Download a model from the Brain tab, or import a .litertlm file from your device storage to enable local generation.",
                                    color = Color(0xFFCF6679),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            HorizontalDivider(color = Color(0xFF323246), thickness = 1.dp)

                            // Method 2: Cloud copy/paste helper
                            Text(
                                "Method 2: Cloud Fallback Prompt",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Generate a perfect prompt to copy into a cloud AI (e.g. Gemini Pro), then paste the response below.",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )

                            Button(
                                onClick = {
                                    isLoading = true
                                    statusText = "Generating prompt..."
                                    coroutineScope.launch {
                                        cloudPrompt = currentOnGenerateCloudPrompt()
                                        isLoading = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (cloudPrompt.isEmpty()) "Generate Cloud Prompt" else "Regenerate Cloud Prompt")
                            }

                            if (cloudPrompt.isNotEmpty()) {
                                OutlinedTextField(
                                    value = cloudPrompt,
                                    onValueChange = {},
                                    readOnly = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.LightGray,
                                        focusedBorderColor = Color(0xFFBB86FC)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                )
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Prioritize Prompt", cloudPrompt)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Copy Prompt to Clipboard")
                                }

                                HorizontalDivider(color = Color(0xFF323246), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    "Paste Cloud Response Here:",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                OutlinedTextField(
                                    value = pastedText,
                                    onValueChange = { pastedText = it },
                                    placeholder = { Text("Paste response here...") },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                )
                                Button(
                                    onClick = {
                                        if (pastedText.isNotBlank()) {
                                            isLoading = true
                                            statusText = "Parsing cloud response with Gemma 4..."
                                            coroutineScope.launch {
                                                val results = currentOnParsePastedText(pastedText)
                                                if (results.isNotEmpty()) {
                                                    subTaskSuggestions = results
                                                } else {
                                                    Toast.makeText(context, "Failed to parse text. Please ensure it has clear steps and time estimates.", Toast.LENGTH_LONG).show()
                                                }
                                                isLoading = false
                                            }
                                        }
                                    },
                                    enabled = pastedText.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Parse & Extract Steps")
                                }
                            }
                        }
                    } else {
                        // Subtask suggestions preview list
                        Text(
                            text = "Extracted Sub-tasks",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        subTaskSuggestions.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E1E2C))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. ${item.title}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${item.estimatedMinutes}m",
                                    color = Color(0xFF03DAC6),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { subTaskSuggestions = emptyList() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Back")
                            }
                            Button(
                                onClick = {
                                    val finalSubTasks = subTaskSuggestions.map {
                                        SubTask(
                                            taskId = task.id,
                                            title = it.title,
                                            estimatedMinutes = it.estimatedMinutes
                                        )
                                    }
                                    onSaveSubTasks(finalSubTasks)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black)
                            ) {
                                Text("Save Steps")
                            }
                        }
                    }
                }

                if (!isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.Gray)
                    }
                }
            }
        }
    }
}
