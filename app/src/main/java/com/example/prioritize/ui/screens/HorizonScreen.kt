package com.example.prioritize.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.SpecialDateType
import com.example.prioritize.ui.viewmodel.TaskViewModel
import java.util.Calendar

sealed class HorizonItem : Comparable<HorizonItem> {
    data class TaskItem(val task: Task, val score: Double) : HorizonItem() {
        override fun compareTo(other: HorizonItem): Int {
            return when (other) {
                is TaskItem -> other.score.compareTo(this.score) // Descending priority
                is DateItem -> 1 // Dates come first
            }
        }
    }
    data class DateItem(val date: SpecialDate, val msRemaining: Long) : HorizonItem() {
        override fun compareTo(other: HorizonItem): Int {
            return when (other) {
                is TaskItem -> -1 // Dates come first
                is DateItem -> this.msRemaining.compareTo(other.msRemaining) // Ascending time
            }
        }
    }
}

fun getNextOccurrenceMs(date: SpecialDate, currentTimeMs: Long): Long {
    val currentCal = Calendar.getInstance().apply { timeInMillis = currentTimeMs }
    val currentYear = currentCal.get(Calendar.YEAR)
    
    val targetCal = Calendar.getInstance().apply {
        clear()
        set(currentYear, date.dateMonth - 1, date.dateDay)
    }
    
    if (targetCal.timeInMillis < currentTimeMs - (24 * 60 * 60 * 1000L)) {
        targetCal.set(Calendar.YEAR, currentYear + 1)
    }
    return targetCal.timeInMillis
}

@Composable
fun HorizonScreen(viewModel: TaskViewModel) {
    val activeTasks by viewModel.activeTasks.collectAsState(initial = emptyList())
    val specialDates by viewModel.specialDates.collectAsState(initial = emptyList())

    // currentTime refreshes every 60 seconds so horizon buckets stay accurate
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val oneWeekMs = 7L * 24 * 60 * 60 * 1000
    val oneMonthMs = 30L * 24 * 60 * 60 * 1000
    val oneQuarterMs = 90L * 24 * 60 * 60 * 1000

    val weekItems = remember(activeTasks, specialDates, currentTime) {
        val tasks = activeTasks.filter { task ->
            val deadline = task.deadline ?: return@filter false
            val remaining = deadline - currentTime
            remaining <= oneWeekMs
        }.map { HorizonItem.TaskItem(it, it.getPriorityScore(currentTime)) }
        
        val dates = specialDates.mapNotNull { date ->
            val nextMs = getNextOccurrenceMs(date, currentTime)
            val remaining = nextMs - currentTime
            if (remaining <= oneWeekMs) HorizonItem.DateItem(date, remaining) else null
        }
        (tasks + dates).sorted()
    }

    val monthItems = remember(activeTasks, specialDates, currentTime) {
        val tasks = activeTasks.filter { task ->
            val deadline = task.deadline ?: return@filter false
            val remaining = deadline - currentTime
            val isInRange = remaining in (oneWeekMs + 1)..oneMonthMs
            val isLongCadence = task.repeatingTaskId == null
            isInRange && isLongCadence
        }.map { HorizonItem.TaskItem(it, it.getPriorityScore(currentTime)) }
        
        val dates = specialDates.mapNotNull { date ->
            val nextMs = getNextOccurrenceMs(date, currentTime)
            val remaining = nextMs - currentTime
            if (remaining in (oneWeekMs + 1)..oneMonthMs) HorizonItem.DateItem(date, remaining) else null
        }
        (tasks + dates).sorted()
    }

    val quarterItems = remember(activeTasks, specialDates, currentTime) {
        val tasks = activeTasks.filter { task ->
            val deadline = task.deadline ?: return@filter false
            val remaining = deadline - currentTime
            val isInRange = remaining in (oneMonthMs + 1)..oneQuarterMs
            val isLongCadence = task.repeatingTaskId == null
            isInRange && isLongCadence
        }.map { HorizonItem.TaskItem(it, it.getPriorityScore(currentTime)) }
        
        val dates = specialDates.mapNotNull { date ->
            val nextMs = getNextOccurrenceMs(date, currentTime)
            val remaining = nextMs - currentTime
            if (remaining in (oneMonthMs + 1)..oneQuarterMs) HorizonItem.DateItem(date, remaining) else null
        }
        (tasks + dates).sorted()
    }

    val yearItems = remember(activeTasks, specialDates, currentTime) {
        val tasks = activeTasks.filter { task ->
            val deadline = task.deadline ?: return@filter false
            val remaining = deadline - currentTime
            val isInRange = remaining > oneQuarterMs
            val isLongCadence = task.repeatingTaskId == null
            isInRange && isLongCadence
        }.map { HorizonItem.TaskItem(it, it.getPriorityScore(currentTime)) }
        
        val dates = specialDates.mapNotNull { date ->
            val nextMs = getNextOccurrenceMs(date, currentTime)
            val remaining = nextMs - currentTime
            if (remaining > oneQuarterMs) HorizonItem.DateItem(date, remaining) else null
        }
        (tasks + dates).sorted()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Logarithmic Time Horizon",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Future outlook. Repeating items filtered by cadence to reduce clutter.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        item {
            HorizonPanel(title = "Week Ahead", items = weekItems, defaultColor = Color(0xFF03DAC6))
        }

        item {
            HorizonPanel(title = "Month Ahead", items = monthItems, defaultColor = Color(0xFFBB86FC))
        }

        item {
            HorizonPanel(title = "Quarter Ahead", items = quarterItems, defaultColor = Color(0xFF3700B3))
        }

        item {
            HorizonPanel(title = "Year & Beyond", items = yearItems, defaultColor = Color(0xFF1F1B24))
        }
    }
}

