package com.example.prioritize.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prioritize.MainActivity
import com.example.prioritize.data.SpecialDateType
import com.example.prioritize.data.Task
import com.example.prioritize.data.TaskDatabase
import java.util.Calendar

class PriorityUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PriorityUpdateWorker"
        private const val CHANNEL_ID = "prioritize_critical_alerts"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic priority recalculation and alert check")
        
        val database = TaskDatabase.getDatabase(context)
        val taskDao = database.taskDao()
        
        val activeTasks = taskDao.getActiveTasks()
        val currentTime = System.currentTimeMillis()

        // 1. Check for upcoming Special Dates and inject planning tasks if necessary
        try {
            val specialDates = taskDao.getSpecialDates()
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)

            for (date in specialDates) {
                val dateCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, date.dateMonth - 1)
                    set(Calendar.DAY_OF_MONTH, date.dateDay)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If already passed this year, roll over to next year
                if (dateCal.timeInMillis < currentTime) {
                    dateCal.add(Calendar.YEAR, 1)
                }

                val msRemaining = dateCal.timeInMillis - currentTime
                val daysRemaining = msRemaining / (1000L * 60 * 60 * 24)

                // Determine advance notice days based on event importance
                val advanceDays = when (date.type) {
                    SpecialDateType.ANNIVERSARY -> 45 // 6 weeks (allows booking trips, honeymoons, or big dinners)
                    SpecialDateType.BIRTHDAY    -> 28 // 4 weeks
                    SpecialDateType.MARITAL_CARE -> 7 // 1 week
                }

                if (daysRemaining <= advanceDays) {
                    val taskTitle = "Plan gift/celebration for ${date.name}"
                    val alreadyExists = activeTasks.any { it.title.contains(date.name) }

                    if (!alreadyExists) {
                        // Due date for the planning task is set to 7 days BEFORE the actual special date
                        val planningDeadline = dateCal.timeInMillis - (7L * 24 * 60 * 60 * 1000)
                        
                        val planningTask = Task(
                            title = taskTitle,
                            description = "Event approaches on ${date.dateDay}/${date.dateMonth}. Notes: ${date.notes}",
                            importance = 8,
                            urgency = 6,
                            estimatedMinutes = 60,
                            deadline = planningDeadline,
                            isSoftDeadline = true,
                            graceDays = 3,
                            isScratchPadItem = false // Inject directly into Focus List
                        )
                        taskDao.insertTask(planningTask)
                        Log.d(TAG, "Injected planning task for special date: ${date.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run special dates injection check", e)
        }

        // 2. Alert notifications check
        if (activeTasks.isNotEmpty()) {
            val criticalTasks = activeTasks.filter { task ->
                val score = task.getPriorityScore(currentTime)
                score >= 45.0 // Critical score threshold
            }

            if (criticalTasks.isNotEmpty()) {
                sendCriticalNotification(criticalTasks.size)
            }
        }

        return Result.success()
    }

    private fun sendCriticalNotification(count: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Critical Priorities Pending")
            .setContentText("You have $count high-priority tasks requiring urgent attention.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Notification permission may be missing.", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Critical Alerts"
            val descriptionText = "Notifications for tasks with high priority score thresholds"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
