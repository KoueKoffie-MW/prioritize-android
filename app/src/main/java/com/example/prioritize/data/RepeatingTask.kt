package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repeating_tasks")
data class RepeatingTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val importance: Int = 5,
    val urgency: Int = 5,
    val estimatedMinutes: Int = 30,
    // Stored as enum.name by AppTypeConverters — identical to previous String values, no migration needed
    val recurrenceType: RecurrenceType,
    val intervalValue: Int = 1,             // e.g. every 3 (weeks/months) or every 2 (years)
    val preferredDaysOfWeek: String? = null,// Comma-separated indices (1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat)
    val isSoftDeadline: Boolean = true,
    val graceDays: Int = 2,
    val lastCompletedAt: Long? = null,
    val nextDueDate: Long
)
