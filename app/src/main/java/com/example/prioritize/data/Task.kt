package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.Index

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["repeatingTaskId"])]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val importance: Int = 5,              // 1 to 10
    val urgency: Int = 5,                 // 1 to 10
    val estimatedMinutes: Int = 15,       // Z-axis Duration
    val deadline: Long? = null,           // Epoch timestamp in milliseconds
    val isSoftDeadline: Boolean = true,
    val graceDays: Int = 2,
    val repeatingTaskId: Long? = null,    // References the parent RepeatingTask if applicable
    val isScratchPadItem: Boolean = true, // True if dumped and not yet processed
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    // Dynamic recalculation of 3D priority score
    fun getPriorityScore(currentTime: Long = System.currentTimeMillis()): Double {
        val importanceWeight = 3.0
        val urgencyWeight = 2.0
        
        val baseScore = (importanceWeight * importance) + (urgencyWeight * urgency)
        
        val durationHours = estimatedMinutes.toDouble() / 60.0
        
        var deadlineScore = 0.0
        var slackTimeHours = Double.MAX_VALUE
        
        if (deadline != null) {
            val effectiveDeadline = if (isSoftDeadline) {
                deadline + (graceDays.toLong() * 24L * 60L * 60L * 1000L)
            } else {
                deadline
            }
            
            val timeRemainingHours = (effectiveDeadline - currentTime).toDouble() / (1000.0 * 60.0 * 60.0)
            slackTimeHours = timeRemainingHours - durationHours
            
            deadlineScore = if (slackTimeHours <= 0) {
                60.0 // Maximum urgency penalty if slack time is exhausted
            } else {
                val calculated = (72.0 / (slackTimeHours + 6.0)) * 5.0
                calculated.coerceAtLeast(0.0)
            }
        }
        
        // Dopamine quick win bonus for short tasks when not in a critical crunch
        val isNotUrgent = deadline == null || slackTimeHours > 24.0
        val dopamineBonus = if (isNotUrgent) {
            if (estimatedMinutes <= 15) 10.0
            else if (estimatedMinutes <= 60) 5.0
            else 0.0
        } else {
            0.0
        }
        
        return baseScore + deadlineScore + dopamineBonus
    }
}
