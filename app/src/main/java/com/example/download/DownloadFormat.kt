package com.example.download

import java.util.Locale

/**
 * Arabic-aware formatters shared by the Compose UI and the foreground notification,
 * so both surfaces always display identical numbers.
 */
object DownloadFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /** "12.4 م.ب" — Arabic abbreviations with Latin digits for readability. */
    fun bytes(bytes: Long): String {
        if (bytes < 0) return ""
        return when {
            bytes >= GB -> String.format(Locale.US, "%.2f ج.ب", bytes / GB)
            bytes >= MB -> String.format(Locale.US, "%.1f م.ب", bytes / MB)
            bytes >= KB -> String.format(Locale.US, "%.0f ك.ب", bytes / KB)
            else -> "$bytes بايت"
        }
    }

    /** "2.4 م.ب/ث" — empty when the speed is unknown. */
    fun speed(bytesPerSec: Long): String =
        if (bytesPerSec <= 0L) "" else bytes(bytesPerSec) + "/ث"

    /** "متبقي 01:42" — empty when the ETA is unknown. */
    fun eta(seconds: Long): String {
        if (seconds <= 0L) return ""
        val totalMinutes = seconds / 60
        val secs = seconds % 60
        return if (totalMinutes >= 60) {
            String.format(Locale.US, "متبقي %02d:%02d:%02d", totalMinutes / 60, totalMinutes % 60, secs)
        } else {
            String.format(Locale.US, "متبقي %02d:%02d", totalMinutes, secs)
        }
    }

    /** "45%" — empty for indeterminate progress. */
    fun percent(progress: Float): String =
        if (progress < 0f) "" else "${(progress.coerceIn(0f, 1f) * 100).toInt()}%"
}
