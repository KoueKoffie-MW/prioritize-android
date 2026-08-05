package com.example.prioritize.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prioritize.data.Task
import com.example.prioritize.ui.components.SwipeableTaskCard
import com.example.prioritize.ui.components.TaskCard
import com.example.prioritize.ui.components.TaskDetailDialog
import com.example.prioritize.ui.viewmodel.TaskViewModel
import com.example.prioritize.data.RepeatingTask
import com.example.prioritize.ui.components.AddRepeatingTaskDialog
import kotlinx.coroutines.launch

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
    val deletedTasks by viewModel.deletedTasks.collectAsState()
    val subTasksMap by viewModel.subTasksMap.collectAsState()
    val repeatingTasks by viewModel.repeatingTasks.collectAsState()

    var activeTaskForEdit by remember { mutableStateOf<Task?>(null) }
    var editingRepeatingTask by remember { mutableStateOf<RepeatingTask?>(null) }
    var showCompleted by remember { mutableStateOf(false) }
    var showDeleted by remember { mutableStateOf(false) }
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

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val handleDeleteTask: (Task) -> Unit = { task ->
        viewModel.deleteTask(task)
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "'${task.title}' moved to Recycle Bin",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreTask(task)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BG)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            // ── Pinned Quick-Add ──────────────────────────────────────────────
            var quickAddText by remember { mutableStateOf("") }
            val focusManager = LocalFocusManager.current

            fun doQuickAdd() {
                if (quickAddText.isNotBlank()) {
                    viewModel.saveTask(
                        Task(
                            title = quickAddText.trim(),
                            isScratchPadItem = false,
                            importance = 5,
                            urgency = 5
                        )
                    )
                    quickAddText = ""
                    focusManager.clearFocus()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SURFACE)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    textStyle = TextStyle(color = TEXT_PRIMARY, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { doQuickAdd() }),
                    decorationBox = { innerTextField ->
                        if (quickAddText.isEmpty()) {
                            Text(
                                text = "+ Quick add a task…",
                                color = Color(0xFF444466),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (quickAddText.isNotBlank()) {
                    IconButton(
                        onClick = { doQuickAdd() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add task",
                            tint = ACCENT_TEAL,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (activeTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✨", fontSize = 44.sp)
                                Spacer(Modifier.height(8.dp))
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
                    }
                } else {
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
                                onDeleteClick = { handleDeleteTask(task) },
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
                                onDeleteClick = { handleDeleteTask(task) },
                                onEditClick = { activeTaskForEdit = task },
                                onBreakdownClick = { onBreakdownClick(task) }
                            )
                        }
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
                                    onDeleteClick = { handleDeleteTask(task) },
                                    onEditClick = { activeTaskForEdit = task },
                                    onBreakdownClick = { onBreakdownClick(task) }
                                )
                            }
                        }
                    }
                }

                // ── Recycle Bin section ──────────────────────────────────
                if (deletedTasks.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = DIVIDER, modifier = Modifier.padding(vertical = 12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showDeleted = !showDeleted }
                                .background(ACCENT_RED.copy(alpha = 0.07f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ACCENT_RED.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "RECYCLE BIN",
                                        color = ACCENT_RED,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                Text(
                                    text = "  ${deletedTasks.size} task${if (deletedTasks.size == 1) "" else "s"}",
                                    color = TEXT_SECONDARY,
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = if (showDeleted) "▲ Hide" else "▼ Show",
                                color = ACCENT_RED,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showDeleted) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { viewModel.emptyRecycleBin() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFFCF6679)
                                    )
                                ) {
                                    Text(
                                        text = "💥 Empty Recycle Bin",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        items(deletedTasks, key = { "bin_${it.id}" }) { task ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = BG.copy(alpha = 0.4f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, DIVIDER),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = TEXT_PRIMARY,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (task.description.isNotEmpty()) {
                                            Text(
                                                text = task.description,
                                                color = TEXT_SECONDARY,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { viewModel.restoreTask(task) }) {
                                            Text("↩️", fontSize = 16.sp)
                                        }
                                        IconButton(onClick = { viewModel.permanentlyDeleteTask(task) }) {
                                            Text("🗑️", fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Premium Edit Task Dialog (Task Detail Screen - Option C)
        activeTaskForEdit?.let { task ->
            val subTasks = subTasksMap[task.id] ?: emptyList()
            val parentRepeatingTask = repeatingTasks.find { it.id == task.repeatingTaskId }
            TaskDetailDialog(
                task = task,
                initialSubTasks = subTasks,
                parentRepeatingTask = parentRepeatingTask,
                onSave = { updatedTask, updatedSubTasks ->
                    val finalTask = updatedTask.copy(
                        id = task.id,
                        createdAt = task.createdAt
                    )
                    viewModel.saveTask(finalTask)
                    viewModel.saveSubTasks(updatedSubTasks)
                    activeTaskForEdit = null
                },
                onDelete = {
                    handleDeleteTask(task)
                    activeTaskForEdit = null
                },
                onDismiss = { activeTaskForEdit = null },
                onEditRepeatingTemplate = {
                    editingRepeatingTask = parentRepeatingTask
                    activeTaskForEdit = null
                }
            )
        }

        editingRepeatingTask?.let { rt ->
            AddRepeatingTaskDialog(
                existingRepeatingTask = rt,
                onDismiss = { editingRepeatingTask = null },
                onSave = { updated ->
                    viewModel.updateRepeatingTask(updated)
                    editingRepeatingTask = null
                },
                onDelete = {
                    viewModel.deleteRepeatingTask(rt)
                    editingRepeatingTask = null
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}
