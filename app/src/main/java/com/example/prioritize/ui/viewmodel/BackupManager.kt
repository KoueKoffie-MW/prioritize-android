package com.example.prioritize.ui.viewmodel

import android.util.Log
import com.example.prioritize.data.RecurrenceType
import com.example.prioritize.data.RepeatingTask
import com.example.prioritize.data.SpecialDate
import com.example.prioritize.data.SpecialDateType
import com.example.prioritize.data.Task
import com.example.prioritize.data.TaskRepository
import com.example.prioritize.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles all JSON backup/restore and ZIP export operations.
 *
 * Extracted from TaskViewModel to reduce its size and enable independent testing.
 * All functions are suspend functions — callers must run them on an appropriate dispatcher
 * (use withContext(Dispatchers.IO) or viewModelScope.launch(Dispatchers.IO)).
 *
 * The ViewModel delegates to this object via thin wrapper methods that provide the
 * coroutine scope and call site (BrainScreen export/import buttons).
 */
object BackupManager {

    // ─────────────────────────────────────────────────────────────
    //  JSON Backup (single-file export)
    // ─────────────────────────────────────────────────────────────

    /**
     * Exports all database tables to a single JSON file written to [outputStream].
     * Includes: tasks, repeating_tasks, special_dates, user_profile.
     * Does NOT close the stream — callers must close it.
     */
    suspend fun exportDatabase(outputStream: OutputStream, repository: TaskRepository) =
        withContext(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val json = JSONObject()

                    // Tasks (active + completed + scratch pad)
                    val tasksArray = JSONArray()
                    repository.getActiveTasks().forEach { tasksArray.put(taskToJson(it)) }
                    repository.completedTasksFlow.first().forEach { tasksArray.put(taskToJson(it)) }
                    repository.scratchPadTasksFlow.first().forEach { tasksArray.put(taskToJson(it)) }
                    json.put("tasks", tasksArray)

                    // Repeating Tasks
                    val repArray = JSONArray()
                    repository.getRepeatingTasks().forEach {
                        repArray.put(JSONObject().apply {
                            put("title", it.title)
                            put("description", it.description)
                            put("importance", it.importance)
                            put("urgency", it.urgency)
                            put("estimatedMinutes", it.estimatedMinutes)
                            put("recurrenceType", it.recurrenceType)
                            put("intervalValue", it.intervalValue)
                            put("preferredDaysOfWeek", it.preferredDaysOfWeek ?: "")
                            put("isSoftDeadline", it.isSoftDeadline)
                            put("graceDays", it.graceDays)
                            put("nextDueDate", it.nextDueDate)
                        })
                    }
                    json.put("repeating_tasks", repArray)

                    // Special Dates
                    val datesArray = JSONArray()
                    repository.getSpecialDates().forEach {
                        datesArray.put(JSONObject().apply {
                            put("name", it.name)
                            put("dateMonth", it.dateMonth)
                            put("dateDay", it.dateDay)
                            put("type", it.type)
                            put("notes", it.notes)
                        })
                    }
                    json.put("special_dates", datesArray)

                    // User Profile
                    repository.getUserProfile()?.let { profile ->
                        json.put("user_profile", JSONObject().apply {
                            put("systemPrompt", profile.systemPrompt)
                            put("metadataJson", profile.metadataJson)
                        })
                    }

                    outputStream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                    outputStream.close()
                } catch (e: Exception) {
                    Log.e("BackupManager", "JSON export failed", e)
                }
            }
        }

    // ─────────────────────────────────────────────────────────────
    //  Agent ZIP Export
    // ─────────────────────────────────────────────────────────────

    /**
     * Exports all database tables as individual JSON files plus an agents.md context
     * handbook bundled into a ZIP archive written to [outputStream].
     */
    suspend fun exportAgentHandbook(outputStream: OutputStream, repository: TaskRepository) =
        withContext(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    val zipStream = java.util.zip.ZipOutputStream(outputStream)

                    // 1. user_profile.json
                    repository.getUserProfile()?.let { profile ->
                        writeZipEntry(zipStream, "user_profile.json", JSONObject().apply {
                            put("systemPrompt", profile.systemPrompt)
                            put("metadataJson", profile.metadataJson)
                        }.toString(2))
                    }

                    // 2. memory_profiles.json
                    val mpArray = JSONArray()
                    repository.getMemoryProfiles().forEach { mp ->
                        mpArray.put(JSONObject().apply {
                            put("key", mp.key)
                            put("title", mp.title)
                            put("keywordsCsv", mp.keywordsCsv)
                            put("factsJson", mp.factsJson)
                            put("lastUpdated", mp.lastUpdated)
                        })
                    }
                    writeZipEntry(zipStream, "memory_profiles.json", mpArray.toString(2))

                    // 3. special_dates.json
                    val sdArray = JSONArray()
                    repository.getSpecialDates().forEach { sd ->
                        sdArray.put(JSONObject().apply {
                            put("name", sd.name)
                            put("dateMonth", sd.dateMonth)
                            put("dateDay", sd.dateDay)
                            put("type", sd.type)
                            put("notes", sd.notes)
                        })
                    }
                    writeZipEntry(zipStream, "special_dates.json", sdArray.toString(2))

                    // 4. repeating_tasks.json
                    val rtArray = JSONArray()
                    repository.getRepeatingTasks().forEach { rt ->
                        rtArray.put(JSONObject().apply {
                            put("title", rt.title)
                            put("description", rt.description)
                            put("importance", rt.importance)
                            put("urgency", rt.urgency)
                            put("estimatedMinutes", rt.estimatedMinutes)
                            put("recurrenceType", rt.recurrenceType)
                            put("intervalValue", rt.intervalValue)
                            put("preferredDaysOfWeek", rt.preferredDaysOfWeek ?: "")
                            put("isSoftDeadline", rt.isSoftDeadline)
                            put("graceDays", rt.graceDays)
                            put("nextDueDate", rt.nextDueDate)
                        })
                    }
                    writeZipEntry(zipStream, "repeating_tasks.json", rtArray.toString(2))

                    // 5. tasks.json
                    val tasksArray = JSONArray()
                    repository.getActiveTasks().forEach { tasksArray.put(taskToJson(it)) }
                    repository.completedTasksFlow.first().forEach { tasksArray.put(taskToJson(it)) }
                    repository.scratchPadTasksFlow.first().forEach { tasksArray.put(taskToJson(it)) }
                    writeZipEntry(zipStream, "tasks.json", tasksArray.toString(2))

                    // 6. agents.md context handbook
                    writeZipEntry(zipStream, "agents.md", agentsHandbookMd())

                    zipStream.close()
                } catch (e: Exception) {
                    Log.e("BackupManager", "ZIP export failed", e)
                }
            }
        }

    // ─────────────────────────────────────────────────────────────
    //  JSON Restore (import)
    // ─────────────────────────────────────────────────────────────

    /**
     * Imports all database tables from a JSON backup [inputStream].
     * Existing rows are replaced via @Insert(REPLACE) semantics.
     * Sub-tasks and memory profiles are NOT included in the JSON backup format
     * (use the Agent ZIP for full exports).
     */
    suspend fun importDatabase(inputStream: InputStream, repository: TaskRepository) =
        withContext(Dispatchers.IO) {
            try {
                // bufferedReader().readText() is correct for content URIs —
                // inputStream.available() only returns currently-buffered bytes, not the full size.
                val json = JSONObject(inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })

                // Tasks
                if (json.has("tasks")) {
                    val tasks = json.getJSONArray("tasks")
                    for (i in 0 until tasks.length()) {
                        val obj = tasks.getJSONObject(i)
                        repository.insertTask(
                            Task(
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                importance = obj.optInt("importance", 5),
                                urgency = obj.optInt("urgency", 5),
                                estimatedMinutes = obj.optInt("estimatedMinutes", 15),
                                deadline = if (obj.isNull("deadline")) null else obj.getLong("deadline"),
                                isSoftDeadline = obj.optBoolean("isSoftDeadline", true),
                                graceDays = obj.optInt("graceDays", 2),
                                isScratchPadItem = obj.optBoolean("isScratchPadItem", false),
                                isCompleted = obj.optBoolean("isCompleted", false),
                                completedAt = if (obj.isNull("completedAt")) null else obj.getLong("completedAt")
                            )
                        )
                    }
                }

                // Repeating Tasks
                if (json.has("repeating_tasks")) {
                    val rep = json.getJSONArray("repeating_tasks")
                    for (i in 0 until rep.length()) {
                        val obj = rep.getJSONObject(i)
                        repository.insertRepeatingTask(
                            RepeatingTask(
                                title = obj.getString("title"),
                                description = obj.optString("description", ""),
                                importance = obj.optInt("importance", 5),
                                urgency = obj.optInt("urgency", 5),
                                estimatedMinutes = obj.optInt("estimatedMinutes", 30),
                                // Parse safely — fallback to DAILY for any unknown legacy value
                                recurrenceType = RecurrenceType.entries.find {
                                    it.name == obj.getString("recurrenceType")
                                } ?: RecurrenceType.DAILY,
                                intervalValue = obj.optInt("intervalValue", 1),
                                preferredDaysOfWeek = obj.optString("preferredDaysOfWeek", "")
                                    .takeIf { it.isNotBlank() },
                                isSoftDeadline = obj.optBoolean("isSoftDeadline", true),
                                graceDays = obj.optInt("graceDays", 2),
                                nextDueDate = obj.getLong("nextDueDate")
                            )
                        )
                    }
                }

                // Special Dates
                if (json.has("special_dates")) {
                    val dates = json.getJSONArray("special_dates")
                    for (i in 0 until dates.length()) {
                        val obj = dates.getJSONObject(i)
                        repository.insertSpecialDate(
                            SpecialDate(
                                name = obj.getString("name"),
                                dateMonth = obj.getInt("dateMonth"),
                                dateDay = obj.getInt("dateDay"),
                                // Parse safely — fallback to BIRTHDAY for any unknown legacy value
                                type = SpecialDateType.entries.find {
                                    it.name == obj.getString("type")
                                } ?: SpecialDateType.BIRTHDAY,
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                }

                // User Profile
                if (json.has("user_profile")) {
                    val obj = json.getJSONObject("user_profile")
                    repository.insertUserProfile(
                        UserProfile(
                            id = 1,
                            systemPrompt = obj.getString("systemPrompt"),
                            metadataJson = obj.optString("metadataJson", "{}")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("BackupManager", "JSON import failed", e)
            }
        }

    // ─────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────

    internal fun taskToJson(task: Task): JSONObject = JSONObject().apply {
        put("title", task.title)
        put("description", task.description)
        put("importance", task.importance)
        put("urgency", task.urgency)
        put("estimatedMinutes", task.estimatedMinutes)
        put("deadline", task.deadline ?: JSONObject.NULL)
        put("isSoftDeadline", task.isSoftDeadline)
        put("graceDays", task.graceDays)
        put("isScratchPadItem", task.isScratchPadItem)
        put("isCompleted", task.isCompleted)
        put("completedAt", task.completedAt ?: JSONObject.NULL)
    }

    private fun writeZipEntry(zip: java.util.zip.ZipOutputStream, filename: String, content: String) {
        zip.putNextEntry(java.util.zip.ZipEntry(filename))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun agentsHandbookMd(): String = """
        # Prioritize Agent Context & Handbook

        Welcome, Agent. You are loaded with Jan's ADHD Second Brain context.
        Below is the specification of the files included in this archive and instructions on how to use them.

        ## Files Included
        *   `user_profile.json`: Contains the baseline ADHD coach system prompt and active configurations.
        *   `memory_profiles.json`: Contains long-term memory facts extracted about people, relationships, and routines.
        *   `special_dates.json`: Core special events (birthdays, anniversaries).
        *   `tasks.json`: Raw dump of active, scratchpad, and completed tasks.
        *   `repeating_tasks.json`: Rescheduling rules for recurring actions.

        ## The Priority Score Math
        Jan's tasks are automatically ranked using the following formula:
        Score = 3 * Importance + 2 * Urgency + UrgencySpike + DopamineBonus
        Where:
        *   Base = 3 * Importance + 2 * Urgency
        *   UrgencySpike = (72 / (SlackTime + 6)) * 5.0 (with SlackTime in hours until task deadline)
        *   DopamineBonus = +10.0 if duration <= 15 min, else +5.0 if duration <= 60 min.

        Use this mathematical formula to calculate, rank, or audit task lists.

        ## Agent Guidelines
        *   **Tone:** Highly candid, direct, and pragmatic. Do not be default-agreeable.
        *   **ADHD Support:** Group tasks logically, break them down into micro-steps, and use bold visual anchors for rapid scanning.
    """.trimIndent()
}
