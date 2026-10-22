package com.example.data

import java.io.File
import java.nio.charset.StandardCharsets

/** Builds a readable MP3 name from the source title without the app's timestamp prefix. */
internal fun audioFileName(title: String, collisionNumber: Int = 1): String {
    val cleaned = title
        .replace(Regex("[\\\\/\\u0000-\\u001F\\u007F]"), " ")
        .trim()
        .trimEnd('.')
        .ifBlank { "صوت" }

    val collisionSuffix = if (collisionNumber > 1) " ($collisionNumber)" else ""
    val suffixBytes = collisionSuffix.toByteArray(StandardCharsets.UTF_8).size
    val stem = truncateUtf8(cleaned, (240 - suffixBytes).coerceAtLeast(1))
        .trimEnd()
        .ifBlank { "صوت" }
    return "$stem$collisionSuffix.mp3"
}

/** Keeps identical titles from overwriting an existing recording. */
internal fun nextAudioOutputFile(directory: File, title: String): File {
    var collisionNumber = 1
    while (true) {
        val candidate = File(directory, audioFileName(title, collisionNumber))
        if (!candidate.exists()) return candidate
        collisionNumber++
    }
}

private fun truncateUtf8(value: String, maxBytes: Int): String {
    var index = 0
    var bytes = 0
    while (index < value.length) {
        val codePoint = value.codePointAt(index)
        val encodedLength = String(Character.toChars(codePoint))
            .toByteArray(StandardCharsets.UTF_8)
            .size
        if (bytes + encodedLength > maxBytes) break
        bytes += encodedLength
        index += Character.charCount(codePoint)
    }
    return value.substring(0, index)
}
