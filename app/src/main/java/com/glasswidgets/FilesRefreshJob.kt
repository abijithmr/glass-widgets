package com.glasswidgets

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore
import java.io.File

/**
 * Keeps the Files widget fresh without polling. Android wakes this job only when its
 * file index changes (a download finishes, a photo is taken, a file is deleted). The job
 * then checks whether the folder each widget is showing really changed and reloads only
 * those widgets. With no Files widget on the home screen nothing is scheduled at all.
 */
class FilesRefreshJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val context = applicationContext
        if (FilesRefresh.hasWidgets(context)) {
            // A content-trigger job runs once, so queue the next one first and no change is missed.
            FilesRefresh.schedule(context)
            FilesRefresh.reloadChanged(context)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}

object FilesRefresh {

    private const val JOB_ID = 4201

    /** Wait for changes to settle, but never hold a reload back longer than this. */
    private const val SETTLE_MS = 3_000L
    private const val MAX_WAIT_MS = 30_000L

    private fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, GlassFilesWidget::class.java))

    fun hasWidgets(context: Context): Boolean = widgetIds(context).isNotEmpty()

    /** Queue the change watcher unless one is already waiting. */
    fun ensureScheduled(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) == null) schedule(context)
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val trigger = JobInfo.TriggerContentUri(
            MediaStore.Files.getContentUri("external"),
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
        )
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, FilesRefreshJob::class.java))
            .addTriggerContentUri(trigger)
            .setTriggerContentUpdateDelay(SETTLE_MS)
            .setTriggerContentMaxDelay(MAX_WAIT_MS)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }

    /** Reload the widgets whose current folder changed since it was last listed. */
    fun reloadChanged(context: Context) {
        if (!FilesStore.hasAccess(context)) return
        val manager = AppWidgetManager.getInstance(context)
        for (id in widgetIds(context)) {
            val path = FilesStore.currentPath(context, id)
            if (File(path).lastModified() != FilesStore.listedStamp(context, id)) {
                manager.notifyAppWidgetViewDataChanged(id, R.id.files_grid)
            }
        }
    }
}
