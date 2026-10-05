package com.kairo.app.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Keeps "Next: X in n min" roughly current. 15 minutes is WorkManager's minimum period, and
 * AppWidget's own updatePeriodMillis can't go below 30, so WorkManager does the ticking.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        KairoWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "kairo-widget-refresh"
        private const val PERIOD_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Re-arms the periodic refresh on app start if a widget is on the home screen (e.g. after an update). */
        suspend fun ensureScheduledIfPlaced(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(KairoWidget::class.java).isNotEmpty()) schedule(context)
        }
    }
}
