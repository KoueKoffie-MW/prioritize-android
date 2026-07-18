package com.example.prioritize.ai


interface TaskParser {
    suspend fun parseTaskFromText(inputText: String): ParsedTaskSuggestion?
    suspend fun generateBreakdownPrompt(taskTitle: String): String
    suspend fun parseSubTasksFromResponse(pastedText: String): List<ParsedSubTaskSuggestion>
    suspend fun generateSubTasksLocally(taskTitle: String): List<ParsedSubTaskSuggestion>
    suspend fun runRawInference(prompt: String): String?
}

data class ParsedTaskSuggestion(
    val title: String,
    val description: String,
    val importance: Int, // 1 to 10
    val urgency: Int,    // 1 to 10
    val estimatedMinutes: Int = 15,
    val deadline: Long?,  // Epoch ms
    val recurrenceType: String? = null // "DAILY", "WEEKLY", "MONTHLY", "YEARLY" or null
)

data class ParsedSubTaskSuggestion(
    val title: String,
    val estimatedMinutes: Int
)
