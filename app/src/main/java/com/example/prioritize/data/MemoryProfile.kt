package com.example.prioritize.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(
    tableName = "memory_profiles",
    indices = [Index(value = ["profile_key"], unique = true)]
)
data class MemoryProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_key") val key: String, // e.g. "angelique"
    @ColumnInfo(name = "title") val title: String, // e.g. "Angelique (Wife)"
    @ColumnInfo(name = "keywords_csv") val keywordsCsv: String, // e.g. "wife,angelique,angie"
    @ColumnInfo(name = "facts_json") val factsJson: String, // JSON array: ["Likes white lilies", "Birthday: Sep 22"]
    @ColumnInfo(name = "last_updated") val lastUpdated: Long
)
