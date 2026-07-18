package com.example.prioritize.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND isScratchPadItem = 0")
    fun getActiveTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND isScratchPadItem = 0")
    suspend fun getActiveTasks(): List<Task>

    @Query("SELECT * FROM tasks WHERE isScratchPadItem = 1 AND isCompleted = 0")
    fun getScratchPadTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1")
    fun getCompletedTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    // Sub-task operations
    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId")
    fun getSubTasksFlow(taskId: Long): Flow<List<SubTask>>

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId")
    suspend fun getSubTasksForTask(taskId: Long): List<SubTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTasks(subTasks: List<SubTask>)

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    @Query("DELETE FROM sub_tasks WHERE taskId = :taskId")
    suspend fun deleteSubTasksForTask(taskId: Long)

    // UserProfile operations
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfileFlow(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    // Targeted update — preferred over @Insert(REPLACE) which deletes+re-inserts
    @Update
    suspend fun updateUserProfile(profile: UserProfile)

    // RepeatingTask operations
    @Query("SELECT * FROM repeating_tasks")
    fun getRepeatingTasksFlow(): Flow<List<RepeatingTask>>

    @Query("SELECT * FROM repeating_tasks")
    suspend fun getRepeatingTasks(): List<RepeatingTask>

    @Query("SELECT * FROM repeating_tasks WHERE id = :id LIMIT 1")
    suspend fun getRepeatingTaskById(id: Long): RepeatingTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepeatingTask(task: RepeatingTask): Long

    @Update
    suspend fun updateRepeatingTask(task: RepeatingTask)

    @Delete
    suspend fun deleteRepeatingTask(task: RepeatingTask)

    // SpecialDate operations
    @Query("SELECT * FROM special_dates ORDER BY dateMonth ASC, dateDay ASC")
    fun getSpecialDatesFlow(): Flow<List<SpecialDate>>

    @Query("SELECT * FROM special_dates")
    suspend fun getSpecialDates(): List<SpecialDate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecialDate(specialDate: SpecialDate): Long

    @Update
    suspend fun updateSpecialDate(specialDate: SpecialDate)

    @Delete
    suspend fun deleteSpecialDate(specialDate: SpecialDate)

    // MemoryProfile operations
    @Query("SELECT * FROM memory_profiles")
    fun getMemoryProfilesFlow(): Flow<List<MemoryProfile>>

    @Query("SELECT * FROM memory_profiles")
    suspend fun getMemoryProfiles(): List<MemoryProfile>

    @Query("SELECT * FROM memory_profiles WHERE profile_key = :key LIMIT 1")
    suspend fun getMemoryProfileByKey(key: String): MemoryProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryProfile(profile: MemoryProfile): Long

    @Delete
    suspend fun deleteMemoryProfile(profile: MemoryProfile)

    // ObservationLog operations
    @Query("SELECT * FROM observation_logs WHERE is_processed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessedLogs(): List<ObservationLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservationLog(log: ObservationLog): Long

    @Query("UPDATE observation_logs SET is_processed = 1 WHERE id IN (:ids)")
    suspend fun markLogsAsProcessed(ids: List<Long>)

    @Query("DELETE FROM observation_logs WHERE is_processed = 1")
    suspend fun deleteProcessedLogs()
}
