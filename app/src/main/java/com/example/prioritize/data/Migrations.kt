package com.example.prioritize.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database migration objects for the Prioritize app.
 *
 * IMPORTANT: These migrations ensure that the database schema is updated without
 * destroying user data. DO NOT use fallbackToDestructiveMigration() in production.
 *
 * How to add a new migration when you bump the database version:
 * 1. Add a new MIGRATION_X_Y object below following the same pattern.
 * 2. Add the migration to the .addMigrations(...) call in TaskDatabase.
 * 3. Describe the exact SQL changes made to the schema in the migration body.
 */
object Migrations {

    /**
     * Migration from schema version 2 to 3.
     *
     * Uses CREATE TABLE IF NOT EXISTS for all tables that were introduced after the
     * initial v1/v2 schema. This is safe to run on both fresh installs (tables already
     * exist — IF NOT EXISTS is a no-op) and on genuine v2 upgrades (tables get created).
     *
     * ALTER TABLE ADD COLUMN is NOT used here because SQLite does not support
     * IF NOT EXISTS for ADD COLUMN before 3.35.0. Any column additions needed for
     * the tasks table on genuine v2 upgrades will be picked up by MIGRATION_3_4
     * since we use CREATE INDEX IF NOT EXISTS there.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // RepeatingTask table — likely added after v2
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `repeating_tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL DEFAULT '',
                    `importance` INTEGER NOT NULL DEFAULT 5,
                    `urgency` INTEGER NOT NULL DEFAULT 5,
                    `estimatedMinutes` INTEGER NOT NULL DEFAULT 30,
                    `recurrenceType` TEXT NOT NULL,
                    `intervalValue` INTEGER NOT NULL DEFAULT 1,
                    `preferredDaysOfWeek` TEXT,
                    `isSoftDeadline` INTEGER NOT NULL DEFAULT 1,
                    `graceDays` INTEGER NOT NULL DEFAULT 2,
                    `lastCompletedAt` INTEGER,
                    `nextDueDate` INTEGER NOT NULL
                )
            """.trimIndent())

            // SpecialDate table — likely added after v2
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `special_dates` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `dateMonth` INTEGER NOT NULL,
                    `dateDay` INTEGER NOT NULL,
                    `type` TEXT NOT NULL,
                    `notes` TEXT NOT NULL DEFAULT '',
                    `lastTriggeredYear` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // MemoryProfile table — likely added after v2
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `memory_profiles` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `profile_key` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `keywords_csv` TEXT NOT NULL,
                    `facts_json` TEXT NOT NULL,
                    `last_updated` INTEGER NOT NULL
                )
            """.trimIndent())

            // ObservationLog table — likely added after v2
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `observation_logs` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `description` TEXT NOT NULL,
                    `is_processed` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /**
     * Migration from schema version 3 to 4.
     *
     * No SQL column changes were made in this version bump.
     * The version was incremented because adding @TypeConverters(AppTypeConverters::class)
     * to TaskDatabase changes Room's computed identity hash, which would cause an
     * IllegalStateException on existing installs. The migration body is intentionally
     * empty — Room will update the stored hash automatically after this migration runs.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // These @Index annotations were declared in entity classes but never created
            // via a migration script. Any install that went through MIGRATION_2_3 (empty)
            // is missing these indices. CREATE INDEX IF NOT EXISTS is safe to run even
            // if the index already exists on a fresh install.

            // Task.repeatingTaskId — declared in @Entity(indices = [...])
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_tasks_repeatingTaskId ON tasks (repeatingTaskId)"
            )

            // SubTask.taskId — required for ForeignKey performance + @Index declaration
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sub_tasks_taskId ON sub_tasks (taskId)"
            )

            // MemoryProfile.profile_key — unique index declared in @Entity
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_memory_profiles_profile_key ON memory_profiles (profile_key)"
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add originalYear column to special_dates
            db.execSQL("ALTER TABLE special_dates ADD COLUMN originalYear INTEGER")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `sender` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `isPromoted` INTEGER NOT NULL DEFAULT 0,
                    `imagePath` TEXT,
                    `audioPath` TEXT
                )
            """.trimIndent())
            
            // Add userAccent and knownSpeakersJson to user_profile
            db.execSQL("ALTER TABLE user_profile ADD COLUMN userAccent TEXT NOT NULL DEFAULT 'South African Afrikaans'")
            db.execSQL("ALTER TABLE user_profile ADD COLUMN knownSpeakersJson TEXT NOT NULL DEFAULT '[]'")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN documentPath TEXT")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages (timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_observation_logs_is_processed_timestamp ON observation_logs (is_processed, timestamp)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN thought TEXT")
        }
    }
}
