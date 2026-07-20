package com.example.prioritize.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.ui.viewmodel.TaskViewModel

@Composable
fun TranscriptionReviewDialog(
    rawText: String,
    refinedText: String,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textState by remember { mutableStateOf(refinedText) }
    var showAddSpeaker by remember { mutableStateOf(false) }
    var newSpeakerName by remember { mutableStateOf("") }
    var newSpeakerAccent by remember { mutableStateOf("South African Afrikaans") }
    
    val knownSpeakers by viewModel.knownSpeakers.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 500.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Verify Transcription",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Transcript Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Known speakers section
                Text(
                    text = "Speakers Context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                knownSpeakers.forEach { speaker ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${speaker.name} (${speaker.accent})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (showAddSpeaker) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = newSpeakerName,
                            onValueChange = { newSpeakerName = it },
                            label = { Text("Speaker Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newSpeakerAccent,
                            onValueChange = { newSpeakerAccent = it },
                            label = { Text("Accent / Language") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newSpeakerName.isNotBlank()) {
                                    viewModel.addKnownSpeaker(newSpeakerName, newSpeakerAccent)
                                    newSpeakerName = ""
                                    showAddSpeaker = false
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Save Speaker")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showAddSpeaker = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("+ Add/Learn New Speaker")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm(textState)
                    }) {
                        Text("Send to Brain")
                    }
                }
            }
        }
    }
}
