package com.example.prioritize.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.data.SubTask
import com.example.prioritize.data.Task
import com.example.prioritize.data.RepeatingTask
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailDialog(
    task: Task,
    initialSubTasks: List<SubTask>,
    parentRepeatingTask: RepeatingTask? = null,
    onSave: (Task, List<SubTask>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onEditRepeatingTemplate: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var importance by remember { mutableFloatStateOf(task.importance.toFloat()) }
    var urgency by remember { mutableFloatStateOf(task.urgency.toFloat()) }
    var estimatedMinutes by remember { mutableFloatStateOf(task.estimatedMinutes.toFloat()) }
    var deadline by remember { mutableStateOf(task.deadline) }

    // Subtask states
    val subTasks = remember { mutableStateListOf<SubTask>().apply { addAll(initialSubTasks) } }
    var newSubTaskText by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = deadline ?: System.currentTimeMillis()
    )

    // Calculate dynamic priority score real-time
    val importanceWeight = 3.0
    val urgencyWeight = 2.0
    val baseScore = (importanceWeight * importance) + (urgencyWeight * urgency)
    val durationHours = estimatedMinutes / 60.0
    val now = System.currentTimeMillis()
    val timeRemainingHours = if (deadline != null) {
        val effective = if (task.isSoftDeadline) deadline!! + (task.graceDays * 24L * 60 * 60 * 1000) else deadline!!
        (effective - now).toDouble() / (1000.0 * 60.0 * 60.0)
    } else Double.MAX_VALUE
    val slackTimeHours = if (deadline != null) timeRemainingHours - durationHours else Double.MAX_VALUE
    val deadlineScore = if (deadline != null) {
        if (slackTimeHours <= 0) 30.0
        else if (slackTimeHours < 12.0) 25.0
        else if (slackTimeHours < 24.0) 18.0
        else if (slackTimeHours < 72.0) 10.0
        else 2.0
    } else 0.0
    val dynamicScore = (baseScore + deadlineScore).coerceIn(0.0, 100.0)

    val scoreCol = when {
        dynamicScore >= 45.0 -> Color(0xFFFF6B6B) // Critical
        dynamicScore >= 30.0 -> Color(0xFFFFB74D) // High
        dynamicScore >= 20.0 -> Color(0xFFBB86FC) // Medium
        else -> Color(0xFF03DAC6)                 // Normal
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151522))
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with live priority score
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Task Details",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(scoreCol)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Score: ${dynamicScore.roundToInt()}",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }

                parentRepeatingTask?.let { rt ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditRepeatingTemplate?.invoke() }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("🔁 Repeating Task Instance", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Click to edit the repeating template cadence", color = Color.Gray, fontSize = 11.sp)
                            }
                            Icon(Icons.Filled.Edit, contentDescription = "Edit Template", tint = Color(0xFF03DAC6), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF03DAC6), unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color(0xFF03DAC6), unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sliders for Importance, Urgency, Duration
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Importance: ${importance.roundToInt()}", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = importance,
                        onValueChange = {
                            importance = it
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFBB86FC), activeTrackColor = Color(0xFFBB86FC))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Urgency: ${urgency.roundToInt()}", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = urgency,
                        onValueChange = {
                            urgency = it
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF03DAC6), activeTrackColor = Color(0xFF03DAC6))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Duration: ${estimatedMinutes.roundToInt()}m", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = estimatedMinutes,
                        onValueChange = { estimatedMinutes = it },
                        valueRange = 5f..240f,
                        steps = 46, // 5m steps
                        colors = SliderDefaults.colors(thumbColor = Color.Gray, activeTrackColor = Color.Gray)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subtask checklist editor
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E2C))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "Subtasks / Steps (${subTasks.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    subTasks.forEachIndexed { index, subTask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = subTask.isCompleted,
                                    onCheckedChange = { isChecked ->
                                        subTasks[index] = subTask.copy(isCompleted = isChecked)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                                Text(
                                    text = subTask.title,
                                    color = if (subTask.isCompleted) Color.Gray else Color.White,
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = {
                                subTasks.removeAt(index)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Subtask", tint = Color(0xFFCF6679), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Add new subtask row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSubTaskText,
                            onValueChange = { newSubTaskText = it },
                            placeholder = { Text("Add checklist step...", fontSize = 12.sp, color = Color.Gray) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newSubTaskText.isNotBlank()) {
                                    subTasks.add(
                                        SubTask(
                                            taskId = task.id,
                                            title = newSubTaskText.trim(),
                                            isCompleted = false
                                        )
                                    )
                                    newSubTaskText = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF03DAC6),
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (newSubTaskText.isNotBlank()) {
                                    subTasks.add(
                                        SubTask(
                                            taskId = task.id,
                                            title = newSubTaskText.trim(),
                                            isCompleted = false
                                        )
                                    )
                                    newSubTaskText = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add subtask", tint = Color(0xFF03DAC6))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Date Picker for Deadline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E2C))
                        .clickable { showDatePicker = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = "Deadline", tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Deadline", color = Color.Gray, fontSize = 11.sp)
                            Text(
                                text = deadline?.let { dateFormatter.format(Date(it)) } ?: "No Deadline Set",
                                color = if (deadline != null) Color(0xFF03DAC6) else Color.LightGray,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (deadline != null) {
                        TextButton(onClick = { deadline = null }) {
                            Text("Clear", color = Color(0xFFCF6679), fontSize = 12.sp)
                        }
                    }
                }

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                                deadline = datePickerState.selectedDateMillis
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) { Text("Confirm") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom actions: Delete, Cancel, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onDelete()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1A1E), contentColor = Color(0xFFCF6679)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Delete", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(
                                    task.copy(
                                        title = title.trim(),
                                        description = description.trim(),
                                        importance = importance.roundToInt(),
                                        urgency = urgency.roundToInt(),
                                        estimatedMinutes = estimatedMinutes.roundToInt(),
                                        deadline = deadline
                                    ),
                                    subTasks.toList()
                                )
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6), contentColor = Color.Black),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
