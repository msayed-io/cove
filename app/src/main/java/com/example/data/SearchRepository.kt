package com.example.data

import android.util.Log
import com.example.api.NetworkModule
import com.example.domain.SearchResult
import com.example.domain.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

import okhttp3.MediaType.Companion.toMediaType

import okhttp3.RequestBody.Companion.toRequestBody

class SearchRepository {

    private val TAG = "SearchRepository"

    private val searchCache = android.util.LruCache<String, List<SearchResult>>(50)
    private val streamInfoCache = android.util.LruCache<String, StreamInfo>(50)
    private val suggestionsCache = android.util.LruCache<String, List<String>>(100)
    private data class CachedPlaybackStream(val info: StreamInfo, val expiresAtElapsedMs: Long)
    private val playbackStreamCache = android.util.LruCache<String, CachedPlaybackStream>(32)

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        suggestionsCache.get(trimmed.lowercase())?.let { return@withContext it }

        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyString = response.body?.string() ?: return@withContext emptyList()
                val jsonElement = NetworkModule.json.parseToJsonElement(bodyString)
                val jsonArray = jsonElement.jsonArray
                if (jsonArray.size > 1) {
                    val suggestionsArray = jsonArray[1].jsonArray
                    val results = suggestionsArray.map { it.jsonPrimitive.content }
                    if (results.isNotEmpty()) {
                        suggestionsCache.put(trimmed.lowercase(), results)
                    }
                    return@withContext results
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch suggestions: ${e.message}")
        }
        emptyList()
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        searchCache.get(trimmed.lowercase())?.let { return@withContext it }

        val results = mutableListOf<SearchResult>()
        try {
            val searchExtractor = ServiceList.YouTube.getSearchExtractor(trimmed)
            searchExtractor.fetchPage()
            
            var page = searchExtractor.initialPage
            var pageCount = 0
            while (page != null) {
                val items = page.items
                for (item in items) {
                    if (item is StreamInfoItem) {
                        results.add(
                            SearchResult(
                                videoId = item.url,
                                title = item.name,
                                thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: "",
                                uploaderName = item.uploaderName,
                                durationSeconds = item.duration,
                                viewCount = item.viewCount,
                                uploadedEpoch = 0L
                            )
                        )
                    }
                }
                pageCount++
                // Rich search results: up to 100 items or 5 pages for comprehensive search
                if (results.size >= 100 || pageCount >= 5 || !page.hasNextPage()) {
                    break
                }
                page = searchExtractor.getPage(page.nextPage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}", e)
            throw Exception("فشل البحث: ${e.message}")
        }
        
        val distinctResults = results.distinctBy { it.videoId }
        if (distinctResults.isNotEmpty()) {
            searchCache.put(trimmed.lowercase(), distinctResults)
        }
        return@withContext distinctResults
    }

