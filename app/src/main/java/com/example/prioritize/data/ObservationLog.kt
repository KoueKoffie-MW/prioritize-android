package com.example.prioritize.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "observation_logs")
data class ObservationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "is_processed") val isProcessed: Boolean = false
)
