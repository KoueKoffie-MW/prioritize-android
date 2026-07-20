package com.example.prioritize.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// NOTE: exportSchema = true writes a schema JSON snapshot to the schemas/ directory at build time.
// This is required for safe migration testing. Configure the output directory in build.gradle.kts:
//   ksp { arg("room.schemaLocation", "$projectDir/schemas") }
@Database(
    entities = [Task::class, SubTask::class, UserProfile::class, RepeatingTask::class, SpecialDate::class, MemoryProfile::class, ObservationLog::class, ChatMessageEntity::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(AppTypeConverters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "prioritize_database"
                )
                // IMPORTANT: Using explicit migrations instead of fallbackToDestructiveMigration()
                // to prevent silent data loss on schema changes. See Migrations.kt for details.
                // When upgrading the database version, always add a new Migration object in Migrations.kt
                // and register it here BEFORE removing the old migration for backward compatibility.
                .addMigrations(
                    Migrations.MIGRATION_2_3,
                    Migrations.MIGRATION_3_4,
                    Migrations.MIGRATION_4_5,
                    Migrations.MIGRATION_5_6,
                    Migrations.MIGRATION_6_7,
                    Migrations.MIGRATION_7_8,
                    Migrations.MIGRATION_8_9,
                    Migrations.MIGRATION_9_10
                )
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

