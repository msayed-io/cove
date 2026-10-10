package com.example.data

import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.DownloadItem
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.util.regex.Pattern

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class DownloadRepository {

    val downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())

    init {
        scanDownloadedFiles()
    }

    fun getCoveBaseDir(context: android.content.Context? = null): File {
        val targetCtx = context ?: com.example.api.NetworkModule.applicationContext
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                File(Environment.getExternalStorageDirectory(), "Cove")
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Cove")
            }
        } else {
            File(Environment.getExternalStorageDirectory(), "Cove")
        }
        
        // Test if we can write to this directory. If not, use external files dir as robust fallback.
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            val testFile = File(baseDir, ".write_test_" + System.currentTimeMillis())
            if (testFile.createNewFile()) {
                testFile.delete()
                return baseDir
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Cannot write to public directory, falling back to app-specific external storage", e)
        }
        
        // Fallback to app-specific external storage which is always writable without permissions
        if (targetCtx != null) {
            val appSpecificDir = File(targetCtx.getExternalFilesDir(null), "Cove")
            if (!appSpecificDir.exists()) {
                appSpecificDir.mkdirs()
            }
            return appSpecificDir
        }
        return baseDir
    }

    fun scanDownloadedFiles() {
        try {
            // Capture currently active downloading items or failed items so they don't disappear from the UI
            val activeItems = downloadsFlow.value.filter { 
                val isDownloading = it.progress < 1f && !it.statusText.contains("فشل") && it.statusText != "اكتمل بنجاح"
                val isFailed = it.statusText.contains("فشل")
                isDownloading || isFailed
            }
            val activePaths = activeItems.mapNotNull { it.localUri }.toSet()

            val items = mutableListOf<DownloadItem>()
            val subFolders = listOf("Cove Video", "Cove Audio", "Cove Image")
            
            // Scan the root directory, public downloads folder, and app-specific files directory so no files are lost
            val rootDir = File(Environment.getExternalStorageDirectory(), "Cove")
            val fallbackDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Cove")
            val appSpecificDir = com.example.api.NetworkModule.applicationContext?.let { File(it.getExternalFilesDir(null), "Cove") }
            
            val directoriesToScan = mutableListOf<File>()
            directoriesToScan.add(rootDir)
            directoriesToScan.add(fallbackDir)
            if (appSpecificDir != null) {
                directoriesToScan.add(appSpecificDir)
            }
            
            val scannedPaths = mutableSetOf<String>()

            for (baseDir in directoriesToScan) {
                if (!baseDir.exists()) continue
                for (subFolderName in subFolders) {
                    val folder = File(baseDir, subFolderName)
                    if (folder.exists() && folder.isDirectory) {
                        val files = folder.listFiles() ?: continue
                        for (file in files) {
                            if (file.isFile && file.length() > 0 && !scannedPaths.contains(file.absolutePath)) {
                                scannedPaths.add(file.absolutePath)
                                
                                // If this path is currently being downloaded actively, do NOT replace it with a completed state from disk scan
                                if (activePaths.contains(file.absolutePath)) {
                                    continue
                                }

                                val name = file.name
                                val isVideo = subFolderName == "Cove Video"
                                val isAudio = subFolderName == "Cove Audio"
                                
                                var cleanTitle = name
                                if (name.startsWith("Media_")) {
                                    val parts = name.split("_")
                                    if (parts.size >= 3) {
                                        val titleWithExt = parts.drop(2).joinToString("_")
                                        cleanTitle = titleWithExt.substringBeforeLast(".")
                                    }
                                }
                                if (cleanTitle.isEmpty()) {
                                    cleanTitle = name.substringBeforeLast(".")
                                }
                                
                                val companionThumb = File(file.parentFile, file.name.substringBeforeLast(".") + ".thumb.jpg")
                                val thumbPath = if (companionThumb.exists()) companionThumb.absolutePath else (if (!isVideo && !isAudio) file.absolutePath else null)

                                val item = DownloadItem(
                                    id = file.absolutePath.hashCode().toLong(),
                                    title = if (isAudio) "$cleanTitle (صوت)" else cleanTitle,
                                    mediaUrl = "",
                                    isVideo = isVideo,
                                    statusText = "اكتمل بنجاح",
                                    progress = 1f,
                                    localUri = file.absolutePath,
                                    timestamp = file.lastModified(),
                                    thumbnailUrl = thumbPath
                                )
                                items.add(item)
                            }
                        }
                    }
                }
            }
            
            // Add all the active items to the final scanned list
            items.addAll(activeItems)
            
            items.sortByDescending { it.timestamp }
            downloadsFlow.value = items
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to scan downloaded files", e)
        }
    }

    private fun isDirectFileUrl(url: String): Boolean {
        val cleanUrl = url.lowercase().substringBefore("?").substringBefore("#")
        return cleanUrl.endsWith(".jpg") || cleanUrl.endsWith(".jpeg") ||
               cleanUrl.endsWith(".png") || cleanUrl.endsWith(".webp") ||
               cleanUrl.endsWith(".gif") || cleanUrl.endsWith(".mp4") ||
               cleanUrl.endsWith(".webm") || cleanUrl.endsWith(".mkv") ||
               cleanUrl.endsWith(".mov") || cleanUrl.endsWith(".m4a") ||
               cleanUrl.endsWith(".mp3") ||
               url.contains(".jpg?") || url.contains(".jpeg?") ||
               url.contains(".png?") || url.contains(".webp?") ||
               url.contains(".mp4?") || url.contains(".m4a?") || url.contains(".mp3?")
    }

    private fun extractYouTubeVideoId(url: String): String? {
        val clean = url.trim()
        if (!clean.contains("youtube.com") && !clean.contains("youtu.be")) return null
        return try {
            when {
                clean.contains("youtu.be/") -> clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&").substringBefore("#")
                clean.contains("/shorts/") -> clean.substringAfter("/shorts/").substringBefore("?").substringBefore("&").substringBefore("#")
                clean.contains("/embed/") -> clean.substringAfter("/embed/").substringBefore("?").substringBefore("&").substringBefore("#")
                clean.contains("v=") -> clean.substringAfter("v=").substringBefore("&").substringBefore("?").substringBefore("#")
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchYouTubeOEmbed(url: String): com.example.domain.ScrapedMedia? = withContext(Dispatchers.IO) {
        try {
            val videoId = extractYouTubeVideoId(url) ?: return@withContext null
            val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            com.example.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext null
                    val json = com.example.api.NetworkModule.json.parseToJsonElement(body).jsonObject
                    val title = json["title"]?.jsonPrimitive?.content ?: "فيديو يوتيوب"
                    val author = json["author_name"]?.jsonPrimitive?.content ?: ""
                    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    val watchUrl = "https://www.youtube.com/watch?v=$videoId"
                    return@withContext com.example.domain.ScrapedMedia(
                        title = title,
                        mediaUrl = watchUrl,
                        isVideo = true,
                        thumbnailUrl = thumbnailUrl,
                        sourceUrl = watchUrl,
                        items = listOf(com.example.domain.ScrapedItem(watchUrl, true, thumbnailUrl, title))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "YouTube oEmbed fallback failed: ${e.message}")
        }
        null
    }

    suspend fun fetchYouTubeStreamFromPiped(videoId: String, isVideo: Boolean, isAudioOnly: Boolean): String? = withContext(Dispatchers.IO) {
        val instances = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.mha.fi",
            "https://piped-api.garudalinux.org",
            "https://pipedapi.drgns.space",
            "https://pipedapi.astral.yous.org",
            "https://pipedapi.adminforge.de"
        )
        for (baseUrl in instances) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/streams/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                com.example.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = com.example.api.NetworkModule.json.parseToJsonElement(body).jsonObject
                        if (isAudioOnly) {
                            val audioStreams = json["audioStreams"]?.jsonArray
                            val bestAudio = audioStreams?.firstOrNull { elem ->
                                val mime = elem.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                                mime.contains("mp4") || mime.contains("m4a")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                                ?: audioStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!bestAudio.isNullOrEmpty()) return@withContext bestAudio
                        } else if (isVideo) {
                            val videoStreams = json["videoStreams"]?.jsonArray
                            val bestVideo = videoStreams?.firstOrNull { elem ->
                                val mime = elem.jsonObject["mimeType"]?.jsonPrimitive?.content ?: ""
                                mime.contains("mp4")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                                ?: videoStreams?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!bestVideo.isNullOrEmpty()) return@withContext bestVideo
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadRepository", "Piped $baseUrl failed: ${e.message}")
            }
        }
        null
    }

    suspend fun fetchYouTubeStreamFromInvidious(videoId: String, isVideo: Boolean, isAudioOnly: Boolean): String? = withContext(Dispatchers.IO) {
        val instances = listOf(
            "https://invidious.nerdvpn.de",
            "https://inv.tux.stream",
            "https://invidious.drgns.space",
            "https://vid.puffyan.us",
            "https://inv.us.projectsegfau.lt",
            "https://invidious.io.lol"
        )
        for (baseUrl in instances) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/v1/videos/$videoId")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                com.example.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = com.example.api.NetworkModule.json.parseToJsonElement(body).jsonObject
                        
                        if (isAudioOnly) {
                            val adaptive = json["adaptiveFormats"]?.jsonArray
                            val audioFormat = adaptive?.firstOrNull { elem ->
                                val type = elem.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                                type.contains("audio")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!audioFormat.isNullOrEmpty()) return@withContext audioFormat
                        } else {
                            val formatStreams = json["formatStreams"]?.jsonArray
                            val mp4Stream = formatStreams?.firstOrNull { elem ->
                                val container = elem.jsonObject["container"]?.jsonPrimitive?.content ?: ""
                                container.contains("mp4")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!mp4Stream.isNullOrEmpty()) return@withContext mp4Stream

                            val adaptive = json["adaptiveFormats"]?.jsonArray
                            val videoFormat = adaptive?.firstOrNull { elem ->
                                val type = elem.jsonObject["type"]?.jsonPrimitive?.content ?: ""
                                type.contains("video/mp4")
                            }?.jsonObject?.get("url")?.jsonPrimitive?.content
                            if (!videoFormat.isNullOrEmpty()) return@withContext videoFormat
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadRepository", "Invidious $baseUrl failed: ${e.message}")
            }
        }
        null
    }

    suspend fun fetchYouTubeStreamFromCobalt(url: String, isAudioOnly: Boolean): String? = withContext(Dispatchers.IO) {
        val instances = listOf(
            "https://api.cobalt.tools",
            "https://co.wuk.sh",
            "https://cobalt.api.red"
        )
        for (baseUrl in instances) {
            try {
                val jsonPayload = if (isAudioOnly) {
                    """{"url":"$url","downloadMode":"audio"}"""
                } else {
                    """{"url":"$url"}"""
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonPayload.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(baseUrl)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .post(body)
                    .build()
                com.example.api.NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use
                        val json = com.example.api.NetworkModule.json.parseToJsonElement(bodyStr).jsonObject
                        val status = json["status"]?.jsonPrimitive?.content ?: ""
                        val directUrl = json["url"]?.jsonPrimitive?.content
                        val pickerUrl = json["picker"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
                        val finalUrl = if ((status == "tunnel" || status == "redirect" || status == "stream") && !directUrl.isNullOrEmpty()) directUrl else pickerUrl
                        if (!finalUrl.isNullOrEmpty()) return@withContext finalUrl
                    }
                }
            } catch (e: Exception) {
                Log.w("DownloadRepository", "Cobalt $baseUrl failed: ${e.message}")
            }
        }
        null
    }

    suspend fun resolveDirectUrl(context: android.content.Context, url: String, isVideo: Boolean, isAudioOnly: Boolean): String? = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase()
        val isYouTube = lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")
        
        if (isYouTube) {
            // 1. Try NewPipe
            try {
                val streamExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(url)
                streamExtractor.fetchPage()
                
                if (isAudioOnly) {
                    val audioStreams = streamExtractor.audioStreams
                    val bestAudio = audioStreams?.firstOrNull { it.getFormat()?.getName()?.contains("m4a", true) == true }
                        ?: audioStreams?.firstOrNull { it.getFormat()?.getName()?.contains("mp3", true) == true }
                        ?: audioStreams?.firstOrNull()
                    if (bestAudio != null) {
                        Log.d("DownloadRepository", "Resolved YouTube audio stream via NewPipe: ${bestAudio.content}")
                        return@withContext bestAudio.content
                    }
                } else if (isVideo) {
                    val videoStreams = streamExtractor.videoStreams
                    // Prioritize muxed formats (formats with both video and audio)
                    val muxedVideo = videoStreams?.firstOrNull { it.getFormat()?.getName()?.contains("mp4", true) == true && !it.isVideoOnly }
                        ?: videoStreams?.firstOrNull { !it.isVideoOnly }
                        ?: videoStreams?.firstOrNull { it.getFormat()?.getName()?.contains("mp4", true) == true }
                        ?: videoStreams?.firstOrNull()
                    if (muxedVideo != null) {
                        Log.d("DownloadRepository", "Resolved YouTube muxed video stream via NewPipe: ${muxedVideo.content}")
                        return@withContext muxedVideo.content
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadRepository", "NewPipe resolveDirectUrl failed for $url: ${e.message}")
            }

            // 2. Try YoutubeDL with TV/mweb client fallback
            try {
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(context)
                } catch (e: Exception) {
                    Log.e("DownloadRepository", "YoutubeDL init error", e)
                }
                val request = YoutubeDLRequest(url)
                request.addOption("--extractor-args", "youtube:player_client=tv_embedded,mweb,ios,tv")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                request.addOption("-g")
                if (isAudioOnly) {
                    request.addOption("-f", "bestaudio[ext=m4a]/bestaudio/best")
                } else {
                    // Only request single stream containing both video AND audio (or single best muxed stream)
                    request.addOption("-f", "best[ext=mp4]/best[vcodec!=none][acodec!=none]/best")
                }
                val info = YoutubeDL.getInstance().getInfo(request)
                var directUrl = info.url
                if (directUrl == null && info.formats != null) {
                    directUrl = info.formats?.lastOrNull { (it.ext == "mp4" || it.ext == "m4a") && it.vcodec != "none" && it.acodec != "none" }?.url
                        ?: info.formats?.lastOrNull { it.ext == "mp4" }?.url
                }
                if (!directUrl.isNullOrEmpty()) {
                    Log.d("DownloadRepository", "Resolved YouTube stream via YoutubeDL TV client")
                    return@withContext directUrl
                }
            } catch (e: Exception) {
                Log.e("DownloadRepository", "YoutubeDL resolveDirectUrl failed: ${e.message}")
            }

            // 3. Fallback to Piped API
            val videoId = extractYouTubeVideoId(url)
            if (videoId != null) {
                val pipedUrl = fetchYouTubeStreamFromPiped(videoId, isVideo, isAudioOnly)
                if (pipedUrl != null) {
                    Log.d("DownloadRepository", "Resolved YouTube stream via Piped fallback")
                    return@withContext pipedUrl
                }

                // 4. Fallback to Invidious API
                val invidiousUrl = fetchYouTubeStreamFromInvidious(videoId, isVideo, isAudioOnly)
                if (invidiousUrl != null) {
                    Log.d("DownloadRepository", "Resolved YouTube stream via Invidious fallback")
                    return@withContext invidiousUrl
                }
            }

            // 5. Fallback to Cobalt API
            val cobaltUrl = fetchYouTubeStreamFromCobalt(url, isAudioOnly)
            if (cobaltUrl != null) {
                Log.d("DownloadRepository", "Resolved YouTube stream via Cobalt fallback")
                return@withContext cobaltUrl
            }
        }
        null
    }

    suspend fun downloadUrl(context: android.content.Context, title: String, url: String, isVideo: Boolean, isAudioOnly: Boolean, thumbnailUrl: String? = null): DownloadItem = withContext(Dispatchers.IO) {
        val extension = if (isAudioOnly) "mp3" else (if (isVideo) "mp4" else "jpg")
        val sanitizedTitle = title.take(30).replace(Regex("[^\\w\\s\\u0600-\\u06FF]"), "").trim()
        val fileName = "Media_${System.currentTimeMillis()}_$sanitizedTitle.$extension"
        
        val coveDir = getCoveBaseDir(context)
        val subDirName = if (isAudioOnly) "Cove Audio" else (if (isVideo) "Cove Video" else "Cove Image")
        val targetDir = File(coveDir, subDirName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val outputFile = File(targetDir, fileName)
        
        val taskId = System.currentTimeMillis().toString()
        val downloadId = taskId.hashCode().toLong()
        
        val newItem = DownloadItem(
            id = downloadId,
            title = if (isAudioOnly) "$title (صوت)" else title,
            mediaUrl = url,
            isVideo = isVideo && !isAudioOnly,
            statusText = "جاري التحضير...",
            progress = 0f,
            localUri = outputFile.absolutePath,
            thumbnailUrl = thumbnailUrl
        )
        
        val currentList = downloadsFlow.value.toMutableList()
        currentList.add(0, newItem)
        downloadsFlow.value = currentList
        
        try {
            // Resolve direct download URL first for maximum reliability and 100% video+audio merging bypass
            var targetUrl = url
            var isResolvedDirect = isDirectFileUrl(url)
            
            if (!isResolvedDirect) {
                val resolved = resolveDirectUrl(context, url, isVideo, isAudioOnly)
                if (resolved != null) {
                    targetUrl = resolved
                    isResolvedDirect = true
                    Log.d("DownloadRepository", "Resolved direct stream URL: $targetUrl")
                }
            }
            
            if (isResolvedDirect) {
                // Direct file download (Image, Video, or Audio) using OkHttp with optimized buffer
                val client = com.example.api.NetworkModule.okHttpClient
                val dlRequest = okhttp3.Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()
                
                client.newCall(dlRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("فشل الاتصال بالخادم: رمز الخطأ ${response.code}")
                    }
                    val body = response.body ?: throw Exception("محتوى الملف فارغ")
                    val totalBytes = body.contentLength()
                    val inputStream = body.byteStream()
                    val outputFileStream = java.io.FileOutputStream(outputFile)
                    
                    val buffer = ByteArray(262144) // 256 KB high-speed buffer
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgressUpdate = 0L
                    
                    val statusTextPrefix = if (isAudioOnly) "جاري تحميل الصوت..." else (if (isVideo) "جاري تحميل الفيديو..." else "جاري تحميل الصورة...")
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputFileStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 200) { // Throttle UI updates to 5Hz for high performance
                            lastProgressUpdate = now
                            if (totalBytes > 0) {
                                val progressPercent = totalRead.toFloat() / totalBytes.toFloat()
                                updateItem(downloadId) {
                                    it.copy(
                                        progress = progressPercent,
                                        statusText = statusTextPrefix
                                    )
                                }
                            } else {
                                updateItem(downloadId) {
                                    it.copy(
                                        progress = -1f,
                                        statusText = statusTextPrefix
                                    )
                                }
                            }
                        }
                    }
                    outputFileStream.flush()
                    outputFileStream.close()
                    inputStream.close()
                }
            } else {
                // Ensure YoutubeDL, FFmpeg, and Aria2c are fully initialized before starting download
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(context)
                    com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
                    com.yausername.aria2c.Aria2c.getInstance().init(context)
                } catch (e: Throwable) {
                    Log.e("DownloadRepository", "Failed to initialize YoutubeDL/FFmpeg/Aria2c", e)
                }

                // Video/Audio download acceleration using YoutubeDL
                val request = YoutubeDLRequest(targetUrl)
                request.addOption("--extractor-args", "youtube:player_client=tv,mweb,ios,android,web")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                request.addOption("--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                request.addOption("--add-header", "Referer: https://www.youtube.com/")
                request.addOption("--buffer-size", "1024k")
                request.addOption("--concurrent-fragments", "16")

                if (isAudioOnly) {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("--extract-audio")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                    request.addOption("--embed-thumbnail")
                    request.addOption("--add-metadata")
                } else if (isVideo) {
                    // Critical: download highest quality MP4 format containing BOTH audio and video, or merge via best format
                    request.addOption("-f", "best[ext=mp4]/bestvideo[ext=mp4]+bestaudio[ext=m4a]/best")
                    request.addOption("--merge-output-format", "mp4")
                }
                
                // Ensure ffmpeg is located by YoutubeDL
                try {
                    val ffmpegDir = java.io.File(context.noBackupFilesDir, "youtubedl-android/packages/ffmpeg")
                    if (ffmpegDir.exists()) {
                        request.addOption("--ffmpeg-location", ffmpegDir.absolutePath)
                    }
                } catch (e: Exception) {
                    Log.w("DownloadRepository", "Failed to check ffmpeg package dir", e)
                }

                request.addOption("-o", outputFile.absolutePath)
                
                try {
                    YoutubeDL.getInstance().execute(request, taskId) { progress, _, _ ->
                        val p = progress / 100f
                        updateItem(downloadId) {
                            it.copy(
                                progress = if (p < 0) 0f else p,
                                statusText = "جاري التحميل..."
                            )
                        }
                    }
                } catch (ytdlErr: Exception) {
                    Log.w("DownloadRepository", "YoutubeDL execute failed: ${ytdlErr.message}. Attempting fallback direct stream download...")
                    val emergencyUrl = resolveDirectUrl(context, url, isVideo, isAudioOnly)
                    if (!emergencyUrl.isNullOrEmpty()) {
                        val client = com.example.api.NetworkModule.okHttpClient
                        val dlRequest = okhttp3.Request.Builder()
                            .url(emergencyUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                            .build()
                        client.newCall(dlRequest).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("فشل الاتصال بالخادم: ${response.code}")
                            val body = response.body ?: throw Exception("محتوى الملف فارغ")
                            val totalBytes = body.contentLength()
                            val inputStream = body.byteStream()
                            val outputFileStream = java.io.FileOutputStream(outputFile)
                            val buffer = ByteArray(131072)
                            var bytesRead: Int
                            var totalRead = 0L
                            var lastProgressUpdate = 0L
                            val statusTextPrefix = if (isAudioOnly) "جاري تحميل الصوت..." else (if (isVideo) "جاري تحميل الفيديو..." else "جاري تحميل الصورة...")
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputFileStream.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 200) {
                                    lastProgressUpdate = now
                                    val progressPercent = if (totalBytes > 0) totalRead.toFloat() / totalBytes.toFloat() else -1f
                                    updateItem(downloadId) { it.copy(progress = progressPercent, statusText = statusTextPrefix) }
                                }
                            }
                            outputFileStream.flush()
                            outputFileStream.close()
                            inputStream.close()
                        }
                    } else {
                        throw ytdlErr
                    }
                }
            }
            
            // Save companion thumbnail if provided (crucial for local album art and gallery covers)
            if (!thumbnailUrl.isNullOrEmpty() && outputFile.exists()) {
                try {
                    val companionThumb = File(outputFile.parentFile, outputFile.name.substringBeforeLast(".") + ".thumb.jpg")
                    if (!companionThumb.exists()) {
                        val thumbReq = okhttp3.Request.Builder()
                            .url(thumbnailUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        com.example.api.NetworkModule.okHttpClient.newCall(thumbReq).execute().use { tResp ->
                            if (tResp.isSuccessful) {
                                tResp.body?.byteStream()?.use { input ->
                                    java.io.FileOutputStream(companionThumb).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                } catch (te: Exception) {
                    Log.w("DownloadRepository", "Could not cache companion thumbnail: ${te.message}")
                }
            }

            // Complete
            val finalThumb = if (!thumbnailUrl.isNullOrEmpty()) {
                val companionThumb = File(outputFile.parentFile, outputFile.name.substringBeforeLast(".") + ".thumb.jpg")
                if (companionThumb.exists()) companionThumb.absolutePath else thumbnailUrl
            } else if (!isVideo && !isAudioOnly) {
                outputFile.absolutePath
            } else null

            updateItem(downloadId) {
                it.copy(
                    progress = 1f,
                    statusText = "اكتمل بنجاح",
                    thumbnailUrl = finalThumb
                )
            }

            // Immediately scan the downloaded file to notify the media provider (Gallery)
            if (outputFile.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outputFile.absolutePath),
                    null
                ) { path, uri ->
                    Log.d("DownloadRepository", "Media scan completed for path: $path, uri: $uri")
                }
            }
            
            return@withContext newItem.copy(thumbnailUrl = finalThumb)
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Download failed for $url", e)
            updateItem(downloadId) {
                it.copy(
                    statusText = "فشل التحميل: ${e.message}"
                )
            }
            throw Exception("فشل التحميل: ${e.message}")
        }
    }

    private fun updateItem(id: Long, update: (DownloadItem) -> DownloadItem) {
        val currentList = downloadsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            currentList[index] = update(currentList[index])
            downloadsFlow.value = currentList
        }
    }

    fun deleteItem(id: Long) {
        val currentList = downloadsFlow.value.toMutableList()
        val item = currentList.find { it.id == id }
        if (item != null) {
            item.localUri?.let { path ->
                try {
                    val file = File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("DownloadRepository", "Failed to delete file on disk", e)
                }
            }
            currentList.removeAll { it.id == id }
            downloadsFlow.value = currentList
        }
    }

    private fun extractInstagramShortcode(url: String): String? {
        val patterns = listOf(
            Pattern.compile("instagram\\.com/p/([^/?#]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("instagram\\.com/reel/([^/?#]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("instagram\\.com/tv/([^/?#]+)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun extractThreadsPostId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("threads\\.(?:net|com)/@[^/]+/post/([^/?#]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("threads\\.(?:net|com)/t/([^/?#]+)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun fetchInstagramEmbedMedia(shortcode: String): com.example.domain.ScrapedMedia? {
        try {
            val embedUrl = "https://www.instagram.com/p/$shortcode/embed/"
            val client = com.example.api.NetworkModule.okHttpClient
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                val hasVideoMeta = html.contains("property=\"og:video\"") || html.contains("property='og:video'") || html.contains("name=\"og:video\"") || html.contains("name='og:video'") || html.contains("og:video:secure_url") || html.contains("og:video:url")
                val hasIsVideoTrue = html.contains("\"is_video\":true") || html.contains("\"is_video\": true") || html.contains("'is_video':true") || html.contains("'is_video': true")
                val isVideo = hasVideoMeta || hasIsVideoTrue

                var videoUrl: String? = null
                if (isVideo) {
                    videoUrl = findMetaTag(html, "og:video") ?: findMetaTag(html, "og:video:secure_url")
                    if (videoUrl == null || videoUrl.isEmpty()) {
                        val videoPattern = Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"")
                        val m = videoPattern.matcher(html)
                        if (m.find()) {
                            videoUrl = m.group(1).replace("\\u0026", "&")
                        }
                    }
                }
                
                var imageUrl = findMetaTag(html, "og:image") ?: findMetaTag(html, "og:image:secure_url")
                if (imageUrl == null || imageUrl.isEmpty()) {
                    val imgPattern = Pattern.compile("\"display_url\"\\s*:\\s*\"([^\"]+)\"")
                    val m = imgPattern.matcher(html)
                    if (m.find()) {
                        imageUrl = m.group(1).replace("\\u0026", "&")
                    }
                }
                if (imageUrl == null || imageUrl.isEmpty()) {
                    val classPattern = Pattern.compile("EmbeddedMediaImage[^>]*src=\"([^\"]+)\"")
                    val m = classPattern.matcher(html)
                    if (m.find()) {
                        imageUrl = m.group(1).replace("&amp;", "&")
                    }
                }
                
                if (imageUrl.isNullOrEmpty() && (!isVideo || videoUrl.isNullOrEmpty())) {
                    return null
                }
                
                val finalUrl = if (isVideo) videoUrl!! else imageUrl!!
                
                return com.example.domain.ScrapedMedia(
                    title = "انستجرام - $shortcode",
                    mediaUrl = finalUrl,
                    isVideo = isVideo,
                    thumbnailUrl = imageUrl ?: "",
                    sourceUrl = "https://www.instagram.com/p/$shortcode/",
                    items = listOf(com.example.domain.ScrapedItem(
                        url = finalUrl,
                        isVideo = isVideo,
                        thumbnailUrl = imageUrl ?: "",
                        title = "انستجرام - $shortcode"
                    ))
                )
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Instagram embed scraper failed for $shortcode", e)
        }
        return null
    }

    private fun fetchThreadsEmbedMedia(postId: String): com.example.domain.ScrapedMedia? {
        try {
            val embedUrl = "https://www.threads.net/t/$postId/embed/"
            val client = com.example.api.NetworkModule.okHttpClient
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                val hasVideoMeta = html.contains("property=\"og:video\"") || html.contains("property='og:video'") || html.contains("name=\"og:video\"") || html.contains("name='og:video'") || html.contains("og:video:secure_url") || html.contains("og:video:url")
                val hasIsVideoTrue = html.contains("\"is_video\":true") || html.contains("\"is_video\": true") || html.contains("'is_video':true") || html.contains("'is_video': true")
                val isVideo = hasVideoMeta || hasIsVideoTrue

                var videoUrl: String? = null
                if (isVideo) {
                    videoUrl = findMetaTag(html, "og:video") ?: findMetaTag(html, "og:video:secure_url")
                    if (videoUrl == null || videoUrl.isEmpty()) {
                        val videoPattern = Pattern.compile("\"video_url\"\\s*:\\s*\"([^\"]+)\"")
                        val m = videoPattern.matcher(html)
                        if (m.find()) {
                            videoUrl = m.group(1).replace("\\u0026", "&")
                        }
                    }
                }
                
                var imageUrl = findMetaTag(html, "og:image") ?: findMetaTag(html, "og:image:secure_url")
                if (imageUrl == null || imageUrl.isEmpty()) {
                    val imgPattern = Pattern.compile("\"display_url\"\\s*:\\s*\"([^\"]+)\"")
                    val m = imgPattern.matcher(html)
                    if (m.find()) {
                        imageUrl = m.group(1).replace("\\u0026", "&")
                    }
                }
                
                if (imageUrl.isNullOrEmpty() && (!isVideo || videoUrl.isNullOrEmpty())) {
                    return null
                }
                
                val finalUrl = if (isVideo) videoUrl!! else imageUrl!!
                
                return com.example.domain.ScrapedMedia(
                    title = "ثريدز - $postId",
                    mediaUrl = finalUrl,
                    isVideo = isVideo,
                    thumbnailUrl = imageUrl ?: "",
                    sourceUrl = "https://www.threads.net/t/$postId",
                    items = listOf(com.example.domain.ScrapedItem(
                        url = finalUrl,
                        isVideo = isVideo,
                        thumbnailUrl = imageUrl ?: "",
                        title = "ثريدز - $postId"
                    ))
                )
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Threads embed scraper failed for $postId", e)
        }
        return null
    }

    private suspend fun fetchCobaltUrl(videoUrl: String, isAudioOnly: Boolean = false): String? = withContext(Dispatchers.IO) {
        // Cobalt API is dead/auth-required, returning null
        null
    }

    suspend fun fetchMediaInfo(url: String): com.example.domain.ScrapedMedia = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase().trim()
        
        // 0. Filter out search engines and empty URLs
        if (lowerUrl.isEmpty() || lowerUrl.contains("google.com/search") || lowerUrl.contains("google.com/?") || lowerUrl.contains("bing.com/search") || lowerUrl.contains("duckduckgo.com/?")) {
            throw Exception("الرابط المدخل عبارة عن صفحة بحث وليس فيديو أو ملف وسائط.")
        }
        
        // 1. Check if the URL is a direct file link (extremely fast, zero overhead)
        val cleanDirect = lowerUrl.substringBefore("?").substringBefore("#")
        if (cleanDirect.endsWith(".jpg") || cleanDirect.endsWith(".jpeg") || cleanDirect.endsWith(".png") || cleanDirect.endsWith(".webp") || cleanDirect.endsWith(".gif") ||
            lowerUrl.contains(".jpg?") || lowerUrl.contains(".jpeg?") || lowerUrl.contains(".png?") || lowerUrl.contains(".webp?")) {
            val title = cleanDirect.substringAfterLast("/")
            return@withContext com.example.domain.ScrapedMedia(
                title = if (title.length > 3) title else "صورة مشتركة",
                mediaUrl = url,
                isVideo = false,
                thumbnailUrl = url,
                sourceUrl = url,
                items = listOf(com.example.domain.ScrapedItem(url, false, url, if (title.length > 3) title else "صورة مشتركة"))
            )
        }
        if (cleanDirect.endsWith(".mp4") || cleanDirect.endsWith(".webm") || cleanDirect.endsWith(".mkv") || cleanDirect.endsWith(".mov") ||
            lowerUrl.contains(".mp4?") || lowerUrl.contains(".webm?")) {
            val title = cleanDirect.substringAfterLast("/")
            return@withContext com.example.domain.ScrapedMedia(
                title = if (title.length > 3) title else "فيديو مشترك",
                mediaUrl = url,
                isVideo = true,
                thumbnailUrl = "",
                sourceUrl = url,
                items = listOf(com.example.domain.ScrapedItem(url, true, "", if (title.length > 3) title else "فيديو مشترك"))
            )
        }

        // 2. High-reliability embed scraper for Instagram & Threads
        if (lowerUrl.contains("instagram.com")) {
            val shortcode = extractInstagramShortcode(url)
            if (shortcode != null) {
                val media = fetchInstagramEmbedMedia(shortcode)
                if (media != null) {
                    return@withContext media
                }
            }
        }
        if (lowerUrl.contains("threads.net") || lowerUrl.contains("threads.com")) {
            val postId = extractThreadsPostId(url)
            if (postId != null) {
                val media = fetchThreadsEmbedMedia(postId)
                if (media != null) {
                    return@withContext media
                }
            }
        }

        val isYouTube = lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")
        
        // 3. YouTube URLs handling
        if (isYouTube) {
            // A. NewPipe Extractor
            try {
                val streamExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getStreamExtractor(url)
                streamExtractor.fetchPage()
                
                val videoStreams = streamExtractor.videoStreams
                val bestVideo = videoStreams?.firstOrNull { it.getFormat()?.getName()?.contains("mp4", true) == true } ?: videoStreams?.firstOrNull()
                val isVideoInfo = bestVideo != null
                val finalUrl = bestVideo?.content ?: url
                
                return@withContext com.example.domain.ScrapedMedia(
                    title = streamExtractor.name ?: "بدون عنوان",
                    mediaUrl = url,
                    isVideo = isVideoInfo,
                    thumbnailUrl = streamExtractor.thumbnails?.firstOrNull()?.url ?: "",
                    sourceUrl = url,
                    items = listOf(com.example.domain.ScrapedItem(finalUrl, isVideoInfo, streamExtractor.thumbnails?.firstOrNull()?.url ?: "", streamExtractor.name ?: ""))
                )
            } catch (e: Exception) {
                Log.e("DownloadRepository", "NewPipe fetchMediaInfo failed for $url: ${e.message}")
            }

            // B. YoutubeDL with tv,mweb player clients
            try {
                try {
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().init(com.example.api.NetworkModule.applicationContext!!)
                } catch (e: Exception) {
                    Log.e("DownloadRepository", "Failed to init YoutubeDL for media info", e)
                }
                val request = YoutubeDLRequest(url)
                request.addOption("--extractor-args", "youtube:player_client=tv,mweb,ios,android,web")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                val info = YoutubeDL.getInstance().getInfo(request)
                
                val ext = info.ext?.lowercase() ?: ""
                val isVideoInfo = ext != "jpg" && ext != "jpeg" && ext != "png" && ext != "webp" && ext != "gif"
                
                return@withContext com.example.domain.ScrapedMedia(
                    title = info.title ?: "بدون عنوان",
                    mediaUrl = url,
                    isVideo = isVideoInfo,
                    thumbnailUrl = info.thumbnail ?: "",
                    sourceUrl = url,
                    items = listOf(com.example.domain.ScrapedItem(url, isVideoInfo, info.thumbnail ?: "", info.title ?: ""))
                )
            } catch (e: Exception) {
                Log.e("DownloadRepository", "YoutubeDL fetchMediaInfo failed for url: $url: ${e.message}")
            }

            // C. YouTube oEmbed Fallback (Guaranteed Title & Thumbnail)
            val oembedMedia = fetchYouTubeOEmbed(url)
            if (oembedMedia != null) {
                Log.d("DownloadRepository", "Retrieved YouTube media info via oEmbed fallback")
                return@withContext oembedMedia
            }
        }

        // 4. Try fast HTML scraper for non-YouTube URLs (Facebook, Twitter, etc.)
        if (!isYouTube) {
            val fastMedia = fetchMediaInfoFast(url)
            if (fastMedia != null) {
                Log.d("DownloadRepository", "Fast media scraper succeeded for $url")
                return@withContext fastMedia
            }
        }

        // 5. Fallback to youtube-dl for other platforms
        try {
            try {
                com.yausername.youtubedl_android.YoutubeDL.getInstance().init(com.example.api.NetworkModule.applicationContext!!)
            } catch (e: Exception) {
                Log.e("DownloadRepository", "Failed to init YoutubeDL for media info", e)
            }
            val request = YoutubeDLRequest(url)
            request.addOption("--no-check-certificates")
            val info = YoutubeDL.getInstance().getInfo(request)
            
            val ext = info.ext?.lowercase() ?: ""
            val isVideoInfo = ext != "jpg" && ext != "jpeg" && ext != "png" && ext != "webp" && ext != "gif"
            
            return@withContext com.example.domain.ScrapedMedia(
                title = info.title ?: "بدون عنوان",
                mediaUrl = url,
                isVideo = isVideoInfo,
                thumbnailUrl = info.thumbnail ?: "",
                sourceUrl = url,
                items = listOf(com.example.domain.ScrapedItem(url, isVideoInfo, info.thumbnail ?: "", info.title ?: ""))
            )
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Failed to fetch media info for url: $url", e)
            
            // Final Graceful Fallback for Instagram & Threads
            if (lowerUrl.contains("instagram.com") || lowerUrl.contains("threads.net") || lowerUrl.contains("threads.com")) {
                val isInstagram = lowerUrl.contains("instagram.com")
                val platformName = if (isInstagram) "انستجرام" else "ثريدز"
                val guessedTitle = "وسائط من $platformName"
                
                val isVideoPost = lowerUrl.contains("/reel") || lowerUrl.contains("/tv") || lowerUrl.contains("/reels") || lowerUrl.contains("/t/")
                
                return@withContext com.example.domain.ScrapedMedia(
                    title = guessedTitle,
                    mediaUrl = url,
                    isVideo = isVideoPost,
                    thumbnailUrl = "",
                    sourceUrl = url,
                    items = listOf(com.example.domain.ScrapedItem(url, isVideoPost, "", guessedTitle))
                )
            }
            
            val msg = e.message ?: ""
            if (msg.contains("Unsupported URL") || msg.contains("generic information extractor")) {
                throw Exception("الرابط غير مدعوم أو لا يحتوي على وسائط قابلة للتنزيل.")
            }
            throw Exception("عذراً، فشل جلب معلومات الوسائط: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun findMetaTag(html: String, attributeValue: String): String? {
        val patterns = listOf(
            Pattern.compile("<meta\\s+[^>]*property=[\"']$attributeValue[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"']\\s+[^>]*property=[\"']$attributeValue[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*name=[\"']$attributeValue[\"']\\s+[^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"']\\s+[^>]*name=[\"']$attributeValue[\"']", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in patterns) {
            val m = pattern.matcher(html)
            if (m.find()) {
                return m.group(1)
            }
        }
        return null
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun fetchMediaInfoFast(url: String): com.example.domain.ScrapedMedia? {
        try {
            val client = com.example.api.NetworkModule.okHttpClient
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                
                var title = findMetaTag(html, "og:title") ?: findMetaTag(html, "twitter:title")
                if (title == null || title.isEmpty()) {
                    val m = Pattern.compile("<title>([^<]+)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
                    if (m.find()) {
                        title = m.group(1)
                    }
                }
                title = title?.let { decodeHtmlEntities(it) } ?: "وسائط مشتركة"
                
                var imageUrl = findMetaTag(html, "og:image") ?: findMetaTag(html, "twitter:image") ?: findMetaTag(html, "og:image:secure_url")
                imageUrl = imageUrl?.let { decodeHtmlEntities(it) } ?: ""
                
                var videoUrl = findMetaTag(html, "og:video") ?: findMetaTag(html, "og:video:url") ?: findMetaTag(html, "og:video:secure_url") ?: findMetaTag(html, "twitter:player")
                videoUrl = videoUrl?.let { decodeHtmlEntities(it) } ?: ""
                
                if (imageUrl.isEmpty() && videoUrl.isEmpty()) {
                    return null
                }
                
                val isVideo = videoUrl.isNotEmpty()
                val mediaUrlToUse = if (isVideo) videoUrl else imageUrl
                
                return com.example.domain.ScrapedMedia(
                    title = title,
                    mediaUrl = mediaUrlToUse,
                    isVideo = isVideo,
                    thumbnailUrl = imageUrl,
                    sourceUrl = url,
                    items = listOf(com.example.domain.ScrapedItem(
                        url = mediaUrlToUse,
                        isVideo = isVideo,
                        thumbnailUrl = imageUrl,
                        title = title
                    ))
                )
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Fast HTML scraper failed for $url", e)
        }
        return null
    }
}
