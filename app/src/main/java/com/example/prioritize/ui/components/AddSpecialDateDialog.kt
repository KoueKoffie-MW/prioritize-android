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
import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.SpecialDateType
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpecialDateDialog(
    existingSpecialDate: SpecialDate? = null,
    onDismiss: () -> Unit,
    onSave: (SpecialDate) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(existingSpecialDate?.name ?: "") }
    var selectedType by remember { mutableStateOf(existingSpecialDate?.type ?: SpecialDateType.BIRTHDAY) }
    var typeExpanded by remember { mutableStateOf(false) }

    // Initialize to today or existing date
    val currentCal = Calendar.getInstance()
    var selectedMonth by remember { mutableStateOf(existingSpecialDate?.dateMonth ?: (currentCal.get(Calendar.MONTH) + 1)) } // 1-12
    var selectedDay by remember { mutableStateOf(existingSpecialDate?.dateDay ?: currentCal.get(Calendar.DAY_OF_MONTH)) }

    val months = java.text.DateFormatSymbols().months.take(12)
    var monthExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var originalYear by remember { mutableStateOf(existingSpecialDate?.originalYear?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (existingSpecialDate == null) "Add Important Date" else "Edit Important Date",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Angelique's Birthday)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Type Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Event Type") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, "Select Type", Modifier.clickable { typeExpanded = true })
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFBB86FC),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { typeExpanded = true }
                    )
                    DropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                        modifier = Modifier.background(Color(0xFF28283C))
                    ) {
                        SpecialDateType.entries.forEach { type ->
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

                OutlinedTextField(
                    value = originalYear,
                    onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) originalYear = it },
                    label = { Text("Original Year (Optional, e.g. 1990)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFBB86FC), unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Month Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = months[selectedMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, "Select Month", Modifier.clickable { monthExpanded = true })
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                  focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFBB86FC), unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { monthExpanded = true }
                        )
                        DropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false },
                            modifier = Modifier.background(Color(0xFF28283C)).heightIn(max = 300.dp)
                        ) {
                            months.forEachIndexed { index, monthName ->
                                DropdownMenuItem(
                                    text = { Text(monthName, color = Color.White) },
                                    onClick = {
                                        selectedMonth = index + 1
                                        monthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Day Dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = selectedDay.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, "Select Day", Modifier.clickable { dayExpanded = true })
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFBB86FC), unfocusedBorderColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { dayExpanded = true }
                        )
                        DropdownMenu(
                            expanded = dayExpanded,
                            onDismissRequest = { dayExpanded = false },
                            modifier = Modifier.background(Color(0xFF28283C)).heightIn(max = 300.dp)
                        ) {
                            (1..31).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString(), color = Color.White) },
                                    onClick = {
                                        selectedDay = day
                                        dayExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (existingSpecialDate != null && onDelete != null) {
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
                                if (name.isNotBlank()) {
                                    onSave(
                                        SpecialDate(
                                            id = existingSpecialDate?.id ?: 0,
                                            name = name.trim(),
                                            type = selectedType,
                                            dateMonth = selectedMonth,
                                            dateDay = selectedDay,
                                            originalYear = originalYear.toIntOrNull(),
                                            lastTriggeredYear = existingSpecialDate?.lastTriggeredYear ?: 0
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
                        ) {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
