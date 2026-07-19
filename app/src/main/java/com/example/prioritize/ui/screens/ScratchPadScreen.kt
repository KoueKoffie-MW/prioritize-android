package com.example.prioritize.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.example.prioritize.ai.Gemma4Parser
import com.example.prioritize.ui.components.ConfirmTaskDialog
import com.example.prioritize.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScratchPadScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val scratchPadTasks by viewModel.scratchPadTasks.collectAsState()
    val isAILoading by viewModel.isAILoading.collectAsState()
    val aiSuggestion by viewModel.aiSuggestion.collectAsState()

    val context = LocalContext.current
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()

    val aiErrorMsg by viewModel.aiErrorMsg.collectAsState()
    LaunchedEffect(aiErrorMsg) {
        aiErrorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearAiError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(16.dp)
    ) {
        Text(
            text = "Scratch Pad",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Capture thoughts fast. Tap to AI-parse, long-press to quick-add.",
            color = Color(0xFF8888AA),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Dump Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Dump a task (e.g. Prepare slides for Friday noon)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFBB86FC),
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.dumpToScratchPad(textInput)
                        textInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC), contentColor = Color.Black),
                modifier = Modifier.height(56.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Dump Task")
            }
        }

        HorizontalDivider(color = Color(0xFF323246), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "Inbox Items (${scratchPadTasks.size})",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isAILoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFBB86FC))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("AI is parsing your task...", color = Color(0xFF9999BB), fontSize = 13.sp)
                }
            }
        } else if (scratchPadTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📥", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Inbox is empty",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Type a thought above and tap →",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scratchPadTasks) { task ->
                    val isAI = isModelAvailable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A2E))
                            .combinedClickable(
                                onClick = {
                                    if (isModelAvailable) {
                                        viewModel.processScratchPadItem(task)
                                    } else {
                                        Toast.makeText(context, "AI unavailable — adding manually.", Toast.LENGTH_SHORT).show()
                                        viewModel.saveTask(
                                            task.copy(
                                                isScratchPadItem = false,
                                                importance = 3,
                                                urgency = 3
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
                                    Toast.makeText(context, "Quick Added: \"${task.title}\"", Toast.LENGTH_SHORT).show()
                                    viewModel.saveTask(
                                        task.copy(
                                            isScratchPadItem = false,
                                            importance = 5,
                                            urgency = 5
                                        )
                                    )
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        // Action chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isAI) Color(0xFF1E3A3A) else Color(0xFF2A2A3A)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isAI) "✶ AI Parse" else "+ Add",
                                color = if (isAI) Color(0xFF03DAC6) else Color(0xFF9999BB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        // Explicit delete icon — dismiss without processing
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF3A1A1E))
                                .clickable { viewModel.deleteTask(task) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "🗑",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Confirm Suggestion Dialog
        aiSuggestion?.let { suggestion ->
            ConfirmTaskDialog(
                suggestion = suggestion,
                onConfirm = { finalTask ->
                    viewModel.saveTask(finalTask)
                    viewModel.clearSuggestion()
                },
                onConfirmRepeating = { finalRepTask ->
                    viewModel.saveRepeatingTask(finalRepTask)
                    viewModel.clearSuggestion()
                },
                onDismiss = {
                    viewModel.clearSuggestion()
                }
            )
        }
    }
}
