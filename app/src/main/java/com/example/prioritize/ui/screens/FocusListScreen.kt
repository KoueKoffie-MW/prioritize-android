package com.example.prioritize.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prioritize.data.Task
import com.example.prioritize.ui.components.ConfirmTaskDialog
import com.example.prioritize.ui.components.SwipeableTaskCard
import com.example.prioritize.ui.components.TaskCard
import com.example.prioritize.ui.viewmodel.TaskViewModel

// Hardcoded dark palette — consistent with all other screens in the app
private val BG = Color(0xFF0D0D1A)
private val SURFACE = Color(0xFF1A1A2E)
private val ACCENT_TEAL = Color(0xFF03DAC6)
private val ACCENT_RED = Color(0xFFFF6B6B)
private val ACCENT_GREEN = Color(0xFF22D3A0)
private val TEXT_PRIMARY = Color.White
private val TEXT_SECONDARY = Color(0xFF9999BB)
private val DIVIDER = Color(0xFF222238)

@Composable
fun FocusListScreen(
    viewModel: TaskViewModel,
    // Breakdown dialog is hoisted to MainDashboard — screens tunnel click events up.
    onBreakdownClick: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val completedTasks by viewModel.completedTasks.collectAsState()
    val subTasksMap by viewModel.subTasksMap.collectAsState()

    var activeTaskForEdit by remember { mutableStateOf<Task?>(null) }
    var showCompleted by remember { mutableStateOf(false) }
    var sortByDeadline by remember { mutableStateOf(false) }

    val isModelAvailable by viewModel.isModelAvailable.collectAsState()

    // Sort locally: VM already sorts by score; deadline sort is UI-only
    val sortedTasks = if (sortByDeadline) {
        activeTasks.sortedWith(
            compareBy<Task> { it.deadline == null }.thenBy { it.deadline }
        )
    } else activeTasks

    val top3Tasks = sortedTasks.take(3)
    val remainingTasks = sortedTasks.drop(3)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 16.dp)
    ) {
        // ── Compact header ─────────────────────────────────────────────────
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Focus",
                color = TEXT_PRIMARY,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            val total = activeTasks.size + completedTasks.size
            Text(
                text = if (total > 0) "${completedTasks.size} / $total done" else "No tasks",
                color = TEXT_SECONDARY,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            text = if (sortByDeadline) "Sorted by deadline" else "Ranked by priority score",
            color = TEXT_SECONDARY,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        // ── Sort toggle pill ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            listOf(false to "🔥 Score", true to "📅 Deadline").forEach { (isDeadline, label) ->
                val selected = sortByDeadline == isDeadline
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) ACCENT_TEAL else Color.Transparent)
                        .clickable { sortByDeadline = isDeadline }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.Black else TEXT_SECONDARY,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Progress bar — shows completion fraction of total lifetime tasks
        val total = activeTasks.size + completedTasks.size
        val fraction = if (total > 0) completedTasks.size.toFloat() / total else 0f
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = ACCENT_GREEN,
            trackColor = DIVIDER
        )
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = DIVIDER, modifier = Modifier.padding(bottom = 8.dp))

        if (activeTasks.isEmpty()) {
            // ── Empty state ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✨", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "All clear!",
                        color = TEXT_PRIMARY,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Dump your next thought in the Scratch Pad.",
                        color = TEXT_SECONDARY,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // ── Top 3 Priorities ───────────────────────────────────────
                if (top3Tasks.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ACCENT_RED.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "TOP PRIORITIES",
                                    color = ACCENT_RED,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }

                    items(top3Tasks, key = { it.id }) { task ->
                        val subTasks = subTasksMap[task.id] ?: emptyList()
                        SwipeableTaskCard(
                            task = task,
                            subTasks = subTasks,
                            onCompleteChange = { isChecked -> viewModel.toggleTaskCompletion(task, isChecked) },
                            onSubTaskCompleteChange = { subTask, isChecked -> viewModel.toggleSubTaskCompletion(subTask, isChecked) },
                            onDeleteClick = { viewModel.deleteTask(task) },
                            onEditClick = { activeTaskForEdit = task },
                            onBreakdownClick = { onBreakdownClick(task) }
                        )
                    }
                }

                // ── Backlog ────────────────────────────────────────────────
                if (remainingTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = DIVIDER, modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ACCENT_TEAL.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "BACKLOG",
                                    color = ACCENT_TEAL,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                            Text(
                                text = "  ${remainingTasks.size} remaining",
                                color = TEXT_SECONDARY,
                                fontSize = 12.sp
                            )
                        }
                    }

                    items(remainingTasks, key = { it.id }) { task ->
                        val subTasks = subTasksMap[task.id] ?: emptyList()
                        SwipeableTaskCard(
                            task = task,
                            subTasks = subTasks,
                            onCompleteChange = { isChecked -> viewModel.toggleTaskCompletion(task, isChecked) },
                            onSubTaskCompleteChange = { subTask, isChecked -> viewModel.toggleSubTaskCompletion(subTask, isChecked) },
                            onDeleteClick = { viewModel.deleteTask(task) },
                            onEditClick = { activeTaskForEdit = task },
                            onBreakdownClick = { onBreakdownClick(task) }
                        )
                    }
                }

                // ── Completed section ──────────────────────────────────
                if (completedTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = DIVIDER, modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showCompleted = !showCompleted }
                                .background(ACCENT_GREEN.copy(alpha = 0.07f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ACCENT_GREEN.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "COMPLETED",
                                        color = ACCENT_GREEN,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                Text(
                                    text = "  ${completedTasks.size} task${if (completedTasks.size == 1) "" else "s"}",
                                    color = TEXT_SECONDARY,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = if (showCompleted) "▲ Hide" else "▼ Show",
                                color = ACCENT_GREEN,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showCompleted) {
                        item {
                            // Clear all completed button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.deleteAllCompletedTasks() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFFCF6679)
                                    )
                                ) {
                                    Text(
                                        text = "🗑 Clear all completed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        items(completedTasks, key = { "done_${it.id}" }) { task ->
                            val subTasks = subTasksMap[task.id] ?: emptyList()
                            Box(modifier = Modifier.alpha(0.55f)) {
                                TaskCard(
                                    task = task,
                                    subTasks = subTasks,
                                    onCompleteChange = { isChecked ->
                                        viewModel.toggleTaskCompletion(task, isChecked)
                                    },
                                    onSubTaskCompleteChange = { subTask, isChecked ->
                                        viewModel.toggleSubTaskCompletion(subTask, isChecked)
                                    },
                                    onDeleteClick = { viewModel.deleteTask(task) },
                                    onEditClick = { activeTaskForEdit = task },
                                    onBreakdownClick = { onBreakdownClick(task) }
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Edit Task Dialog
        activeTaskForEdit?.let { task ->
            ConfirmTaskDialog(
                suggestion = com.example.prioritize.ai.ParsedTaskSuggestion(
                    title = task.title,
                    description = task.description,
                    importance = task.importance,
                    urgency = task.urgency,
                    estimatedMinutes = task.estimatedMinutes,
                    deadline = task.deadline
                ),
                onConfirm = { updatedTask ->
                    val finalTask = updatedTask.copy(
                        id = task.id,
                        createdAt = task.createdAt
                    )
                    viewModel.saveTask(finalTask)
                    activeTaskForEdit = null
                },
                onConfirmRepeating = { updatedRepTask ->
                    viewModel.saveRepeatingTask(updatedRepTask)
                    activeTaskForEdit = null
                },
                onDismiss = { activeTaskForEdit = null }
            )
        }
    }
}
