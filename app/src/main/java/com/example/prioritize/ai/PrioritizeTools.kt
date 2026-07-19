package com.example.prioritize.ai

import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

sealed class Action

data class CreateTaskAction(
    val title: String,
    val description: String,
    val importance: Int,
    val urgency: Int,
    val estimatedMinutes: Int,
    val daysUntilDue: Int?
) : Action()

data class CreateRepeatingTaskAction(
    val title: String,
    val description: String,
    val recurrenceType: String,
    val intervalValue: Int,
    val importance: Int,
    val urgency: Int
) : Action()

data class CreateSpecialDateAction(
    val name: String,
    val month: Int,
    val day: Int,
    val type: String
) : Action()

class PrioritizeTools(
    private val onActionCalled: (Action) -> Unit
) : ToolSet {

    @Tool(description = "Creates a new simple task in the task manager.")
    fun createTask(
        @ToolParam(description = "The short, action-oriented title of the task.") title: String,
        @ToolParam(description = "Optional detailed description or steps.") description: String = "",
        @ToolParam(description = "Importance score from 1 to 10.") importance: Int = 5,
        @ToolParam(description = "Urgency score from 1 to 10.") urgency: Int = 5,
        @ToolParam(description = "Estimated duration in minutes.") estimatedMinutes: Int = 15,
        @ToolParam(description = "Optional days until the task is due (e.g. 1 for tomorrow).") daysUntilDue: Int? = null
    ): Map<String, String> {
        onActionCalled(
            CreateTaskAction(
                title = title,
                description = description,
                importance = importance.coerceIn(1, 10),
                urgency = urgency.coerceIn(1, 10),
                estimatedMinutes = estimatedMinutes,
                daysUntilDue = daysUntilDue
            )
        )
        return mapOf("status" to "success", "action" to "createTask", "title" to title)
    }

    @Tool(description = "Creates a new recurring/repeating task.")
    fun createRepeatingTask(
        @ToolParam(description = "The title of the repeating task.") title: String,
        @ToolParam(description = "The description.") description: String = "",
        @ToolParam(description = "Recurrence cadence type: DAILY, WEEKLY, MONTHLY, or YEARLY.") recurrenceType: String = "WEEKLY",
        @ToolParam(description = "Interval value (e.g. 1 for every week, 2 for every 2 weeks).") intervalValue: Int = 1,
        @ToolParam(description = "Importance score 1 to 10.") importance: Int = 5,
        @ToolParam(description = "Urgency score 1 to 10.") urgency: Int = 5
    ): Map<String, String> {
        onActionCalled(
            CreateRepeatingTaskAction(
                title = title,
                description = description,
                recurrenceType = recurrenceType.uppercase(),
                intervalValue = intervalValue.coerceAtLeast(1),
                importance = importance.coerceIn(1, 10),
                urgency = urgency.coerceIn(1, 10)
            )
        )
        return mapOf("status" to "success", "action" to "createRepeatingTask", "title" to title)
    }

    @Tool(description = "Creates a new important special date (like a birthday or anniversary).")
    fun createSpecialDate(
        @ToolParam(description = "The name of the person or event.") name: String,
        @ToolParam(description = "Month of the event (1 to 12).") month: Int,
        @ToolParam(description = "Day of the month (1 to 31).") day: Int,
        @ToolParam(description = "Type of event: BIRTHDAY, ANNIVERSARY, or MARITAL_CARE.") type: String = "BIRTHDAY"
    ): Map<String, String> {
        onActionCalled(
            CreateSpecialDateAction(
                name = name,
                month = month.coerceIn(1, 12),
                day = day.coerceIn(1, 31),
                type = type.uppercase()
            )
        )
        return mapOf("status" to "success", "action" to "createSpecialDate", "name" to name)
    }
}
