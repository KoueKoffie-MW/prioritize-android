package com.example.prioritize.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prioritize.data.Task
import com.example.prioritize.ui.components.ConfirmTaskDialog
import com.example.prioritize.ui.components.TaskCard
import com.example.prioritize.ui.viewmodel.TaskViewModel

@Composable
fun FocusListScreen(
    viewModel: TaskViewModel,
    // Breakdown dialog is hoisted to MainDashboard — screens tunnel click events up.
    onBreakdownClick: (Task) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    val subTasksMap by viewModel.subTasksMap.collectAsState()

    var activeTaskForEdit by remember { mutableStateOf<Task?>(null) }

    // Observe isModelAvailable as a StateFlow so the UI updates when model is downloaded/deleted
    val isModelAvailable by viewModel.isModelAvailable.collectAsState()

    // Split tasks: Top 3 vs the rest
    val top3Tasks = activeTasks.take(3)
    val remainingTasks = activeTasks.drop(3)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Focus Dashboard",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Your tasks sorted dynamically by dynamic priority scoring.",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (activeTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active tasks. Dump thoughts in the Scratch Pad!",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Render Top 3 Priorities
                if (top3Tasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "TOP PRIORITIES",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(top3Tasks, key = { it.id }) { task ->
                        val subTasks = subTasksMap[task.id] ?: emptyList()
                        TaskCard(
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

                // Render remaining tasks
                if (remainingTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "BACKLOG",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    items(remainingTasks, key = { it.id }) { task ->
                        val subTasks = subTasksMap[task.id] ?: emptyList()
                        TaskCard(
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
            }
        }

        // Edit Task Dialog (using ConfirmTaskDialog reuse pattern)
        activeTaskForEdit?.let { task ->
            ConfirmTaskDialog(
                suggestion = com.example.prioritize.ai.ParsedTaskSuggestion(
                    title = task.title,
                    description = task.description,
                    importance = task.importance,
                    urgency = task.urgency,
                    estimatedMinutes = task.estimatedMinutes, // Preserve original, don't default to 15
                    deadline = task.deadline
                ),
                onConfirm = { updatedTask ->
                    // Preserve original database ID and creation dates
                    val finalTask = updatedTask.copy(
                        id = task.id,
                        createdAt = task.createdAt
                    )
                    viewModel.saveTask(finalTask)
                    activeTaskForEdit = null
                },
                onConfirmRepeating = { updatedRepTask ->
                    // If they edited a normal task and converted it to repeating,
                    // save it as a new repeating task and mark the original as done or delete it.
                    // For simplicity, we just save the repeating task here.
                    viewModel.saveRepeatingTask(updatedRepTask)
                    activeTaskForEdit = null
                },
                onDismiss = { activeTaskForEdit = null }
            )
        }
    }
}
