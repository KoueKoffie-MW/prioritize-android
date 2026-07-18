package com.example.prioritize.ui.viewmodel

import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.Task
import com.example.prioritize.data.RepeatingTask

/** Type-safe sender identifier for chat messages. Replaces the stringly-typed "USER"/"AI" pattern. */
enum class MessageSender { USER, AI }

data class ChatMessage(
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionTask: Task? = null,
    val actionSpecialDate: SpecialDate? = null,
    val actionRepeatingTask: RepeatingTask? = null
)