    /**
     * Playback-only resolver: prefer the same NewPipe extractor used by search, then fall back
     * to the complete existing resolver chain. DownloadViewModel continues to use getStreams()
     * unchanged, so this fast path cannot alter download selection or reliability.
     */
    suspend fun getPlaybackStream(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        val source = videoId.trim()
        if (source.isBlank()) return@withContext null

        // YouTube IDs are case-sensitive; keep the exact source string as the playback cache key.
        val cacheKey = source
        val now = android.os.SystemClock.elapsedRealtime()
        playbackStreamCache.get(cacheKey)?.let { cached ->
            if (cached.expiresAtElapsedMs > now) return@withContext cached.info
            playbackStreamCache.remove(cacheKey)
        }

        val startedAt = android.os.SystemClock.elapsedRealtime()
        try {
            resolveNewPipePlaybackStream(source)?.let { info ->
                playbackStreamCache.put(
                    cacheKey,
                    CachedPlaybackStream(info, android.os.SystemClock.elapsedRealtime() + 60_000L)
                )
                Log.d(TAG, "Playback stream resolved by NewPipe in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
                return@withContext info
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast NewPipe playback resolution failed; using full fallback chain", e)
        }

        val fallbackStartedAt = android.os.SystemClock.elapsedRealtime()
        val info = getStreams(source)
        if (!info?.primaryPlayUrl.isNullOrBlank()) {
            playbackStreamCache.put(
                cacheKey,
                CachedPlaybackStream(info!!, android.os.SystemClock.elapsedRealtime() + 60_000L)
            )
            Log.d(TAG, "Playback fallback resolved in ${android.os.SystemClock.elapsedRealtime() - fallbackStartedAt}ms")
        }
        info
    }

    private fun resolveNewPipePlaybackStream(videoId: String): StreamInfo? {
        val url = if (videoId.startsWith("http", ignoreCase = true)) {
            videoId
        } else {
            "https://www.youtube.com/watch?v=$videoId"
        }
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        extractor.fetchPage()

        val bestVideo = extractor.videoStreams?.firstOrNull {
            it.getFormat()?.getName()?.contains("mp4", ignoreCase = true) == true
        } ?: extractor.videoStreams?.firstOrNull()
        val bestAudio = extractor.audioStreams?.firstOrNull {
            it.getFormat()?.getName()?.contains("m4a", ignoreCase = true) == true
        } ?: extractor.audioStreams?.firstOrNull()
        val primaryUrl = bestVideo?.content ?: bestAudio?.content ?: return null

        return StreamInfo(
            videoId = videoId,
            title = extractor.name,
            primaryPlayUrl = primaryUrl,
            videoUrl = bestVideo?.content,
            audioUrl = bestAudio?.content,
            uploader = extractor.uploaderName,
            thumbnailUrl = extractor.thumbnails?.firstOrNull()?.url ?: "",
            durationSeconds = extractor.length
        )
    }

    private suspend fun fetchCobaltUrl(videoUrl: String, isAudioOnly: Boolean = false): String? = withContext(Dispatchers.IO) {
        null
    }

    suspend fun getStreams(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null

        streamInfoCache.get(videoId)?.let { return@withContext it }

        try {
            val url = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId"
            
            var ytDlException: Exception? = null
            
            // 1. Try YoutubeDL with tv,mweb player clients
            try {
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(com.example.api.NetworkModule.applicationContext!!)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to init YoutubeDL for search", e)
                }
                
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("--extractor-args", "youtube:player_client=tv_embedded,mweb,ios,tv")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                val info = com.yausername.youtubedl_android.YoutubeDL.getInstance().getInfo(request)
                
                // Get URL from formats if primary url is null
                var primaryPlayUrl = info.url
                if (primaryPlayUrl == null && info.formats != null) {
                    primaryPlayUrl = info.formats?.lastOrNull { it.ext == "mp4" }?.url
                }
                
                if (primaryPlayUrl != null) {
                    val streamInfo = StreamInfo(
                        videoId = videoId,
                        title = info.title ?: "بدون عنوان",
                        primaryPlayUrl = primaryPlayUrl,
                        videoUrl = primaryPlayUrl,
                        audioUrl = null,
                        uploader = info.uploader ?: "",
                        thumbnailUrl = info.thumbnail ?: "",
                        durationSeconds = info.duration.toLong()
                    )
                    streamInfoCache.put(videoId, streamInfo)
                    return@withContext streamInfo
                } else {
                    ytDlException = Exception("لم يتم العثور على رابط مباشر في YoutubeDL")
                }
            } catch (e: Exception) {
                ytDlException = e
                Log.e(TAG, "YoutubeDL getStreams failed: ${e.message}")
            }
            
            // 2. Try NewPipe
            var newPipeException: Exception? = null
            try {
                val streamExtractor = ServiceList.YouTube.getStreamExtractor(url)
                streamExtractor.fetchPage()
                
                val videoStreams = streamExtractor.videoStreams
                val audioStreams = streamExtractor.audioStreams
                
                val bestVideo = videoStreams?.firstOrNull { it.getFormat()?.getName()?.contains("mp4", true) == true } ?: videoStreams?.firstOrNull()
                val bestAudio = audioStreams?.firstOrNull { it.getFormat()?.getName()?.contains("m4a", true) == true } ?: audioStreams?.firstOrNull()
                
                val primaryPlayUrl = bestVideo?.content ?: bestAudio?.content ?: throw Exception("لا توجد روابط في NewPipe")
                
                return@withContext StreamInfo(
                    videoId = videoId,
                    title = streamExtractor.name,
                    primaryPlayUrl = primaryPlayUrl,
                    videoUrl = bestVideo?.content,
                    audioUrl = bestAudio?.content,
                    uploader = streamExtractor.uploaderName,
                    thumbnailUrl = streamExtractor.thumbnails?.firstOrNull()?.url ?: "",
                    durationSeconds = streamExtractor.length
                )
            } catch (e: Exception) {
                newPipeException = e
                Log.e(TAG, "NewPipe getStreams failed: ${e.message}")
            }

            // 3. Piped API Fallback
            try {
                val cleanId = if (videoId.startsWith("http")) {
                    videoId.substringAfter("v=").substringBefore("&").substringBefore("?")
                } else videoId
                
                val pipedInstances = listOf(
                    "https://pipedapi.kavin.rocks",
                    "https://api.piped.private.coffee",
                    "https://pipedapi.mha.fi",
                    "https://piped-api.garudalinux.org",
                    "https://pipedapi.drgns.space",
                    "https://pipedapi.astral.yous.org",
                    "https://pipedapi.adminforge.de"
                )
                for (baseUrl in pipedInstances) {
                    try {
                        val req = Request.Builder()
                            .url("$baseUrl/streams/$cleanId")
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val resp = com.example.api.NetworkModule.okHttpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            val json = com.example.api.NetworkModule.json.parseToJsonElement(bodyStr).jsonObject
                            val title = json["title"]?.jsonPrimitive?.content ?: "فيديو يوتيوب"
                            val uploader = json["uploader"]?.jsonPrimitive?.content ?: ""
                            val audioStreams = json["audioStreams"]?.jsonArray
                            val videoStreams = json["videoStreams"]?.jsonArray
                            val bestAudio = audioStreams?.firstOrNull { elem ->
                                val mime = elem.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                                mime.contains("mp4") || mime.contains("m4a")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                                ?: audioStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            val bestVideo = videoStreams?.firstOrNull { elem ->
                                val mime = elem.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                                mime.contains("mp4")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                                ?: videoStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            val primaryUrl = bestVideo ?: bestAudio
                            if (!primaryUrl.isNullOrEmpty()) {
                                val streamInfo = StreamInfo(
                                    videoId = cleanId,
                                    title = title,
                                    primaryPlayUrl = primaryUrl,
                                    videoUrl = bestVideo,
                                    audioUrl = bestAudio,
                                    uploader = uploader,
                                    thumbnailUrl = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                                    durationSeconds = json["duration"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                )
                                streamInfoCache.put(cleanId, streamInfo)
                                return@withContext streamInfo
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Piped instance $baseUrl failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Piped stream resolve failed: ${e.message}")
            }

            // 4. Invidious API Fallback
            try {
                val cleanId = if (videoId.startsWith("http")) {
                    videoId.substringAfter("v=").substringBefore("&").substringBefore("?")
                } else videoId

                val invidiousInstances = listOf(
                    "https://invidious.nerdvpn.de",
                    "https://inv.tux.stream",
                    "https://invidious.drgns.space",
                    "https://vid.puffyan.us",
                    "https://inv.us.projectsegfau.lt",
                    "https://invidious.io.lol"
                )
                for (baseUrl in invidiousInstances) {
                    try {
                        val req = Request.Builder()
                            .url("$baseUrl/api/v1/videos/$cleanId")
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val resp = com.example.api.NetworkModule.okHttpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            val json = com.example.api.NetworkModule.json.parseToJsonElement(bodyStr).jsonObject
                            val title = json["title"]?.jsonPrimitive?.content ?: "فيديو يوتيوب"
                            val uploader = json["author"]?.jsonPrimitive?.content ?: ""
                            val durationSeconds = json["lengthSeconds"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                            val formatStreams = json["formatStreams"]?.jsonArray
                            val adaptiveFormats = json["adaptiveFormats"]?.jsonArray

                            val bestVideo = formatStreams?.firstOrNull { elem ->
                                val container = elem.jsonObject["container"]?.jsonPrimitive?.content ?: ""
                                container.contains("mp4")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                                ?: adaptiveFormats?.firstOrNull { elem ->
                                    val type = elem.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                                    type.contains("video/mp4")
                                }?.jsonObject?.get("url")?.jsonPrimitive?.content

                            val bestAudio = adaptiveFormats?.firstOrNull { elem ->
                                val type = elem.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                                type.contains("audio")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content

                            val primaryUrl = bestVideo ?: bestAudio
                            if (!primaryUrl.isNullOrEmpty()) {
                                val streamInfo = StreamInfo(
                                    videoId = cleanId,
                                    title = title,
                                    primaryPlayUrl = primaryUrl,
                                    videoUrl = bestVideo,
                                    audioUrl = bestAudio,
                                    uploader = uploader,
                                    thumbnailUrl = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                                    durationSeconds = durationSeconds
                                )
                                streamInfoCache.put(cleanId, streamInfo)
                                return@withContext streamInfo
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Invidious instance $baseUrl failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Invidious stream resolve failed: ${e.message}")
            }

            // 5. Cobalt API Fallback
            try {
                val fullUrl = if (videoId.startsWith("http")) videoId else "https://www.youtube.com/watch?v=$videoId"
                val cleanId = if (videoId.startsWith("http")) {
                    videoId.substringAfter("v=").substringBefore("&").substringBefore("?")
                } else videoId

                val cobaltInstances = listOf(
                    "https://api.cobalt.tools",
                    "https://co.wuk.sh",
                    "https://cobalt.api.red"
                )
                for (baseUrl in cobaltInstances) {
                    try {
                        val jsonBody = """{"url":"$fullUrl"}"""
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val body = jsonBody.toRequestBody(mediaType)
                        val req = Request.Builder()
                            .url(baseUrl)
                            .header("Accept", "application/json")
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "Mozilla/5.0")
                            .post(body)
                            .build()
                        val resp = com.example.api.NetworkModule.okHttpClient.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val bodyStr = resp.body?.string() ?: ""
                            val json = com.example.api.NetworkModule.json.parseToJsonElement(bodyStr).jsonObject
                            val status = json["status"]?.jsonPrimitive?.content ?: ""
                            val directUrl = json["url"]?.jsonPrimitive?.content
                            val pickerUrl = json["picker"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            val finalUrl = if ((status == "tunnel" || status == "redirect" || status == "stream") && !directUrl.isNullOrEmpty()) directUrl else pickerUrl

                            if (!finalUrl.isNullOrEmpty()) {
                                val streamInfo = StreamInfo(
                                    videoId = cleanId,
                                    title = "فيديو يوتيوب",
                                    primaryPlayUrl = finalUrl,
                                    videoUrl = finalUrl,
                                    audioUrl = null,
                                    uploader = "",
                                    thumbnailUrl = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                                    durationSeconds = 0L
                                )
                                streamInfoCache.put(cleanId, streamInfo)
                                return@withContext streamInfo
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Cobalt instance $baseUrl failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cobalt stream resolve failed: ${e.message}")
            }
            
            throw Exception("تعذر جلب روابط التشغيل. يرجى محاولة فتح رابط مباشر أو محاولة البحث مجدداً.")
        } catch (e: Exception) {
            Log.e(TAG, "getStreams failed completely: ${e.message}", e)
            throw Exception("${e.message}")
        }
    }
}
