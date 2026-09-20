package de.szalkowski.activitylauncher.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object KnowledgeSyncScheduler {
    private const val PERIODIC_NAME = "sam-knowledge-periodic-refresh"
    private const val STARTUP_NAME = "sam-knowledge-startup-refresh"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val manager = WorkManager.getInstance(appContext)
        manager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<KnowledgeSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
        manager.enqueueUniqueWork(
            STARTUP_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<KnowledgeSyncWorker>()
                .setConstraints(constraints)
                .build(),
        )
    }
}
