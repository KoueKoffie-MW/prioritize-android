package com.example.prioritize

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.prioritize.worker.PriorityUpdateWorker
import java.util.concurrent.TimeUnit

class PrioritizeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        schedulePeriodicUpdates()
    }

    private fun schedulePeriodicUpdates() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<PriorityUpdateWorker>(
            2, TimeUnit.HOURS // Recalculate and notify every 2 hours
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PriorityRecalculator",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }
}
