package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.NetworkModule
import com.example.domain.ScrapedMedia
import com.example.domain.ScrapedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadItem(
    val id: Long,
    val title: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val statusText: String,
    val progress: Float, // 0.0f to 1.0f, or -1.0f for indeterminate
    val localUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailUrl: String? = null
)

sealed interface ScrapingState {
    object Idle : ScrapingState
    object Loading : ScrapingState
    data class Success(val media: ScrapedMedia) : ScrapingState
    data class Error(val message: String) : ScrapingState
}

sealed interface SearchState {
    object Idle : SearchState
}

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    var inputUrl by mutableStateOf("")
    var requestedTab by mutableStateOf<ScreenTab?>(null)
    var browserUrl by mutableStateOf("https://www.google.com")
    
    private val _scrapingState = MutableStateFlow<ScrapingState>(ScrapingState.Idle)
    val scrapingState = _scrapingState.asStateFlow()

    private val downloadRepository = NetworkModule.downloadRepository
    val downloads = downloadRepository.downloadsFlow.asStateFlow()

    private val getStreamUseCase = NetworkModule.getStreamUseCase

    fun setUrl(url: String) {
        inputUrl = url
    }

    fun clearUrl() {
        inputUrl = ""
    }

    fun onUrlInput(url: String) {
        if (ContentFilter.isBlockedUrl(url) || ContentFilter.containsBlockedContent(url)) {
            android.widget.Toast.makeText(context, "عذراً، تم حظر هذا الرابط لتجنب الأغاني والموسيقى تماشياً مع قيمنا الإسلامية.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        inputUrl = url
        fetchMedia()
        requestedTab = ScreenTab.DOWNLOAD
    }

    fun fetchMedia() {
        val input = inputUrl.trim()
        if (input.isEmpty()) {
            _scrapingState.value = ScrapingState.Error("الرجاء إدخال رابط")
            return
        }

        if (ContentFilter.isBlockedUrl(input) || ContentFilter.containsBlockedContent(input)) {
            _scrapingState.value = ScrapingState.Error("عذراً، يُمنع تحميل الأغاني والموسيقى تماشياً مع قيمنا وأخلاقياتنا الإسلامية لتجنب الذنوب.")
            return
        }

        // Check if it's a URL
        if (android.util.Patterns.WEB_URL.matcher(input).matches()) {
            _scrapingState.value = ScrapingState.Loading
            viewModelScope.launch {
                try {
                    val isYouTube = input.contains("youtube.com") || input.contains("youtu.be")
                    if (isYouTube) {
                        val videoId = if (input.contains("youtu.be")) {
                            input.substringAfterLast("/")
                        } else {
                            if (input.contains("v=")) input.substringAfter("v=").substringBefore("&")
                            else input.substringAfterLast("/")
                        }
                        
                        var media: ScrapedMedia? = null
                        try {
                            val streams = getStreamUseCase(videoId)
                            if (streams != null) {
                                if (ContentFilter.containsBlockedContent(streams.title)) {
                                    _scrapingState.value = ScrapingState.Error("عذراً، تم حظر هذا المحتوى لتضمينه مواد موسيقية/أغاني.")
                                    return@launch
                                }
                                media = ScrapedMedia(
                                    title = streams.title,
                                    mediaUrl = input,
                                    isVideo = true,
                                    thumbnailUrl = streams.thumbnailUrl,
                                    sourceUrl = input,
                                    items = listOf(ScrapedItem(url = input, isVideo = true, thumbnailUrl = streams.thumbnailUrl, title = streams.title))
                                )
                            }
                        } catch (e: Exception) {
                            Log.w("DownloadViewModel", "getStreamUseCase failed, falling back to fetchMediaInfo: ${e.message}")
                        }

                        if (media == null) {
                            media = downloadRepository.fetchMediaInfo(input)
                        }

                        if (ContentFilter.containsBlockedContent(media.title)) {
                            _scrapingState.value = ScrapingState.Error("عذراً، تم حظر هذا المحتوى لتضمينه مواد موسيقية/أغاني.")
                            return@launch
                        }
                        _scrapingState.value = ScrapingState.Success(media)
                    } else {
                        // For non-YouTube links, use our super fast and robust scraper
                        val media = downloadRepository.fetchMediaInfo(input)
                        if (ContentFilter.containsBlockedContent(media.title)) {
                            _scrapingState.value = ScrapingState.Error("عذراً، تم حظر هذا المحتوى لتضمينه مواد موسيقية/أغاني.")
                            return@launch
                        }
                        _scrapingState.value = ScrapingState.Success(media)
                    }
                } catch (e: Exception) {
                    Log.e("DownloadViewModel", "Error fetching media", e)
                    _scrapingState.value = ScrapingState.Error("حدث خطأ: ${e.localizedMessage}")
                }
            }
        } else {
            // It's a search query - this will be handled by SearchViewModel in MainActivity
            requestedTab = ScreenTab.DOWNLOAD
        }
    }

    fun downloadMedia(media: ScrapedMedia) {
        downloadUrl(media.title, media.mediaUrl, media.isVideo, false, media.thumbnailUrl)
    }

    fun downloadUrl(title: String, url: String, isVideo: Boolean, isAudioOnly: Boolean, thumbnailUrl: String? = null) {
        _scrapingState.value = ScrapingState.Idle
        inputUrl = "" // Clear after trigger
        
        viewModelScope.launch {
            try {
                val item = downloadRepository.downloadUrl(context, title, url, isVideo, isAudioOnly, thumbnailUrl)
                showSuccessNotification(item.title, item.isVideo, item.localUri)
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Download failed", e)
            }
        }
    }

    fun downloadDirectUrl(ctx: Context, title: String, url: String, isVideo: Boolean, isAudioOnly: Boolean, thumbnailUrl: String? = null) {
        downloadUrl(title, url, isVideo, isAudioOnly, thumbnailUrl)
    }

    private fun showSuccessNotification(title: String, isVideo: Boolean, localUriString: String?) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "cove_download_channel"
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "تنزيلات Cove",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "إشعارات اكتمال تنزيل وسائط Cove"
                    enableLights(true)
                    lightColor = android.graphics.Color.GREEN
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("tab", "HISTORY")
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_custom_complete)
            remoteViews.setTextViewText(R.id.notification_title, title)
            remoteViews.setTextViewText(R.id.notification_subtext, "اكتمل التنزيل، انقر للتحقق")
            
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val currentTime = sdf.format(Date())
            remoteViews.setTextViewText(R.id.notification_time, currentTime)

            val thumbnailBitmap = getDownloadedMediaThumbnail(localUriString)
            if (thumbnailBitmap != null) {
                val roundedBitmap = getRoundedCornerBitmap(thumbnailBitmap, 24)
                remoteViews.setImageViewBitmap(R.id.notification_thumbnail, roundedBitmap)
            } else {
                remoteViews.setImageViewResource(R.id.notification_thumbnail, R.mipmap.ic_launcher)
            }

            remoteViews.setImageViewResource(R.id.notification_status_icon, R.drawable.ic_green_check_circle)

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(title)
                .setContentText("اكتمل التنزيل، انقر للتحقق")
                .setStyle(androidx.core.app.NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setCustomHeadsUpContentView(remoteViews)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)

            if (thumbnailBitmap != null) {
                builder.setLargeIcon(thumbnailBitmap)
            }

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Failed to show custom success notification", e)
        }
    }

    private fun getDownloadedMediaThumbnail(localUriString: String?): android.graphics.Bitmap? {
        if (localUriString == null) return null
        try {
            val file = File(localUriString)
            if (!file.exists()) return null

            if (file.name.endsWith(".mp4", ignoreCase = true)) {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(localUriString)
                val bitmap = retriever.getFrameAtTime(1000000) // 1 second
                retriever.release()
                return bitmap
            } else if (file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".png", ignoreCase = true)) {
                return android.graphics.BitmapFactory.decodeFile(localUriString)
            }
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Failed to retrieve thumbnail", e)
        }
        return null
    }

    private fun getRoundedCornerBitmap(bitmap: android.graphics.Bitmap, pixels: Int): android.graphics.Bitmap {
        val output = android.graphics.Bitmap.createBitmap(bitmap.width, bitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)

        val color = 0xff424242.toInt()
        val paint = android.graphics.Paint()
        val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = android.graphics.RectF(rect)
        val roundPx = pixels.toFloat()

        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint)

        paint.setXfermode(android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN))
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch {
            downloadRepository.deleteItem(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
