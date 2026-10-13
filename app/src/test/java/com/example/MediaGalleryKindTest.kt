package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaGalleryKindTest {
    @Test
    fun recognizesCommonImageFormats() {
        assertEquals(DownloadedMediaKind.IMAGE, kindFor("photo.jpeg"))
        assertEquals(DownloadedMediaKind.IMAGE, kindFor("photo.webp"))
        assertEquals(DownloadedMediaKind.IMAGE, kindFor("photo.png"))
        assertEquals(DownloadedMediaKind.IMAGE, kindFor("photo.JPEG", isVideo = true))
    }

    @Test
    fun recognizesVideoByMetadataOrFileExtension() {
        assertEquals(DownloadedMediaKind.VIDEO, kindFor("clip.mp4", isVideo = true))
        assertEquals(DownloadedMediaKind.VIDEO, kindFor("clip.mkv"))
        assertEquals(DownloadedMediaKind.VIDEO, kindFor("clip.webm"))
        assertEquals(DownloadedMediaKind.VIDEO, kindFor("extensionless", isVideo = true))
    }

    @Test
    fun recognizesCommonAudioFormats() {
        assertEquals(DownloadedMediaKind.AUDIO, kindFor("track.mp3"))
        assertEquals(DownloadedMediaKind.AUDIO, kindFor("track.m4a"))
        assertEquals(DownloadedMediaKind.AUDIO, kindFor("track.flac"))
    }

    @Test
    fun doesNotGuessUnknownFilesAreAudio() {
        assertEquals(DownloadedMediaKind.UNKNOWN, kindFor("document.bin"))
    }

    private fun kindFor(fileName: String, isVideo: Boolean = false): DownloadedMediaKind =
        downloadedMediaKind(
            DownloadItem(
                id = 1L,
                title = fileName,
                mediaUrl = "",
                isVideo = isVideo,
                statusText = "اكتمل بنجاح",
                progress = 1f,
                localUri = "/storage/emulated/0/Download/Cove/$fileName"
            )
        )
}
