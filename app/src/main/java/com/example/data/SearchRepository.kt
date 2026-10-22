package com.example.data

import android.util.Log
import com.example.api.NetworkModule
import com.example.domain.SearchResult
import com.example.domain.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&q=$encodedQuery"
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
     * Playback-only resolver.
     *
     * Order of work (fastest first, so tapping a result starts playing immediately):
     *  1. NewPipe extractor — usually the quickest source that yields a merged stream.
     *  2. Lightweight public APIs (Piped / Invidious / Cobalt) raced **in parallel** with a hard
     *     per-provider timeout. The old sequential chain could keep the player spinning for tens
     *     of seconds; the race bounds the wait to a couple of seconds and cancels the losers.
     *  3. The complete legacy chain ([getStreams], including yt-dlp) as the last resort.
     *
     * DownloadViewModel keeps using [getStreams] unchanged, so download selection and
     * reliability are not affected by this fast path.
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
                cachePlaybackStream(cacheKey, info)
                Log.d(TAG, "Playback stream resolved by NewPipe in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms")
                return@withContext info
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Fast NewPipe playback resolution failed; racing parallel fallbacks", e)
        }

        // Parallel, time-bounded fallbacks (Piped / Invidious / Cobalt).
        val raceStartedAt = android.os.SystemClock.elapsedRealtime()
        try {
            val cleanId = extractCleanVideoId(source)
            val fullUrl = if (source.startsWith("http", ignoreCase = true)) {
                source
            } else {
                "https://www.youtube.com/watch?v=$source"
            }
            val racedUrl = racePlaybackFallbacks(fullUrl = fullUrl, cleanId = cleanId)
            if (!racedUrl.isNullOrBlank()) {
                val info = StreamInfo(
                    videoId = source,
                    title = "",
                    primaryPlayUrl = racedUrl,
                    videoUrl = racedUrl,
                    audioUrl = null,
                    uploader = "",
                    thumbnailUrl = "https://img.youtube.com/vi/$cleanId/hqdefault.jpg",
                    durationSeconds = 0L
                )
                cachePlaybackStream(cacheKey, info)
                Log.d(TAG, "Playback stream resolved by parallel fallback in ${android.os.SystemClock.elapsedRealtime() - raceStartedAt}ms")
                return@withContext info
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Parallel playback fallbacks failed", e)
        }

        val fallbackStartedAt = android.os.SystemClock.elapsedRealtime()
        val info = getStreams(source)
        if (!info?.primaryPlayUrl.isNullOrBlank()) {
            cachePlaybackStream(cacheKey, info!!)
            Log.d(TAG, "Playback fallback resolved in ${android.os.SystemClock.elapsedRealtime() - fallbackStartedAt}ms")
        }
        info
    }

    /** Keeps a resolved playback URL warm long enough for the search → tap flow. */
    private fun cachePlaybackStream(cacheKey: String, info: StreamInfo) {
        playbackStreamCache.put(
            cacheKey,
            CachedPlaybackStream(info, android.os.SystemClock.elapsedRealtime() + PLAYBACK_CACHE_TTL_MS)
        )
    }

    /** Extracts a bare video id whether the caller passed an id or a full URL. */
    private fun extractCleanVideoId(source: String): String {
        if (!source.startsWith("http", ignoreCase = true)) return source
        return source.substringAfter("v=", "")
            .substringBefore("&")
            .substringBefore("?")
            .ifBlank { source.substringAfterLast("/").substringBefore("?").substringBefore("&") }
    }

    /**
     * Races every lightweight fallback provider at once and returns the first direct URL.
     * A provider that fails or times out reports null and never blocks the others.
     */
    private suspend fun racePlaybackFallbacks(
        fullUrl: String,
        cleanId: String,
        timeoutMs: Long = PROVIDER_TIMEOUT_MS
    ): String? = coroutineScope {
        val producers = mutableListOf<suspend () -> String?>()
        FAST_PIPED_INSTANCES.forEach { producers += pipedPlayUrlProducer(it, cleanId) }
        FAST_INVIDIOUS_INSTANCES.forEach { producers += invidiousPlayUrlProducer(it, cleanId) }
        FAST_COBALT_INSTANCES.forEach { producers += cobaltPlayUrlProducer(it, fullUrl) }

        val results = Channel<String?>(producers.size)
        val jobs = producers.map { producer ->
            launch(Dispatchers.IO) {
                val value = try {
                    withTimeout(timeoutMs) { producer() }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Fallback provider timed out after ${timeoutMs}ms")
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback provider failed: ${e.message}")
                    null
                }
                try {
                    results.send(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // The race already finished (channel closed) — nothing left to report.
                }
            }
        }

        try {
            repeat(producers.size) {
                val candidate = results.receive()
                if (!candidate.isNullOrBlank()) return@coroutineScope candidate
            }
            null
        } finally {
            jobs.forEach { it.cancel() }
            results.close()
        }
    }

    /** Piped `/streams/{id}`: muxed (video+audio) first, then any video, then audio. */
    private fun pipedPlayUrlProducer(baseUrl: String, cleanId: String): suspend () -> String? = {
        val request = Request.Builder()
            .url("$baseUrl/streams/$cleanId")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val bodyString = response.body?.string().orEmpty()
                val json = NetworkModule.json.parseToJsonElement(bodyString).jsonObject
                val videoStreams = json["videoStreams"]?.jsonArray
                val audioStreams = json["audioStreams"]?.jsonArray

                val muxed = videoStreams?.firstOrNull { element ->
                    val mime = element.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                    val videoOnly = element.jsonObject["videoOnly"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    mime.contains("mp4") && !videoOnly
                }?.jsonObject?.get("url")?.jsonPrimitive?.content
                val anyVideo = videoStreams?.firstOrNull { element ->
                    val mime = element.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                    mime.contains("mp4")
                }?.jsonObject?.get("url")?.jsonPrimitive?.content
                val anyAudio = audioStreams?.firstOrNull { element ->
                    val mime = element.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                    mime.contains("mp4") || mime.contains("m4a")
                }?.jsonObject?.get("url")?.jsonPrimitive?.content

                muxed ?: anyVideo ?: anyAudio
            }
        }
    }

    /** Invidious `/api/v1/videos/{id}`: muxed `formatStreams` first, then adaptive formats. */
    private fun invidiousPlayUrlProducer(baseUrl: String, cleanId: String): suspend () -> String? = {
        val request = Request.Builder()
            .url("$baseUrl/api/v1/videos/$cleanId")
            .header("User-Agent", "Mozilla/5.0")
            .build()
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val bodyString = response.body?.string().orEmpty()
                val json = NetworkModule.json.parseToJsonElement(bodyString).jsonObject
                val formatStreams = json["formatStreams"]?.jsonArray
                val adaptiveFormats = json["adaptiveFormats"]?.jsonArray

                val muxed = formatStreams?.firstOrNull { element ->
                    val container = element.jsonObject["container"]?.jsonPrimitive?.content ?: ""
                    container.contains("mp4")
                }?.jsonObject?.get("url")?.jsonPrimitive?.content
                val adaptiveVideo = adaptiveFormats?.firstOrNull { element ->
                    val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                    type.contains("video/mp4")
                }?.jsonObject?.get("url")?.jsonPrimitive?.content
                val adaptiveAudio = adaptiveFormats?.firstOrNull { element ->
                    val type = element.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                    type.contains("audio")
                }?.jsonObject?.get("url")?.jsonPrimitive?.content

                muxed ?: adaptiveVideo ?: adaptiveAudio
            }
        }
    }

    /** Cobalt API: returns an already merged direct URL. */
    private fun cobaltPlayUrlProducer(baseUrl: String, fullUrl: String): suspend () -> String? = {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = """{"url":"$fullUrl"}""".toRequestBody(mediaType)
        val request = Request.Builder()
            .url(baseUrl)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Mozilla/5.0")
            .post(body)
            .build()
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val bodyString = response.body?.string().orEmpty()
                val json = NetworkModule.json.parseToJsonElement(bodyString).jsonObject
                val status = json["status"]?.jsonPrimitive?.content ?: ""
                val directUrl = json["url"]?.jsonPrimitive?.content
                val pickerUrl = json["picker"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                if ((status == "tunnel" || status == "redirect" || status == "stream") && !directUrl.isNullOrEmpty()) {
                    directUrl
                } else {
                    pickerUrl
                }
            }
        }
    }

    private companion object {
        /** Resolved playback URLs stay cached this long so search → tap stays instant. */
        private const val PLAYBACK_CACHE_TTL_MS = 300_000L

        /** Hard per-provider timeout inside the parallel race. */
        private const val PROVIDER_TIMEOUT_MS = 7_000L

        /** A reduced, reliable subset is enough — the race needs speed, not breadth. */
        private val FAST_PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.drgns.space"
        )
        private val FAST_INVIDIOUS_INSTANCES = listOf(
            "https://invidious.nerdvpn.de",
            "https://inv.tux.stream",
            "https://invidious.drgns.space"
        )
        private val FAST_COBALT_INSTANCES = listOf(
            "https://api.cobalt.tools",
            "https://co.wuk.sh"
        )
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
