package net.orionx.messenger.videonote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Имя файла — единственный носитель метаданных кружка (схема сообщений на сервере
 * не менялась), поэтому парсинг имени покрыт тестами: ошибка здесь ломает и
 * определение «это кружок», и длительность в ленте.
 */
class VideoNoteTest {

    @Test
    fun `file name carries duration`() {
        val name = VideoNote.buildFileName(7_400L)
        assertTrue(name.startsWith("vnote_7400_"))
        assertTrue(name.endsWith(".mp4"))
        assertEquals(7_400L, VideoNote.durationMsFromName(name))
    }

    @Test
    fun `duration is clamped to the recording limit`() {
        val name = VideoNote.buildFileName(VideoNote.MAX_DURATION_MS * 3)
        assertEquals(VideoNote.MAX_DURATION_MS, VideoNote.durationMsFromName(name))
    }

    @Test
    fun `legacy circle attachments are still recognised`() {
        assertTrue(VideoNote.isVideoNote("video_circle_1699999999999.mp4", "video/mp4"))
        assertTrue(VideoNote.isVideoNote("circle_1699999999999.mp4", "video/mp4"))
        assertTrue(VideoNote.isVideoNote(VideoNote.buildFileName(3_000L), "video/mp4"))
    }

    @Test
    fun `legacy attachments report unknown duration instead of garbage`() {
        assertEquals(0L, VideoNote.durationMsFromName("video_circle_1699999999999.mp4"))
        assertEquals(0L, VideoNote.durationMsFromName(null))
    }

    @Test
    fun `ordinary videos and other media are not video notes`() {
        assertFalse(VideoNote.isVideoNote("holiday.mp4", "video/mp4"))
        assertFalse(VideoNote.isVideoNote("vnote_3000_1.mp4", "audio/mp4"))
        assertFalse(VideoNote.isVideoNote(null, null))
    }

    @Test
    fun `durations are formatted as minutes and seconds`() {
        assertEquals("0:07", VideoNote.formatDuration(7_400L))
        assertEquals("1:00", VideoNote.formatDuration(60_000L))
        assertEquals("0:00", VideoNote.formatDuration(-5L))
    }

    @Test
    fun `recording clock shows tenths only in the first ten seconds`() {
        assertEquals("0:03,4", VideoNote.formatRecordingClock(3_400L))
        assertEquals("0:12", VideoNote.formatRecordingClock(12_900L))
    }
}
