package com.example.prioritize.data

import androidx.room.TypeConverter

/**
 * Type-safe enum for repeating task recurrence patterns.
 *
 * Replaces the stringly-typed `recurrenceType: String` field in [RepeatingTask].
 * Room stores the enum as its [name] string (e.g. "DAILY", "WEEKLY") which is
 * **identical to the values previously stored as raw strings** — so NO database
 * migration is required when switching from String to this enum.
 *
 * To add a new recurrence type: add it here. The TypeConverter handles serialisation.
 */
enum class RecurrenceType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM_DAYS
}

/**
 * Type-safe enum for special date categories.
 *
 * Replaces the stringly-typed `type: String` field in [SpecialDate].
 * Same no-migration guarantee as [RecurrenceType] — stored values are identical.
 */
enum class SpecialDateType {
    BIRTHDAY,
    ANNIVERSARY,
    MARITAL_CARE
}

/**
 * Room TypeConverters for [RecurrenceType] and [SpecialDateType].
 *
 * Registered via @TypeConverters on [TaskDatabase].
 * Uses `.name` / `valueOf()` to ensure the stored string is identical to the
 * previous raw string values, preserving existing data without a migration.
 */
class AppTypeConverters {

    @TypeConverter
    fun recurrenceTypeToString(type: RecurrenceType): String = type.name

    @TypeConverter
    fun stringToRecurrenceType(value: String): RecurrenceType =
        RecurrenceType.entries.find { it.name == value }
            ?: RecurrenceType.DAILY // Safe fallback for any unexpected legacy value

    @TypeConverter
    fun specialDateTypeToString(type: SpecialDateType): String = type.name

    @TypeConverter
    fun stringToSpecialDateType(value: String): SpecialDateType =
        SpecialDateType.entries.find { it.name == value }
            ?: SpecialDateType.BIRTHDAY // Safe fallback
}
