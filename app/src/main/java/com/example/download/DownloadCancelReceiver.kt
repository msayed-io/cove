package com.example.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.api.NetworkModule

/**
 * Handles the "إلغاء" action of the ongoing download notification.
 *
 * A dedicated receiver (instead of WorkManager's generic cancel intent) lets us mark
 * the shared item as cancelled *before* the engine tears down, so the UI never shows
 * a misleading "فشل التحميل" state for a download the user cancelled on purpose.
 */
class DownloadCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadWorker.KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return

        try {
            val repository = NetworkModule.downloadRepository
            repository.markCancelledByUser(downloadId)
            repository.stopActiveNetworkTask(downloadId)
            repository.markDownloadStatus(downloadId, "تم إلغاء التنزيل")
            DownloadScheduler.cancel(context, downloadId)
            DownloadNotifications.cancelProgress(context, downloadId)
        } catch (t: Throwable) {
            Log.w("DownloadCancelReceiver", "Failed to cancel download $downloadId", t)
        }
    }
}
