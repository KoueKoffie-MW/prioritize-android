package com.example.prioritize.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.ai.ParsedTaskSuggestion
import com.example.prioritize.data.Task
import com.example.prioritize.data.RepeatingTask
import com.example.prioritize.data.RecurrenceType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmTaskDialog(
    suggestion: ParsedTaskSuggestion,
    onConfirm: (Task) -> Unit,
    onConfirmRepeating: (RepeatingTask) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(suggestion.title) }
    var description by remember { mutableStateOf(suggestion.description) }
    var importance by remember { mutableStateOf(suggestion.importance) }
    var urgency by remember { mutableStateOf(suggestion.urgency) }
    var estimatedMinutes by remember { mutableStateOf(suggestion.estimatedMinutes) }
    var deadline by remember { mutableStateOf(suggestion.deadline) }
    var isSoftDeadline by remember { mutableStateOf(true) }
    var graceDays by remember { mutableStateOf(2) }
    
    var isRepeating by remember { mutableStateOf(suggestion.recurrenceType != null) }
    var selectedRecurrence by remember { 
        mutableStateOf(
            RecurrenceType.entries.find { it.name == suggestion.recurrenceType } ?: RecurrenceType.WEEKLY
        )
    }
    var recurrenceExpanded by remember { mutableStateOf(false) }

    // Material3 date / time picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Temporarily holds selected date components while waiting for the time picker
    val pendingCal = remember { Calendar.getInstance() }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = deadline ?: System.currentTimeMillis()
    )
    val timePickerState = rememberTimePickerState(
        initialHour = pendingCal.get(Calendar.HOUR_OF_DAY),
        initialMinute = pendingCal.get(Calendar.MINUTE),
        is24Hour = true
    )

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Confirm Task Prioritization",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive 3D Cube Selector
                InteractiveGraph(
                    importance = importance,
                    urgency = urgency,
                    estimatedMinutes = estimatedMinutes,
                    onValueChange = { imp, urg, dur ->
                        importance = imp
                        urgency = urg
                        estimatedMinutes = dur
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Material3 Date Picker ──────────────────────────────────
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showDatePicker = false
                                // Store the selected UTC midnight to pendingCal, then open time picker
                                datePickerState.selectedDateMillis?.let { selectedMs ->
                                    pendingCal.timeInMillis = selectedMs
                                }
                                showTimePicker = true
                            }) { Text("Next") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }

                // ── Material3 Time Picker ──────────────────────────────────
                if (showTimePicker) {
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                showTimePicker = false
                                pendingCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                pendingCal.set(Calendar.MINUTE, timePickerState.minute)
                                pendingCal.set(Calendar.SECOND, 0)
                                pendingCal.set(Calendar.MILLISECOND, 0)
                                deadline = pendingCal.timeInMillis
                            }) { Text("Set") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
                        },
                        text = {
                            TimePicker(state = timePickerState)
                        }
                    )
                }

                // ── Deadline display row (tapping opens the date picker) ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .clickable { showDatePicker = true }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Deadline",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = deadline?.let { dateFormatter.format(Date(it)) } ?: "No Deadline Set",
                        color = if (deadline != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (deadline != null) {
                    // Soft deadline config options
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = isSoftDeadline,
                                onCheckedChange = { isSoftDeadline = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                "Soft Deadline (Allow Grace Period)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }

                        if (isSoftDeadline) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Grace Period: $graceDays days",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Slider(
                                    value = graceDays.toFloat(),
                                    onValueChange = { graceDays = it.roundToInt() },
                                    valueRange = 1f..7f,
                                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { deadline = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear Deadline")
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isRepeating,
                        onCheckedChange = { isRepeating = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "Repeating Task",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    )
                }

                if (isRepeating) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        OutlinedTextField(
                            value = selectedRecurrence.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, "Select Frequency", Modifier.clickable { recurrenceExpanded = true })
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { recurrenceExpanded = true }
                        )
                        DropdownMenu(
                            expanded = recurrenceExpanded,
                            onDismissRequest = { recurrenceExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            RecurrenceType.entries.forEach { type ->
                                if (type != RecurrenceType.CUSTOM_DAYS) {
                                    DropdownMenuItem(
                                        text = { Text(type.name, color = MaterialTheme.colorScheme.onSurface) },
                                        onClick = {
                                            selectedRecurrence = type
                                            recurrenceExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                if (isRepeating) {
                                    onConfirmRepeating(
                                        RepeatingTask(
                                            title = title.trim(),
                                            description = description.trim(),
                                            importance = importance,
                                            urgency = urgency,
                                            estimatedMinutes = estimatedMinutes,
                                            recurrenceType = selectedRecurrence,
                                            intervalValue = 1,
                                            nextDueDate = deadline ?: (System.currentTimeMillis() + 86400000L),
                                            isSoftDeadline = isSoftDeadline,
                                            graceDays = graceDays
                                        )
                                    )
                                } else {
                                    onConfirm(
                                        Task(
                                            title = title.trim(),
                                            description = description.trim(),
                                            importance = importance,
                                            urgency = urgency,
                                            estimatedMinutes = estimatedMinutes,
                                            deadline = deadline,
                                            isSoftDeadline = if (deadline != null) isSoftDeadline else false,
                                            graceDays = if (deadline != null) graceDays else 0,
                                            isScratchPadItem = false // Mark processed
                                        )
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Save Task")
                    }
                }
            }
        }
    }
}
