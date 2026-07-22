package com.example.prioritize.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.data.RecurrenceType
import com.example.prioritize.data.RepeatingTask
import java.util.Calendar
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepeatingTaskDialog(
    existingRepeatingTask: RepeatingTask? = null,
    onDismiss: () -> Unit,
    onSave: (RepeatingTask) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(existingRepeatingTask?.title ?: "") }
    var description by remember { mutableStateOf(existingRepeatingTask?.description ?: "") }
    var selectedType by remember { mutableStateOf(existingRepeatingTask?.recurrenceType ?: RecurrenceType.WEEKLY) }
    var typeExpanded by remember { mutableStateOf(false) }
    var interval by remember { mutableStateOf(existingRepeatingTask?.intervalValue?.toString() ?: "1") }
    
    var importance by remember { mutableFloatStateOf(existingRepeatingTask?.importance?.toFloat() ?: 5f) }
    var urgency by remember { mutableFloatStateOf(existingRepeatingTask?.urgency?.toFloat() ?: 5f) }
    
    val context = LocalContext.current
    var startDate by remember { mutableStateOf(existingRepeatingTask?.nextDueDate ?: (System.currentTimeMillis() + 86400000L)) }
    var isSoftDeadline by remember { mutableStateOf(existingRepeatingTask?.isSoftDeadline ?: false) }
    var graceDays by remember { mutableFloatStateOf(existingRepeatingTask?.graceDays?.toFloat() ?: 2f) }
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }


    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (existingRepeatingTask == null) "Add Repeating Task" else "Edit Repeating Task",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = interval,
                        onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) interval = it },
                        label = { Text("Every") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.weight(0.3f),
                        singleLine = true
                    )

                    Box(modifier = Modifier.weight(0.7f).padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = selectedType.name,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, "Select Frequency", Modifier.clickable { typeExpanded = true })
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF03DAC6), unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }
                        )
                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(Color(0xFF28283C))
                        ) {
                            RecurrenceType.entries.forEach { type ->
                                if (type != RecurrenceType.CUSTOM_DAYS) {
                                    DropdownMenuItem(
                                        text = { Text(type.name, color = Color.White) },
                                        onClick = {
                                            selectedType = type
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Importance Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Importance", color = Color.LightGray, fontSize = 12.sp)
                        Text(importance.toInt().toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = importance,
                        onValueChange = { importance = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF03DAC6), activeTrackColor = Color(0xFF03DAC6))
                    )
                }

                // Urgency Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Urgency", color = Color.LightGray, fontSize = 12.sp)
                        Text(urgency.toInt().toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = urgency,
                        onValueChange = { urgency = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB74D), activeTrackColor = Color(0xFFFFB74D))
                    )
                }

                // Start Date Picker
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = startDate
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val selected = Calendar.getInstance()
                                selected.set(year, month, dayOfMonth, 0, 0, 0)
                                startDate = selected.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("First Instance: ", color = Color.White, fontSize = 14.sp)
                    Text(dateFormatter.format(startDate), color = Color(0xFF03DAC6), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Soft Deadline
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isSoftDeadline,
                        onCheckedChange = { isSoftDeadline = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF03DAC6))
                    )
                    Text("Soft Deadline", color = Color.White, fontSize = 14.sp)
                }
                
                if (isSoftDeadline) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grace Period", color = Color.LightGray, fontSize = 12.sp)
                            Text("${graceDays.toInt()} days", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = graceDays,
                            onValueChange = { graceDays = it },
                            valueRange = 0f..7f,
                            steps = 6,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF03DAC6), activeTrackColor = Color(0xFF03DAC6))
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (existingRepeatingTask != null && onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFCF6679))
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val intervalVal = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    onSave(
                                        RepeatingTask(
                                            id = existingRepeatingTask?.id ?: 0,
                                            title = title.trim(),
                                            description = description.trim(),
                                            importance = importance.toInt(),
                                            urgency = urgency.toInt(),
                                            recurrenceType = selectedType,
                                            intervalValue = intervalVal,
                                            nextDueDate = startDate,
                                            isSoftDeadline = isSoftDeadline,
                                            graceDays = graceDays.toInt(),
                                            lastCompletedAt = existingRepeatingTask?.lastCompletedAt
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                        ) {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
