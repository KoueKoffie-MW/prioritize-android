package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val systemPrompt: String,
    val metadataJson: String = "{}" // Dynamic storage for preferences (e.g. wife's gift ideas)
)
