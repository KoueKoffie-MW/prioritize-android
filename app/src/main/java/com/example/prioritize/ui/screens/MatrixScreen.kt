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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.prioritize.data.Task
import com.example.prioritize.ui.viewmodel.TaskViewModel

@Composable
fun MatrixScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val activeTasks by viewModel.activeTasks.collectAsState()
    var selectedQuadrant by remember { mutableStateOf<String?>(null) }

    // Map tasks to Eisenhower quadrants.
    // Threshold is 6 on a 1-10 scale (true midpoint), so only genuinely high-priority
    // tasks appear in "Do First" instead of ~80% of all tasks landing there.
    val doFirst = activeTasks.filter { it.importance >= 6 && it.urgency >= 6 }
    val schedule = activeTasks.filter { it.importance >= 6 && it.urgency < 6 }
    val delegate = activeTasks.filter { it.importance < 6 && it.urgency >= 6 }
    val eliminate = activeTasks.filter { it.importance < 6 && it.urgency < 6 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(16.dp)
    ) {
        Text(
            text = "Matrix",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Eisenhower quadrants · tap to manage",
            color = Color(0xFF8888AA),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 2x2 Grid Layout
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Do First (Urgent & Important)
                QuadrantCard(
                    title = "DO FIRST",
                    subtitle = "Urgent & Important",
                    tasks = doFirst,
                    color = Color(0xFFCF6679),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedQuadrant = "Do First" }
                )

                // Schedule (Important but Not Urgent)
                QuadrantCard(
                    title = "SCHEDULE",
                    subtitle = "Important / Low Urgency",
                    tasks = schedule,
                    color = Color(0xFFBB86FC),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedQuadrant = "Schedule" }
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delegate (Urgent but Not Important)
                QuadrantCard(
                    title = "DELEGATE",
                    subtitle = "Urgent / Low Importance",
                    tasks = delegate,
                    color = Color(0xFF03DAC6),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedQuadrant = "Delegate" }
                )

                // Eliminate (Not Urgent & Not Important)
                QuadrantCard(
                    title = "ELIMINATE",
                    subtitle = "Low Prio / Trivial",
                    tasks = eliminate,
                    color = Color.Gray,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedQuadrant = "Eliminate" }
                )
            }
        }

        // Expanded Quadrant View Dialog
        selectedQuadrant?.let { quad ->
            val tasksInQuad = when (quad) {
                "Do First" -> doFirst
                "Schedule" -> schedule
                "Delegate" -> delegate
                else -> eliminate
            }

            val quadColor = when (quad) {
                "Do First" -> Color(0xFFCF6679)
                "Schedule" -> Color(0xFFBB86FC)
                "Delegate" -> Color(0xFF03DAC6)
                else -> Color.Gray
            }

            Dialog(onDismissRequest = { selectedQuadrant = null }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151522))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        Text(
                            text = quad.uppercase(),
                            color = quadColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "Tasks matching this quadrant classification.",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (tasksInQuad.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("✓", color = quadColor, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "All clear in this quadrant",
                                        color = Color(0xFF777799),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(tasksInQuad) { task ->
                                    val score = task.getPriorityScore()
                                    val scoreColor = when {
                                        score >= 45.0 -> Color(0xFFEF4444)
                                        score >= 30.0 -> Color(0xFFF59E0B)
                                        else          -> Color(0xFF22D3A0)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1E1E2C))
                                            .padding(end = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left accent bar
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(52.dp)
                                                .background(quadColor)
                                        )
                                        Checkbox(
                                            checked = task.isCompleted,
                                            onCheckedChange = { isChecked ->
                                                viewModel.toggleTaskCompletion(task, isChecked)
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = quadColor)
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = task.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            if (task.description.isNotBlank()) {
                                                Text(
                                                    text = task.description,
                                                    color = Color(0xFF777799),
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        // Score badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(scoreColor)
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = String.format("%.0f", score),
                                                color = Color.Black,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { selectedQuadrant = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E1E2C),
                                contentColor = Color(0xFF03DAC6)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuadrantCard(
    title: String,
    subtitle: String,
    tasks: List<Task>,
    color: Color,
    modifier: Modifier = Modifier
) {
    val count = tasks.size
    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2C)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // ── Header row: title + count badge ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFF777799),
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                // Count badge — top right
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = if (count > 0) 0.9f else 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        color = if (count > 0) Color.Black else color,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Content: task previews or empty state ──────────────────────
            if (count == 0) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓ All clear",
                        color = color.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    tasks.take(3).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.08f))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = task.title,
                                color = Color(0xFFCCCCEE),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (count > 3) {
                        Text(
                            text = "+ ${count - 3} more",
                            color = color.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            modifier = Modifier.padding(start = 7.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
