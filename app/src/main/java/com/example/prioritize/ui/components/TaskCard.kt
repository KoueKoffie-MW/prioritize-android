package com.example.prioritize.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prioritize.data.SubTask
import com.example.prioritize.data.Task
import androidx.compose.ui.text.AnnotatedString
import java.util.*

// buildBionicString() lives in TextUtils.kt (same package — no import needed)

// ── App-wide dark palette ────────────────────────────────────────────────────
private val CARD_BG       = Color(0xFF16162A)
private val CARD_BG_TOP   = Color(0xFF1D1D32)  // Slightly lighter for top-3 emphasis
private val TEXT_PRIMARY   = Color(0xFFF0F0FF)
private val TEXT_SECONDARY = Color(0xFF8888AA)
private val DIVIDER_COLOR  = Color(0xFF252540)

// ── Priority colour mapping ──────────────────────────────────────────────────
private fun scoreColor(score: Int) = when {
    score >= 45 -> Color(0xFFEF4444)   // Critical — red
    score >= 30 -> Color(0xFFF59E0B)   // Warning  — amber
    else        -> Color(0xFF22D3A0)   // OK       — emerald
}

@Composable
fun TaskCard(
    task: Task,
    subTasks: List<SubTask>,
    onCompleteChange: (Boolean) -> Unit,
    onSubTaskCompleteChange: (SubTask, Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onBreakdownClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val priorityScore = remember(task) { task.getPriorityScore() }
    val roundedScore = remember(priorityScore) { kotlin.math.round(priorityScore).toInt() }
    val scoreCol = scoreColor(roundedScore)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = CARD_BG),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // ── Priority left stripe ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(scoreCol)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            ) {
                // ── Title row ─────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = onCompleteChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = scoreCol,
                            checkmarkColor = Color.Black,
                            uncheckedColor = TEXT_SECONDARY
                        ),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 6.dp, top = 4.dp)
                    ) {
                        Text(
                            text = if (task.isCompleted) AnnotatedString(task.title)
                                   else buildBionicString(task.title, TEXT_PRIMARY),
                            color = if (task.isCompleted) TEXT_SECONDARY else TEXT_PRIMARY,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough
                                            else TextDecoration.None
                        )
                        if (task.description.isNotEmpty()) {
                            Text(
                                text = task.description,
                                color = TEXT_SECONDARY,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // ── Premium score badge ────────────────────────────────
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(scoreCol)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = roundedScore.toString(),
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                letterSpacing = 0.sp
                            )
                            val scoreLabel = when {
                                roundedScore >= 45 -> "CRIT"
                                roundedScore >= 30 -> "HIGH"
                                roundedScore >= 20 -> "MED"
                                else -> "LOW"
                            }
                            Text(
                                text = scoreLabel,
                                color = Color.Black.copy(alpha = 0.65f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // ── Meta chips row (Imp / Urg / Deadline) ─────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Importance chip
                    MetaChip(label = "I:${task.importance}", color = Color(0xFF7C3AED))
                    Spacer(Modifier.width(6.dp))
                    // Urgency chip
                    MetaChip(label = "U:${task.urgency}", color = Color(0xFF0D9488))

                    if (task.deadline != null) {
                        Spacer(Modifier.width(8.dp))
                        val now = System.currentTimeMillis()
                        val daysLeft = ((task.deadline - now) / (1000L * 60 * 60 * 24)).toInt()
                        val (deadlineLabel, deadlineColor) = when {
                            daysLeft < 0  -> "⚡ OVERDUE"            to Color(0xFFEF4444)
                            daysLeft == 0 -> "⚡ Today!"             to Color(0xFFEF4444)
                            daysLeft == 1 -> "⚡ Tmrw"               to Color(0xFFF59E0B)
                            daysLeft <= 7 -> "⏰ ${daysLeft}d left"  to Color(0xFFF59E0B)
                            else -> "⏰ ${DateFormat.format("MMM dd", Date(task.deadline))}" to TEXT_SECONDARY
                        }
                        Text(
                            text = deadlineLabel,
                            color = deadlineColor,
                            fontSize = 11.sp,
                            fontWeight = if (daysLeft <= 1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Repeating task badge
                    if (task.repeatingTaskId != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "🔁",
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E1E2C))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Time estimate chip — shown when estimatedMinutes > 0
                    if (task.estimatedMinutes > 0) {
                        Spacer(Modifier.width(6.dp))
                        val timeLabel = when {
                            task.estimatedMinutes < 60 -> "⏱ ${task.estimatedMinutes}m"
                            task.estimatedMinutes % 60 == 0 -> "⏱ ${task.estimatedMinutes / 60}h"
                            else -> "⏱ ${task.estimatedMinutes / 60}h${task.estimatedMinutes % 60}m"
                        }
                        Text(
                            text = timeLabel,
                            color = Color(0xFF7777AA),
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF1E1E2C))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Subtask toggle
                    if (subTasks.isNotEmpty()) {
                        val completedCount = subTasks.count { it.isCompleted }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { expanded = !expanded }
                                .background(DIVIDER_COLOR)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$completedCount/${subTasks.size} steps",
                                color = TEXT_SECONDARY,
                                fontSize = 11.sp
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp
                                              else Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = TEXT_SECONDARY,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ── Action icons ───────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TEXT_SECONDARY,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFCF6679),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onBreakdownClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "Break down task",
                            tint = Color(0xFF7C3AED),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (subTasks.isNotEmpty()) {
                        val totalTime = subTasks.sumOf { it.estimatedMinutes }
                        Text(
                            text = "~${totalTime}m total",
                            color = TEXT_SECONDARY,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // ── Sub-tasks checklist ────────────────────────────────────
                AnimatedVisibility(
                    visible = expanded && subTasks.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        HorizontalDivider(color = DIVIDER_COLOR, thickness = 1.dp)
                        subTasks.forEachIndexed { index, subTask ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Checkbox(
                                    checked = subTask.isCompleted,
                                    onCheckedChange = { isChecked ->
                                        onSubTaskCompleteChange(subTask, isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = scoreCol,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = TEXT_SECONDARY
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                                Text(
                                    text = "${index + 1}. ${subTask.title}",
                                    color = if (subTask.isCompleted) TEXT_SECONDARY
                                            else TEXT_PRIMARY,
                                    fontSize = 13.sp,
                                    textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough
                                                    else TextDecoration.None,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${subTask.estimatedMinutes}m",
                                    color = TEXT_SECONDARY,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Compact meta chip ────────────────────────────────────────────────────────
@Composable
private fun MetaChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Extension to scale checkbox size slightly
private fun Modifier.scale(scale: Float): Modifier = this.then(
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0, zIndex = 0f) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
)

// ── SwipeableTaskCard ────────────────────────────────────────────
// Wraps TaskCard with swipe-to-complete (right) and swipe-to-delete (left).
// For use only with active (non-completed) tasks in FocusListScreen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTaskCard(
    task: Task,
    subTasks: List<SubTask>,
    onCompleteChange: (Boolean) -> Unit,
    onSubTaskCompleteChange: (SubTask, Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onBreakdownClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Migrate away from deprecated confirmValueChange — use LaunchedEffect on currentValue instead.
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.38f }
    )

    // React to swipe completion AFTER the state is committed
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                // Swipe right → haptic + mark complete, snap card back
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCompleteChange(true)
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                // Swipe left → haptic + delete
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDeleteClick()
            }
            SwipeToDismissBoxValue.Settled -> { /* no-op */ }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor by animateColorAsState(
                targetValue = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF22D3A0).copy(alpha = 0.88f)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFCF6679).copy(alpha = 0.88f)
                    else                              -> Color.Transparent
                },
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else                              -> Alignment.CenterEnd
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✓", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Complete", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    SwipeToDismissBoxValue.EndToStart -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("🗑", fontSize = 18.sp)
                    }
                    else -> {}
                }
            }
        },
        modifier = modifier
    ) {
        TaskCard(
            task = task,
            subTasks = subTasks,
            onCompleteChange = onCompleteChange,
            onSubTaskCompleteChange = onSubTaskCompleteChange,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onBreakdownClick = onBreakdownClick
        )
    }
}
