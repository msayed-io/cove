package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.NetworkModule
import com.example.download.DownloadScheduler
import com.example.domain.ScrapedMedia
import com.example.domain.ScrapedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadItem(
    val id: Long,
    val title: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val statusText: String,
    val progress: Float, // 0.0f to 1.0f, or -1.0f for indeterminate
    val localUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailUrl: String? = null,
    // Live telemetry (in-memory only) — mirrors what the ongoing notification shows.
    val bytesDownloaded: Long = -1L,
    val totalBytes: Long = -1L,
    val speedBytesPerSec: Long = -1L,
    val etaSeconds: Long = -1L
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
                val downloadId = DownloadScheduler.generateDownloadId()

                // Register the item off the main thread (resolves folders / writes a probe file)
                // so the UI can show it instantly; the worker reuses the very same entry and
                // output path, which is what makes resuming work.
                val item = withContext(Dispatchers.IO) {
                    downloadRepository.registerPendingDownload(
                        context = context,
                        downloadId = downloadId,
                        title = title,
                        url = url,
                        isVideo = isVideo,
                        isAudioOnly = isAudioOnly,
                        thumbnailUrl = thumbnailUrl
                    )
                }

                DownloadScheduler.enqueue(
                    context = context,
                    downloadId = downloadId,
                    title = title,
                    url = url,
                    isVideo = isVideo,
                    isAudioOnly = isAudioOnly,
                    thumbnailUrl = thumbnailUrl,
                    outputPath = item.localUri
                )
            } catch (e: Exception) {
                Log.e("DownloadViewModel", "Failed to enqueue background download", e)
                android.widget.Toast.makeText(
                    context,
                    "تعذر بدء التنزيل: ${e.localizedMessage ?: e.message}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun downloadDirectUrl(ctx: Context, title: String, url: String, isVideo: Boolean, isAudioOnly: Boolean, thumbnailUrl: String? = null) {
        downloadUrl(title, url, isVideo, isAudioOnly, thumbnailUrl)
    }

    /** Cancels a running background download without removing its entry. */
    fun cancelDownload(id: Long) {
        try {
            downloadRepository.markCancelledByUser(id)
            downloadRepository.stopActiveNetworkTask(id)
            DownloadScheduler.cancel(context, id)
            downloadRepository.markDownloadStatus(id, "تم إلغاء التنزيل")
        } catch (e: Exception) {
            Log.e("DownloadViewModel", "Failed to cancel download $id", e)
        }
    }

    fun deleteDownload(id: Long) {
        viewModelScope.launch {
            try {
                // Stop any pending/running work before removing the entry and its file.
                DownloadScheduler.cancel(context, id)
            } catch (e: Exception) {
                Log.w("DownloadViewModel", "Failed to cancel work for $id", e)
            }
            downloadRepository.deleteItem(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
