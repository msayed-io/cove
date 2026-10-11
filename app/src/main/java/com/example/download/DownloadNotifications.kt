package com.example.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.DownloadTelemetry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single source of truth for every Cove download notification.
 *
 * Keeps the original branded completion notification (RemoteViews) and adds a
 * live progress notification with a real progress bar, speed and ETA.
 */
object DownloadNotifications {

    private const val TAG = "CoveNotifications"

    /** Silent channel used by the ongoing/progress notification. */
    const val CHANNEL_PROGRESS = "cove_download_progress_channel"

    /** Original channel id — kept so existing user settings stay valid. */
    const val CHANNEL_COMPLETE = "cove_download_channel"

    private const val GROUP_KEY = "com.cove.app.DOWNLOADS"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            "التنزيلات الجارية",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "شريط تقدم حي لعمليات التنزيل الجارية في الخلفية"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            "تنزيلات Cove",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات اكتمال تنزيل وسائط Cove"
            enableLights(true)
            lightColor = android.graphics.Color.GREEN
            enableVibration(true)
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(completeChannel)
    }

    // ---------------------------------------------------------------------
    // Ids
    // ---------------------------------------------------------------------

    /** Ongoing progress notification id (stable per download). */
    fun progressNotificationId(downloadId: Long): Int =
        10_000 + ((downloadId % 90_000L).toInt() and 0x7FFFFFFF)

    /** Completion / failure notification id (stable per download). */
    fun resultNotificationId(downloadId: Long): Int =
        100_000 + ((downloadId % 900_000L).toInt() and 0x7FFFFFFF)

    // ---------------------------------------------------------------------
    // Live progress notification
    // ---------------------------------------------------------------------

    /**
     * Builds the notification shown while a download runs. The same instance is
     * used both for [androidx.work.ForegroundInfo] and for regular updates so
     * the status bar entry never flickers.
     */
    fun buildProgressNotification(
        context: Context,
        downloadId: Long,
        title: String,
        thumbnailPath: String?,
        telemetry: DownloadTelemetry,
        cancellable: Boolean
    ): Notification {
        ensureChannels(context)

        val status = statusLine(telemetry)

        val views = RemoteViews(context.packageName, R.layout.notification_download_progress)
        views.setTextViewText(R.id.notification_progress_title, title)
        views.setTextViewText(R.id.notification_progress_status, status)
        views.setTextViewText(R.id.notification_progress_percent, DownloadFormat.percent(telemetry.progress))

        val indeterminate = telemetry.progress < 0f
        views.setProgressBar(
            R.id.notification_progress_bar,
            100,
            if (indeterminate) 0 else (telemetry.progress.coerceIn(0f, 1f) * 100).toInt(),
            indeterminate
        )

        val localThumbnail = decodeSmallBitmap(thumbnailPath, 96)
        if (localThumbnail != null) {
            views.setImageViewBitmap(R.id.notification_progress_thumbnail, roundedBitmap(localThumbnail, 16))
        } else {
            views.setImageViewResource(R.id.notification_progress_thumbnail, R.mipmap.ic_launcher)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(status)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setCustomBigContentView(views)
            .setContentIntent(openDownloadsIntent(context, downloadId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setGroup(GROUP_KEY)

        if (cancellable) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "إلغاء",
                cancelDownloadIntent(context, downloadId)
            )
        }

        return builder.build()
    }

    /** Posts/updates the progress notification when the worker is not foregrounded. */
    fun showProgress(
        context: Context,
        downloadId: Long,
        title: String,
        thumbnailPath: String?,
        telemetry: DownloadTelemetry,
        cancellable: Boolean
    ) {
        try {
            NotificationManagerCompat.from(context).notify(
                progressNotificationId(downloadId),
                buildProgressNotification(context, downloadId, title, thumbnailPath, telemetry, cancellable)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to post progress notification", t)
        }
    }

    fun cancelProgress(context: Context, downloadId: Long) {
        try {
            NotificationManagerCompat.from(context).cancel(progressNotificationId(downloadId))
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to cancel progress notification", t)
        }
    }

    // ---------------------------------------------------------------------
    // Completion / failure
    // ---------------------------------------------------------------------

    /** Original premium completion notification, now owned by the worker. */
    fun showCompleted(
        context: Context,
        downloadId: Long,
        title: String,
        localUriString: String?
    ) {
        try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val remoteViews = RemoteViews(context.packageName, R.layout.notification_custom_complete)
            remoteViews.setTextViewText(R.id.notification_title, title)
            remoteViews.setTextViewText(R.id.notification_subtext, "اكتمل التنزيل، انقر للتحقق")
            remoteViews.setTextViewText(
                R.id.notification_time,
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            )

            val thumbnailBitmap = mediaThumbnail(localUriString)
            if (thumbnailBitmap != null) {
                remoteViews.setImageViewBitmap(R.id.notification_thumbnail, roundedBitmap(thumbnailBitmap, 24))
            } else {
                remoteViews.setImageViewResource(R.id.notification_thumbnail, R.mipmap.ic_launcher)
            }
            remoteViews.setImageViewResource(R.id.notification_status_icon, R.drawable.ic_green_check_circle)

            val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText("اكتمل التنزيل، انقر للتحقق")
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setCustomHeadsUpContentView(remoteViews)
                .setContentIntent(openDownloadsIntent(context, downloadId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_KEY)

            if (thumbnailBitmap != null) {
                builder.setLargeIcon(thumbnailBitmap)
            }

            manager.notify(resultNotificationId(downloadId), builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show completion notification", t)
        }
    }

    fun showFailed(context: Context, downloadId: Long, title: String, message: String) {
        try {
            ensureChannels(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETE)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(openDownloadsIntent(context, downloadId))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup(GROUP_KEY)

            NotificationManagerCompat.from(context)
                .notify(resultNotificationId(downloadId), builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to show failure notification", t)
        }
    }

    // ---------------------------------------------------------------------
    // Intents
    // ---------------------------------------------------------------------

    private fun openDownloadsIntent(context: Context, downloadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("tab", "HISTORY")
            putExtra(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getActivity(
            context,
            resultNotificationId(downloadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** "إلغاء" action — hands the cancel request to [DownloadCancelReceiver]. */
    private fun cancelDownloadIntent(context: Context, downloadId: Long): PendingIntent {
        val intent = Intent(context, DownloadCancelReceiver::class.java).apply {
            putExtra(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            context,
            progressNotificationId(downloadId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun statusLine(telemetry: DownloadTelemetry): String {
        val parts = mutableListOf<String>()

        if (telemetry.statusText.isNotBlank()) {
            parts.add(telemetry.statusText)
        }
        if (telemetry.totalBytes > 0L && telemetry.bytesDownloaded > 0L) {
            parts.add("${DownloadFormat.bytes(telemetry.bytesDownloaded)} من ${DownloadFormat.bytes(telemetry.totalBytes)}")
        }
        val speed = DownloadFormat.speed(telemetry.speedBytesPerSec)
        if (speed.isNotEmpty()) {
            parts.add(speed)
        }
        val eta = DownloadFormat.eta(telemetry.etaSeconds)
        if (eta.isNotEmpty()) {
            parts.add(eta)
        }

        return parts.joinToString(" • ")
    }

    /** Down-scales a local image so the bitmap stays binder friendly. */
    private fun decodeSmallBitmap(path: String?, maxSize: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= maxSize && bounds.outHeight / (sampleSize * 2) >= maxSize) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, options)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to decode notification bitmap from $path", t)
            null
        }
    }

    /** Frame (video) or raw image (photo) used by the completion notification. */
    private fun mediaThumbnail(localUriString: String?): Bitmap? {
        if (localUriString.isNullOrBlank()) return null
        val file = File(localUriString)
        if (!file.exists()) return null

        return try {
            when {
                file.name.endsWith(".mp4", ignoreCase = true) -> {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(localUriString)
                        retriever.getFrameAtTime(1_000_000L)
                    } finally {
                        retriever.release()
                    }
                }
                file.name.endsWith(".jpg", ignoreCase = true) ||
                    file.name.endsWith(".png", ignoreCase = true) -> decodeSmallBitmap(localUriString, 192)
                else -> null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to extract media thumbnail", t)
            null
        }
    }

    private fun roundedBitmap(bitmap: Bitmap, pixels: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply { isAntiAlias = true }
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)
        val roundPx = pixels.toFloat()

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = 0xff424242.toInt()
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }
}
