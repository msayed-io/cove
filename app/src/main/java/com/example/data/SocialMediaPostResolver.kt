package com.example.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A directly downloadable item resolved from a public Instagram or Threads post. */
internal data class SocialPostMediaItem(
    val url: String,
    val isVideo: Boolean,
    val thumbnailUrl: String
)

/**
 * Resolves media from the structured JSON embedded in canonical post pages.
 *
 * This is deliberately separate from networking so the platform-specific matching,
 * carousel handling, and video-vs-cover rules can be regression-tested without Android.
 */
internal object SocialMediaPostResolver {
    private val json = Json { ignoreUnknownKeys = true }
    private val applicationJsonScript = Regex(
        """<script\b[^>]*type\s*=\s*["']application/json["'][^>]*>(.*?)</script\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val transformedImageToken = Regex(
        """(?:^|_)[sp]\d{2,5}x\d{2,5}(?:_|$)|(?:^|_)c\d+(?:\.\d+){2,}[a-z]?(?:_|$)""",
        RegexOption.IGNORE_CASE
    )
    private val imageResolutionToken = Regex(
        """(?:^|_)[sp](\d{2,5})x(\d{2,5})(?:_|$)""",
        RegexOption.IGNORE_CASE
    )

    fun resolveInstagramPage(html: String, shortcode: String): List<SocialPostMediaItem>? {
        val post = findInstagramPost(html, shortcode) ?: return null
        return resolvePostItems(post)
    }

    fun resolveThreadsPage(html: String, postCode: String): List<SocialPostMediaItem>? {
        val post = findThreadsPost(html, postCode) ?: return null
        return resolvePostItems(post)
    }

    private fun findInstagramPost(html: String, shortcode: String): JsonObject? =
        findPostInJsonScripts(html, shortcode) { root -> findInstagramMedia(root, shortcode) }

    private fun findThreadsPost(html: String, postCode: String): JsonObject? =
        findPostInJsonScripts(html, postCode) { root -> findThreadsMedia(root, postCode) }

    private fun findPostInJsonScripts(
        html: String,
        postCode: String,
        findTarget: (JsonElement) -> JsonObject?
    ): JsonObject? {
        for (match in applicationJsonScript.findAll(html)) {
            val script = match.groupValues.getOrNull(1) ?: continue
            if (!script.contains(postCode)) continue
            val root = try {
                json.parseToJsonElement(script)
            } catch (_: Exception) {
                continue
            }
            findTarget(root)?.let { return it }
        }
        return null
    }

    private fun findInstagramMedia(element: JsonElement, shortcode: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                if (element.stringValue("code") == shortcode &&
                    element.intValue("media_type") != null &&
                    (element.containsKey("image_versions2") ||
                        element.containsKey("video_versions") ||
                        element.containsKey("carousel_media"))
                ) {
                    return element
                }
                for (child in element.values) {
                    findInstagramMedia(child, shortcode)?.let { return it }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    findInstagramMedia(child, shortcode)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    /** Threads embeds the target as data.media; require its code and user object to match. */
    private fun findThreadsMedia(element: JsonElement, postCode: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                val data = element["data"] as? JsonObject
                val media = data?.get("media") as? JsonObject
                if (media?.stringValue("code") == postCode && media["user"] is JsonObject) {
                    return media
                }
                for (child in element.values) {
                    findThreadsMedia(child, postCode)?.let { return it }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    findThreadsMedia(child, postCode)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun resolvePostItems(post: JsonObject): List<SocialPostMediaItem>? {
        val mediaType = post.intValue("media_type")
        val carousel = post["carousel_media"] as? JsonArray
        val nodes = when {
            carousel != null && carousel.isNotEmpty() -> carousel
            mediaType == 8 -> return null // Never substitute a carousel cover for missing items.
            else -> JsonArray(listOf(post))
        }

        val result = mutableListOf<SocialPostMediaItem>()
        for (element in nodes) {
            val media = element as? JsonObject ?: return null
            // Do not silently omit a carousel child: callers should use the existing
            // extractor fallback rather than report a partial carousel as complete.
            result += resolveSingleItem(media) ?: return null
        }
        return result.takeIf { it.isNotEmpty() }
    }

    private fun resolveSingleItem(media: JsonObject): SocialPostMediaItem? {
        val mediaType = media.intValue("media_type")
        val videoVersions = media["video_versions"] as? JsonArray
        val hasVideoVersions = videoVersions?.isNotEmpty() == true
        val isVideo = mediaType == 2 || hasVideoVersions
        val imageUrl = bestImageUrl(media)
        val thumbnailUrl = imageUrl ?: media.httpsString("thumbnail_url").orEmpty()

        if (isVideo) {
            // A video cover is never a video fallback. Only accept an explicit MP4
            // rendition from video_versions; otherwise let the existing extractor run.
            val videoUrl = videoVersions
                ?.mapNotNull { (it as? JsonObject)?.httpsString("url") }
                ?.firstOrNull { it.pathEndsWithMp4() }
                ?: return null
            return SocialPostMediaItem(videoUrl, isVideo = true, thumbnailUrl = thumbnailUrl)
        }

        val directImageUrl = imageUrl ?: return null
        return SocialPostMediaItem(directImageUrl, isVideo = false, thumbnailUrl = directImageUrl)
    }

    private fun bestImageUrl(media: JsonObject): String? {
        val versions = media["image_versions2"] as? JsonObject ?: return null
        val candidates = (versions["candidates"] as? JsonArray)
            ?.mapNotNull { element ->
                val candidate = element as? JsonObject ?: return@mapNotNull null
                val url = candidate.httpsString("url") ?: return@mapNotNull null
                ImageCandidate(
                    url = url,
                    width = candidate.intValue("width"),
                    height = candidate.intValue("height")
                )
            }
            .orEmpty()
        if (candidates.isEmpty()) return null

        val originalWidth = media.intValue("original_width")
        val originalHeight = media.intValue("original_height")
        if (originalWidth != null && originalHeight != null) {
            candidates.firstOrNull {
                it.width == originalWidth && it.height == originalHeight
            }?.let { return it.url }
        }

        // Instagram's photo candidates often omit dimensions. Its canonical page
        // includes an untransformed `stp` candidate; transformed `s`/`p` sizes and
        // crops are previews. Prefer that original, then the largest known rendition.
        candidates.firstOrNull { isUntransformedImage(it.url) }?.let { return it.url }
        return candidates
            .filter { it.area > 0L }
            .maxByOrNull { it.area }
            ?.url
            ?: candidates.first().url
    }

    private fun isUntransformedImage(url: String): Boolean {
        val stp = queryParameter(url, "stp") ?: return true
        return !transformedImageToken.containsMatchIn(stp)
    }

    private fun queryParameter(url: String, name: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        for (part in query.split('&')) {
            val key = part.substringBefore('=')
            if (key != name) continue
            val rawValue = part.substringAfter('=', "")
            return try {
                java.net.URLDecoder.decode(rawValue, "UTF-8")
            } catch (_: Exception) {
                rawValue
            }
        }
        return null
    }

    private data class ImageCandidate(
        val url: String,
        val width: Int?,
        val height: Int?
    ) {
        val area: Long
            get() {
                val candidateWidth = width ?: return resolutionFromUrl(url)?.area ?: 0L
                val candidateHeight = height ?: return resolutionFromUrl(url)?.area ?: 0L
                return candidateWidth.toLong() * candidateHeight.toLong()
            }
    }

    private data class ImageResolution(val width: Int, val height: Int) {
        val area: Long get() = width.toLong() * height.toLong()
    }

    private fun resolutionFromUrl(url: String): ImageResolution? {
        val stp = queryParameter(url, "stp") ?: return null
        val match = imageResolutionToken.find(stp) ?: return null
        val width = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val height = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return ImageResolution(width, height)
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeUnless { it == "null" }

    private fun JsonObject.intValue(key: String): Int? = stringValue(key)?.toIntOrNull()

    private fun JsonObject.httpsString(key: String): String? =
        stringValue(key)?.takeIf { it.startsWith("https://", ignoreCase = true) }

    private fun String.pathEndsWithMp4(): Boolean {
        val path = substringBefore('?').substringBefore('#')
        return path.endsWith(".mp4", ignoreCase = true)
    }
}
