package com.example.prioritize.ui.viewmodel

import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.Task
import com.example.prioritize.data.RepeatingTask
import com.example.prioritize.data.ChatMessageEntity
import org.json.JSONObject

/** Type-safe sender identifier for chat messages. Replaces the stringly-typed "USER"/"AI" pattern. */
enum class MessageSender { USER, AI }

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPromoted: Boolean = false,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val documentPath: String? = null,
    val actionTask: Task? = null,
    val actionSpecialDate: SpecialDate? = null,
    val actionRepeatingTask: RepeatingTask? = null,
    val id: Long = 0
)

fun parseChatMessage(
    sender: MessageSender,
    response: String,
    imagePath: String? = null,
    audioPath: String? = null,
    documentPath: String? = null,
    id: Long = 0,
    timestamp: Long = System.currentTimeMillis(),
    isPromoted: Boolean = false
): ChatMessage {
    var cleanText = response
    var actionTask: Task? = null
    var actionSpecialDate: SpecialDate? = null
    var actionRepeatingTask: RepeatingTask? = null
    
    if (cleanText.contains("###TASK_SUGGESTION###")) {
        val parts = cleanText.split("###TASK_SUGGESTION###")
        cleanText = parts[0].trim()
        try {
            val suffix = parts[1].trim()
            val startIdx = suffix.indexOf('{')
            val endIdx = suffix.lastIndexOf('}')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonStr = suffix.substring(startIdx, endIdx + 1)
                val json = JSONObject(jsonStr)
                actionTask = Task(
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    importance = json.optInt("importance", 5).coerceIn(1, 10),
                    urgency = json.optInt("urgency", 5).coerceIn(1, 10),
                    isScratchPadItem = true
                )
            }
        } catch(e: Exception) {
            android.util.Log.e("ChatParser", "Failed to parse task suggestion JSON", e)
        }
    }
    
    if (cleanText.contains("###DATE_SUGGESTION###")) {
        val parts = cleanText.split("###DATE_SUGGESTION###")
        cleanText = parts[0].trim()
        try {
            val suffix = parts[1].trim()
            val startIdx = suffix.indexOf('{')
            val endIdx = suffix.lastIndexOf('}')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonStr = suffix.substring(startIdx, endIdx + 1)
                val json = JSONObject(jsonStr)
                actionSpecialDate = SpecialDate(
                    name = json.getString("name"),
                    dateMonth = json.getInt("month"),
                    dateDay = json.getInt("day"),
                    type = com.example.prioritize.data.SpecialDateType.entries.find { it.name == json.optString("type", "BIRTHDAY") }
                        ?: com.example.prioritize.data.SpecialDateType.BIRTHDAY
                )
            }
        } catch(e: Exception) {
            android.util.Log.e("ChatParser", "Failed to parse date suggestion JSON", e)
        }
    }
    
    if (cleanText.contains("###REPEATING_TASK_SUGGESTION###")) {
        val parts = cleanText.split("###REPEATING_TASK_SUGGESTION###")
        cleanText = parts[0].trim()
        try {
            val suffix = parts[1].trim()
            val startIdx = suffix.indexOf('{')
            val endIdx = suffix.lastIndexOf('}')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                val jsonStr = suffix.substring(startIdx, endIdx + 1)
                val json = JSONObject(jsonStr)
                actionRepeatingTask = RepeatingTask(
                    title = json.getString("title"),
                    description = json.optString("description", ""),
                    importance = json.optInt("importance", 5).coerceIn(1, 10),
                    urgency = json.optInt("urgency", 5).coerceIn(1, 10),
                    recurrenceType = com.example.prioritize.data.RecurrenceType.entries.find { it.name == json.optString("recurrenceType", "WEEKLY") } ?: com.example.prioritize.data.RecurrenceType.WEEKLY,
                    intervalValue = json.optInt("intervalValue", 1).coerceAtLeast(1),
                    nextDueDate = System.currentTimeMillis() + (24L * 60 * 60 * 1000)
                )
            }
        } catch(e: Exception) {
            android.util.Log.e("ChatParser", "Failed to parse repeating task suggestion JSON", e)
        }
    }
    
    return ChatMessage(
        sender = sender,
        text = cleanText,
        timestamp = timestamp,
        isPromoted = isPromoted,
        imagePath = imagePath,
        audioPath = audioPath,
        documentPath = documentPath,
        actionTask = actionTask,
        actionSpecialDate = actionSpecialDate,
        actionRepeatingTask = actionRepeatingTask,
        id = id
    )
}

fun ChatMessageEntity.toChatMessage(): ChatMessage {
    return parseChatMessage(
        sender = MessageSender.valueOf(this.sender),
        response = this.text,
        imagePath = this.imagePath,
        audioPath = this.audioPath,
        documentPath = this.documentPath,
        id = this.id,
        timestamp = this.timestamp,
        isPromoted = this.isPromoted
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    var rawText = this.text
    this.actionTask?.let {
        try {
            val json = JSONObject().apply {
                put("title", it.title)
                put("description", it.description)
                put("importance", it.importance)
                put("urgency", it.urgency)
            }
            rawText += " ###TASK_SUGGESTION### $json"
        } catch (e: Exception) {}
    }
    this.actionSpecialDate?.let {
        try {
            val json = JSONObject().apply {
                put("name", it.name)
                put("month", it.dateMonth)
                put("day", it.dateDay)
                put("type", it.type.name)
            }
            rawText += " ###DATE_SUGGESTION### $json"
        } catch (e: Exception) {}
    }
    this.actionRepeatingTask?.let {
        try {
            val json = JSONObject().apply {
                put("title", it.title)
                put("description", it.description)
                put("importance", it.importance)
                put("urgency", it.urgency)
                put("recurrenceType", it.recurrenceType.name)
                put("intervalValue", it.intervalValue)
            }
            rawText += " ###REPEATING_TASK_SUGGESTION### $json"
        } catch (e: Exception) {}
    }
    return ChatMessageEntity(
        id = this.id,
        sender = this.sender.name,
        text = rawText,
        timestamp = this.timestamp,
        isPromoted = this.isPromoted,
        imagePath = this.imagePath,
        audioPath = this.audioPath,
        documentPath = this.documentPath
    )
}
