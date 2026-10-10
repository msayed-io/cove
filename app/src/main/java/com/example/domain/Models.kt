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
