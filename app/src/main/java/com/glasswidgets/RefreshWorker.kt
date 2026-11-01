package com.glasswidgets

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Keeps the weather and battery widgets fresh in the background. */
class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext
        var ok = true
        if (WeatherRepository.savedPlace(context) != null) {
            ok = WeatherRepository.refresh(context)
        }
        Glass.refresh(context, GlassWeatherWidget::class.java)
        Glass.refresh(context, GlassWeatherCardWidget::class.java)
        Glass.refresh(context, GlassBatteryWidget::class.java)
        Glass.refresh(context, GlassRechargeWidget::class.java)
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "glass_refresh_periodic"
        private const val NOW = "glass_refresh_now"

        /** Every 15 minutes (the shortest interval Android allows). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One refresh as soon as there is a network connection. */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        /** Stops the background refresh once no weather or battery widget is left. */
        fun cancelIfUnused(context: Context) {
            val inUse = Glass.hasInstances(context, GlassWeatherWidget::class.java) ||
                Glass.hasInstances(context, GlassWeatherCardWidget::class.java) ||
                Glass.hasInstances(context, GlassBatteryWidget::class.java) ||
                Glass.hasInstances(context, GlassRechargeWidget::class.java)
            if (!inUse) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            }
        }
    }
}
