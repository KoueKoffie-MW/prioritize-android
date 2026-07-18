package com.example.prioritize.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "special_dates")
data class SpecialDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateMonth: Int,                     // 1-12
    val dateDay: Int,                       // 1-31
    // Stored as enum.name by AppTypeConverters — identical to previous String values, no migration needed
    val type: SpecialDateType,
    val notes: String = "",                 // Gift suggestions or general preferences
    val originalYear: Int? = null,          // Year of original event (e.g. birth year)
    val lastTriggeredYear: Int = 0
)
