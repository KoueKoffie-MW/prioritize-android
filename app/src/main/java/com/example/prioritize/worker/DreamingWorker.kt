package com.example.prioritize.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prioritize.ai.Gemma4Parser
import com.example.prioritize.data.MemoryProfile
import com.example.prioritize.data.ObservationLog
import com.example.prioritize.data.TaskDatabase
import com.example.prioritize.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DreamingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("DreamingWorker", "Dreaming background consolidation worker started.")

        val database = TaskDatabase.getDatabase(applicationContext)
        val repository = TaskRepository(database.taskDao(), database.chatMessageDao())
        val parser = Gemma4Parser(applicationContext)

        val unprocessedLogs = repository.getUnprocessedLogs()
        if (unprocessedLogs.isEmpty()) {
            Log.i("DreamingWorker", "No new observations to consolidate. Dreaming finished.")
            return@withContext Result.success()
        }

        Log.d("DreamingWorker", "Found ${unprocessedLogs.size} unprocessed observations.")

        // Chunk observations in groups of 5 to protect LLM context length and prevent memory failures
        val chunkSize = 5
        val chunks = unprocessedLogs.chunked(chunkSize)

        for (chunk in chunks) {
            val observationsText = chunk.joinToString("\n") { "- ${it.description}" }
            val prompt = """
                You are a memory consolidation engine (Dreaming phase).
                Read the log of recent user actions and completed tasks below.
                Identify any new facts, relationships, birthdays, preferences, routines, or chore details.
                Respond ONLY with a valid JSON array matching this schema:
                [
                  {
                    "key": "unique_subject_key",
                    "title": "Display Title",
                    "keywords": "comma,separated,aliases",
                    "new_facts": ["fact string 1", "fact string 2"]
                  }
                ]
                
                Observations:
                $observationsText
            """.trimIndent()

            val response = parser.runRawInference(prompt)
            if (response.isNullOrBlank()) {
                Log.w("DreamingWorker", "Model returned empty response for chunk. Retrying next chunk.")
                continue
            }

            try {
                val startIndex = response.indexOf('[')
                val endIndex = response.lastIndexOf(']')
                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    val jsonStr = response.substring(startIndex, endIndex + 1)
                    val jsonArray = JSONArray(jsonStr)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val key = obj.getString("key").trim().lowercase()
                        val title = obj.getString("title").trim()
                        val keywords = obj.getString("keywords").trim()
                        val newFactsArr = obj.getJSONArray("new_facts")

                        val existing = repository.getMemoryProfileByKey(key)
                        val mergedFacts = mutableListOf<String>()
                        if (existing != null) {
                            try {
                                val currentArr = JSONArray(existing.factsJson)
                                for (j in 0 until currentArr.length()) {
                                    mergedFacts.add(currentArr.getString(j))
                                }
                            } catch (e: Exception) {
                                // Log instead of silently swallowing — factsJson may be malformed
                                Log.w("DreamingWorker", "Failed to parse existing factsJson for key='$key': ${e.message}")
                            }
                        }

                        for (j in 0 until newFactsArr.length()) {
                            val fact = newFactsArr.getString(j).trim()
                            if (fact.isNotEmpty() && !mergedFacts.any { it.equals(fact, ignoreCase = true) }) {
                                mergedFacts.add(fact)
                            }
                        }

                        // Merge keywords as a deduplicated set to prevent unbounded CSV growth
                        // across repeated Dreaming cycles.
                        val mergedKeywords: String = buildSet {
                            existing?.keywordsCsv?.split(",")?.forEach { add(it.trim()) }
                            keywords.split(",").forEach { add(it.trim()) }
                        }.filter { it.isNotEmpty() }.joinToString(",")

                        val factsJsonStr = JSONArray(mergedFacts).toString()
                        val profileToSave = MemoryProfile(
                            id = existing?.id ?: 0,
                            key = key,
                            title = title,
                            keywordsCsv = mergedKeywords,
                            factsJson = factsJsonStr,
                            lastUpdated = System.currentTimeMillis()
                        )
                        repository.insertMemoryProfile(profileToSave)
                        Log.d("DreamingWorker", "Consolidated facts for: $key")
                    }
                }
            } catch (e: Exception) {
                Log.e("DreamingWorker", "Failed to parse chunk consolidation JSON response: $response", e)
            }
        }

        // Mark all processed log entries as completed
        val processedIds = unprocessedLogs.map { it.id }
        repository.markLogsAsProcessed(processedIds)
        repository.deleteProcessedLogs() // Keep database lean

        Log.i("DreamingWorker", "Dreaming consolidation completed successfully.")
        Result.success()
    }
}
