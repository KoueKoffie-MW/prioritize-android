package com.example.prioritize.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "observation_logs",
    indices = [Index(value = ["is_processed", "timestamp"])]
)
data class ObservationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "is_processed") val isProcessed: Boolean = false
)
