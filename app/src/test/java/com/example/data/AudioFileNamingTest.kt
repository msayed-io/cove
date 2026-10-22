package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AudioFileNamingTest {
    @Test
    fun keepsTheArabicTitleAndDoesNotAddATimestamp() {
        assertEquals("تلاوة القرآن الكريم.mp3", audioFileName("تلاوة القرآن الكريم"))
    }

    @Test
    fun replacesOnlyCharactersThatCannotBeUsedSafelyInAPath() {
        assertEquals("سورة البقرة قراءة.mp3", audioFileName("سورة/البقرة\\قراءة"))
    }

    @Test
    fun addsANumberOnlyWhenTheSameFileAlreadyExists() {
        val directory = Files.createTempDirectory("cove-audio-name").toFile()
        try {
            val first = nextAudioOutputFile(directory, "تلاوة")
            assertEquals("تلاوة.mp3", first.name)
            assertTrue(first.createNewFile())
            assertEquals("تلاوة (2).mp3", nextAudioOutputFile(directory, "تلاوة").name)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun longTitlesStayWithinFilesystemNameLimitsWithoutSplittingEmoji() {
        val name = audioFileName("🎙️".repeat(100))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 244)
        assertTrue(name.endsWith(".mp3"))
    }
}
