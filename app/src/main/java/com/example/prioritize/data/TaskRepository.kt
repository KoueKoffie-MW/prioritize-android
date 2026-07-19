package com.example.prioritize.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val activeTasksFlow: Flow<List<Task>> = taskDao.getActiveTasksFlow()
    val scratchPadTasksFlow: Flow<List<Task>> = taskDao.getScratchPadTasksFlow()
    val completedTasksFlow: Flow<List<Task>> = taskDao.getCompletedTasksFlow()

    suspend fun getTaskById(taskId: Long): Task? = taskDao.getTaskById(taskId)
    suspend fun getActiveTasks(): List<Task> = taskDao.getActiveTasks()

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
    suspend fun deleteAllCompletedTasks() = taskDao.deleteAllCompletedTasks()

    // Sub-tasks
    fun getSubTasksFlow(taskId: Long): Flow<List<SubTask>> = taskDao.getSubTasksFlow(taskId)
    suspend fun getSubTasksForTask(taskId: Long): List<SubTask> = taskDao.getSubTasksForTask(taskId)
    suspend fun insertSubTasks(subTasks: List<SubTask>) = taskDao.insertSubTasks(subTasks)
    suspend fun updateSubTask(subTask: SubTask) = taskDao.updateSubTask(subTask)
    suspend fun deleteSubTask(subTask: SubTask) = taskDao.deleteSubTask(subTask)
    suspend fun deleteSubTasksForTask(taskId: Long) = taskDao.deleteSubTasksForTask(taskId)

    // UserProfile
    val userProfileFlow: Flow<UserProfile?> = taskDao.getUserProfileFlow()
    suspend fun getUserProfile(): UserProfile? = taskDao.getUserProfile()
    suspend fun insertUserProfile(profile: UserProfile) = taskDao.insertUserProfile(profile)
    // Targeted update — does not delete+reinsert, preserving rowid and avoiding FK cascades
    suspend fun updateUserProfile(profile: UserProfile) = taskDao.updateUserProfile(profile)

    // RepeatingTasks
    val repeatingTasksFlow: Flow<List<RepeatingTask>> = taskDao.getRepeatingTasksFlow()
    suspend fun getRepeatingTasks(): List<RepeatingTask> = taskDao.getRepeatingTasks()
    suspend fun getRepeatingTaskById(id: Long): RepeatingTask? = taskDao.getRepeatingTaskById(id)
    suspend fun insertRepeatingTask(task: RepeatingTask): Long = taskDao.insertRepeatingTask(task)
    suspend fun updateRepeatingTask(task: RepeatingTask) = taskDao.updateRepeatingTask(task)
    suspend fun deleteRepeatingTask(task: RepeatingTask) = taskDao.deleteRepeatingTask(task)

    // SpecialDates
    val specialDatesFlow: Flow<List<SpecialDate>> = taskDao.getSpecialDatesFlow()
    suspend fun getSpecialDates(): List<SpecialDate> = taskDao.getSpecialDates()
    suspend fun insertSpecialDate(specialDate: SpecialDate): Long = taskDao.insertSpecialDate(specialDate)
    suspend fun updateSpecialDate(specialDate: SpecialDate) = taskDao.updateSpecialDate(specialDate)
    suspend fun deleteSpecialDate(specialDate: SpecialDate) = taskDao.deleteSpecialDate(specialDate)

    // MemoryProfiles
    val memoryProfilesFlow: Flow<List<MemoryProfile>> = taskDao.getMemoryProfilesFlow()
    suspend fun getMemoryProfiles(): List<MemoryProfile> = taskDao.getMemoryProfiles()
    suspend fun getMemoryProfileByKey(key: String): MemoryProfile? = taskDao.getMemoryProfileByKey(key)
    suspend fun insertMemoryProfile(profile: MemoryProfile): Long = taskDao.insertMemoryProfile(profile)
    suspend fun deleteMemoryProfile(profile: MemoryProfile) = taskDao.deleteMemoryProfile(profile)

    // ObservationLogs
    suspend fun getUnprocessedLogs(): List<ObservationLog> = taskDao.getUnprocessedLogs()
    suspend fun insertObservationLog(log: ObservationLog): Long = taskDao.insertObservationLog(log)
    suspend fun markLogsAsProcessed(ids: List<Long>) = taskDao.markLogsAsProcessed(ids)
    suspend fun deleteProcessedLogs() = taskDao.deleteProcessedLogs()
}
