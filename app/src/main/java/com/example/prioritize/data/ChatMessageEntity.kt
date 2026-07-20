package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long,
    val isPromoted: Boolean = false,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val documentPath: String? = null
)
