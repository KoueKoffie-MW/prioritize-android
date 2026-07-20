package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val systemPrompt: String,
    val metadataJson: String = "{}",
    val userAccent: String = "South African Afrikaans",
    val knownSpeakersJson: String = "[]"
)

data class KnownSpeaker(val name: String, val accent: String)