@Composable
fun HorizonPanel(
    title: String,
    items: List<HorizonItem>,
    defaultColor: Color
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151522))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${items.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isExpanded) "Collapse" else "Expand",
                    color = defaultColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (items.isEmpty()) {
                        Text(
                            text = "No scheduled items in this period.",
                            color = Color.DarkGray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        items.forEach { item ->
                            when (item) {
                                is HorizonItem.TaskItem -> CompactHorizonTaskRow(item.task)
                                is HorizonItem.DateItem -> CompactHorizonDateRow(item.date, item.msRemaining)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactHorizonTaskRow(task: Task) {
    val score = task.getPriorityScore()
    val indicatorColor = when {
        score >= 45.0 -> Color(0xFFCF6679) // Critical - Red
        score >= 30.0 -> Color(0xFFFFB74D) // High - Orange
        score >= 20.0 -> Color(0xFFBB86FC) // Medium - Purple
        else -> Color(0xFF03DAC6)          // Normal - Green
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E2C))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            if (task.description.isNotBlank()) {
                Text(
                    text = task.description,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
        Text(
            text = String.format("%.0f", score),
            color = indicatorColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun CompactHorizonDateRow(date: SpecialDate, msRemaining: Long) {
    val indicatorColor = when (date.type) {
        SpecialDateType.BIRTHDAY -> Color(0xFFFF4081) // Pink
        SpecialDateType.ANNIVERSARY -> Color(0xFFFBC02D) // Gold
        SpecialDateType.MARITAL_CARE -> Color(0xFF29B6F6) // Light Blue
    }
    
    val daysRemaining = (msRemaining / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    val timeLabel = when {
        daysRemaining == 0L -> "Today!"
        daysRemaining == 1L -> "Tmrw"
        else -> "${daysRemaining}d"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E2C).copy(alpha = 0.8f))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 24.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = when(date.type) {
                SpecialDateType.BIRTHDAY -> "🎂"
                SpecialDateType.ANNIVERSARY -> "🥂"
                SpecialDateType.MARITAL_CARE -> "💝"
            },
            fontSize = 18.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = date.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val monthName = java.text.DateFormatSymbols().months[date.dateMonth - 1].take(3)
            Text(
                text = "$monthName ${date.dateDay}",
                color = Color.LightGray,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Text(
            text = timeLabel,
            color = indicatorColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
