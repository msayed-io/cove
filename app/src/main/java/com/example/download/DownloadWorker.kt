package com.example.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.api.NetworkModule
import com.example.data.DownloadRepository
import com.example.domain.DownloadTelemetry
import kotlinx.coroutines.CancellationException

/**
 * Runs a single media download inside WorkManager.
 *
 * Why it exists: downloads used to live in `viewModelScope`, so Android killed them
 * as soon as the app left the foreground. Hosting the work here keeps the process
 * alive (foreground service while running), survives screen lock / app dismissal,
 * resumes automatically after a reboot or process death, and retries transient
 * network failures with exponential backoff.
 *
 * The worker itself contains no download logic — it delegates to the existing,
 * untouched [DownloadRepository] engine and mirrors its live telemetry into the
 * notification and the shared StateFlow consumed by the Compose UI.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val title = inputData.getString(KEY_TITLE)
        val url = inputData.getString(KEY_URL)

        if (downloadId <= 0L || title.isNullOrBlank() || url.isNullOrBlank()) {
            Log.e(TAG, "Aborting worker: invalid input data")
            return Result.failure()
        }

        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, false)
        val isAudioOnly = inputData.getBoolean(KEY_IS_AUDIO_ONLY, false)
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL)
        val outputPath = inputData.getString(KEY_OUTPUT_PATH)

        val repository = NetworkModule.downloadRepository
        DownloadNotifications.ensureChannels(applicationContext)

        val startingStatus = if (runAttemptCount > 0) {
            "إعادة المحاولة (${runAttemptCount + 1}/$MAX_ATTEMPTS)..."
        } else {
            "جاري التحضير..."
        }
        val initialTelemetry = DownloadTelemetry(progress = 0f, statusText = startingStatus)

        // Promote to a foreground service so the download survives backgrounding.
        var isForeground = false
        try {
            setForeground(foregroundInfo(title, null, initialTelemetry))
            isForeground = true
        } catch (t: Throwable) {
            Log.w(TAG, "Foreground promotion unavailable, falling back to a plain notification", t)
        }
        if (!isForeground) {
            DownloadNotifications.showProgress(
                context = applicationContext,
                downloadId = downloadId,
                title = title,
                thumbnailPath = null,
                telemetry = initialTelemetry,
                cancellable = true
            )
        }

        // Ensure the item exists in the shared StateFlow before the first progress tick.
        val pending = repository.registerPendingDownload(
            context = applicationContext,
            downloadId = downloadId,
            title = title,
            url = url,
            isVideo = isVideo,
            isAudioOnly = isAudioOnly,
            thumbnailUrl = thumbnailUrl,
            preferredPath = outputPath
        )
        repository.markDownloadStatus(downloadId, startingStatus)

        var thumbnailPath: String? = pending.localUri?.takeIf { it.endsWith(".thumb.jpg") }
        var lastNotificationAt = 0L

        // Set to true as soon as the work reaches a terminal state inside doWork(); a stopped
        // worker (user cancel or system preemption) leaves it false so the cleanup below runs.
        var reachedTerminalState = false

        return try {
            val completed = repository.downloadUrl(
                context = applicationContext,
                title = title,
                url = url,
                isVideo = isVideo,
                isAudioOnly = isAudioOnly,
                thumbnailUrl = thumbnailUrl,
                taskId = downloadId,
                outputPathOverride = outputPath
            ) { telemetry ->
                thumbnailPath = currentThumbnailPath(pending)

                val now = SystemClock.elapsedRealtime()
                if (now - lastNotificationAt >= NOTIFICATION_INTERVAL_MS) {
                    lastNotificationAt = now

                    if (isForeground) {
                        try {
                            setForeground(foregroundInfo(title, thumbnailPath, telemetry))
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to refresh foreground notification", t)
                        }
                    } else {
                        DownloadNotifications.showProgress(
                            context = applicationContext,
                            downloadId = downloadId,
                            title = title,
                            thumbnailPath = thumbnailPath,
                            telemetry = telemetry,
                            cancellable = true
                        )
                    }

                    try {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to telemetry.progress,
                                KEY_STATUS to telemetry.statusText
                            )
                        )
                    } catch (t: Throwable) {
                        Log.w(TAG, "Failed to publish progress", t)
                    }
                }
            }

            DownloadNotifications.cancelProgress(applicationContext, downloadId)
            DownloadNotifications.showCompleted(
                context = applicationContext,
                downloadId = downloadId,
                title = completed.title,
                localUriString = completed.localUri
            )
            reachedTerminalState = true
            Result.success()
        } catch (e: CancellationException) {
            // The work was stopped (user cancel / system preemption): never report a failure here,
            // the stop path has already updated the shared state.
            throw e
        } catch (e: DownloadRepository.DownloadException) {
            // The engine already classified the failure (transient vs permanent).
            reachedTerminalState = true
            handleFailure(
                message = e.message ?: "فشل التحميل",
                retryable = e.retryable,
                downloadId = downloadId,
                title = title,
                thumbnailPath = thumbnailPath,
                repository = repository
            )
        } catch (e: Exception) {
            reachedTerminalState = true
            handleFailure(
                message = e.message ?: "خطأ غير متوقع",
                retryable = true,
                downloadId = downloadId,
                title = title,
                thumbnailPath = thumbnailPath,
                repository = repository
            )
        } finally {
            // `CoroutineWorker.onStopped()` is final, so the stop cleanup lives here: this branch
            // runs when doWork was cancelled (user cancel / system preemption) without reaching
            // any terminal state above.
            if (!reachedTerminalState) {
                releaseAfterStop(downloadId, repository)
            }
        }
    }

    /**
     * Runs when the work is stopped before finishing (explicit cancel or system preemption).
     * Releases the underlying network resources so nothing keeps running in the dark.
     */
    private fun releaseAfterStop(downloadId: Long, repository: DownloadRepository) {
        try {
            repository.stopActiveNetworkTask(downloadId)
            DownloadNotifications.cancelProgress(applicationContext, downloadId)

            // A user cancel already wrote its final status; a system preemption is a pause that
            // WorkManager resumes once the constraints are satisfied again.
            if (!repository.isCancelledByUser(downloadId)) {
                repository.markDownloadStatus(downloadId, "إيقاف مؤقت — سيستأنف التنزيل تلقائياً...")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Cleanup after stop failed", t)
        }
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private suspend fun handleFailure(
        message: String,
        retryable: Boolean,
        downloadId: Long,
        title: String,
        thumbnailPath: String?,
        repository: DownloadRepository
    ): Result {
        if (repository.isCancelledByUser(downloadId)) {
            Log.d(TAG, "Download $downloadId was cancelled by the user")
            return Result.failure()
        }

        val attemptsUsed = runAttemptCount + 1
        val canRetry = retryable && attemptsUsed < MAX_ATTEMPTS

        if (canRetry) {
            val status = "تعذر الاتصال — إعادة المحاولة ($attemptsUsed/$MAX_ATTEMPTS)..."
            repository.markDownloadStatus(downloadId, status)

            try {
                setForeground(
                    foregroundInfo(
                        title = title,
                        thumbnailPath = thumbnailPath,
                        telemetry = DownloadTelemetry(progress = -1f, statusText = status)
                    )
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to update notification before retry", t)
            }

            return Result.retry()
        }

        repository.markDownloadStatus(downloadId, message)
        DownloadNotifications.cancelProgress(applicationContext, downloadId)
        DownloadNotifications.showFailed(
            context = applicationContext,
            downloadId = downloadId,
            title = title,
            message = message
        )
        return Result.failure()
    }

    private fun foregroundInfo(
        title: String,
        thumbnailPath: String?,
        telemetry: DownloadTelemetry
    ): ForegroundInfo {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        val notification = DownloadNotifications.buildProgressNotification(
            context = applicationContext,
            downloadId = downloadId,
            title = title,
            thumbnailPath = thumbnailPath,
            telemetry = telemetry,
            cancellable = true
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DownloadNotifications.progressNotificationId(downloadId),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DownloadNotifications.progressNotificationId(downloadId), notification)
        }
    }

    /** Prefers the cached companion thumbnail once the engine has written it. */
    private fun currentThumbnailPath(pending: com.example.DownloadItem): String? {
        val outputPath = pending.localUri ?: return null
        val companion = outputPath.substringBeforeLast(".") + ".thumb.jpg"
        return companion.takeIf { java.io.File(it).exists() } ?: outputPath
    }

    companion object {
        private const val TAG = "DownloadWorker"

        // Input data contract (also used by the notification intents).
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TITLE = "title"
        const val KEY_URL = "url"
        const val KEY_IS_VIDEO = "is_video"
        const val KEY_IS_AUDIO_ONLY = "is_audio_only"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_OUTPUT_PATH = "output_path"

        // Progress contract exposed through setProgress().
        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"

        /** Maximum attempts (first try + 2 retries) before surfacing a permanent failure. */
        const val MAX_ATTEMPTS = 3

        /** Notification refresh interval — keeps the binder/DB load negligible. */
        private const val NOTIFICATION_INTERVAL_MS = 500L
    }
}
