package com.cpamp.mobile.ui.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cpamp.mobile.data.widget.UsageWidgetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsageWidgetEntryPoint {
    fun usageWidgetRepository(): UsageWidgetRepository
}

internal fun Context.usageWidgetRepository(): UsageWidgetRepository =
    EntryPointAccessors.fromApplication(applicationContext, UsageWidgetEntryPoint::class.java)
        .usageWidgetRepository()

class UsageWidgetWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        applicationContext.usageWidgetRepository().refresh()
        UsageWidget.reload(applicationContext)
        return Result.success()
    }
}

internal object UsageWidgetWork {
    private const val REFRESH_WORK = "usage-widget-refresh"
    private const val PERIODIC_WORK = "usage-widget-periodic"
    private const val PERIODIC_INTERVAL_MINUTES = 15L

    // Background jobs only get network access when they declare a network constraint.
    private val networkConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun requestRefresh(context: Context) {
        context.usageWidgetRepository().markRefreshRequested()
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH_WORK,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<UsageWidgetWorker>().setConstraints(networkConstraints).build(),
        )
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UsageWidgetWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(networkConstraints)
                .build(),
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(REFRESH_WORK)
        }
    }
}
