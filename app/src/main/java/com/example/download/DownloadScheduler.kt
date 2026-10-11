package com.example.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Enqueues and cancels background downloads.
 *
 * Every download is a unique piece of work (named after its item id) so the same
 * item can never be downloaded twice, and WorkManager itself provides the
 * exponential-backoff retry policy for transient network failures.
 */
object DownloadScheduler {

    /** Tag applied to every Cove download so they can be observed/cancelled together. */
    const val WORK_TAG = "cove_download"

    /** Backoff window used by WorkManager between retry attempts. */
    private const val BACKOFF_SECONDS = 15L

    fun workName(downloadId: Long): String = "cove_download_$downloadId"

    /** Unique, collision-safe item id (timestamp based, matching the previous scheme). */
    fun generateDownloadId(): Long =
        System.currentTimeMillis() * 1000L + (0..999).random()

    /**
     * Schedules a download.
     *
     * @param outputPath absolute destination resolved before enqueueing, persisted in the
     *   worker input so a restarted process resumes into the very same file.
     */
    fun enqueue(
        context: Context,
        downloadId: Long,
        title: String,
        url: String,
        isVideo: Boolean,
        isAudioOnly: Boolean,
        thumbnailUrl: String?,
        outputPath: String?
    ) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
                    DownloadWorker.KEY_TITLE to title,
                    DownloadWorker.KEY_URL to url,
                    DownloadWorker.KEY_IS_VIDEO to isVideo,
                    DownloadWorker.KEY_IS_AUDIO_ONLY to isAudioOnly,
                    DownloadWorker.KEY_THUMBNAIL_URL to thumbnailUrl,
                    DownloadWorker.KEY_OUTPUT_PATH to outputPath
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(downloadId), ExistingWorkPolicy.KEEP, request)
    }

    /** Cancels a single download (used by the notification action and the UI). */
    fun cancel(context: Context, downloadId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(downloadId))
    }

    /** Cancels every scheduled/running Cove download. */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }
}
