package com.example.domain

data class SearchResult(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedEpoch: Long? = null
)

data class StreamInfo(
    val videoId: String,
    val title: String,
    val primaryPlayUrl: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val uploader: String,
    val thumbnailUrl: String,
    val durationSeconds: Long
)

data class ScrapedMedia(
    val title: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val thumbnailUrl: String,
    val sourceUrl: String,
    val items: List<ScrapedItem>
)

data class ScrapedItem(
    val url: String,
    val isVideo: Boolean,
    val thumbnailUrl: String,
    val title: String
)

/**
 * Live telemetry emitted while a download is running.
 *
 * This is intentionally an in-memory snapshot only (never persisted) so the
 * Compose UI can mirror exactly what the foreground notification is showing.
 */
data class DownloadTelemetry(
    /** 0f..1f when known, -1f for an indeterminate/unknown total size. */
    val progress: Float = -1f,
    val bytesDownloaded: Long = -1L,
    val totalBytes: Long = -1L,
    /** -1L when the current speed is not available yet. */
    val speedBytesPerSec: Long = -1L,
    /** -1L when the remaining time cannot be computed. */
    val etaSeconds: Long = -1L,
    val statusText: String = ""
)
