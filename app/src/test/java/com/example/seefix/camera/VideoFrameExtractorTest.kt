package com.example.seefix.camera

import com.example.seefix.ai.core.VideoContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameExtractorTest {

    @Test
    fun testShortDurationTimestampCalculation() {
        val extractor = VideoFrameExtractor(context = null)
        val durationMs = 3000L // 3s
        val timestamps = extractor.calculateSamplingTimestamps(durationMs)

        // clamp(5, 12, 3000 / 2000) = clamp(5, 12, 1) = 5
        assertEquals(5, timestamps.size)
        assertEquals(500L, timestamps.first())
        assertEquals(2500L, timestamps.last()) // max(500, 3000 - 500) = 2500
    }

    @Test
    fun testMediumDurationTimestampCalculation() {
        val extractor = VideoFrameExtractor(context = null)
        val durationMs = 20000L // 20s
        val timestamps = extractor.calculateSamplingTimestamps(durationMs)

        // clamp(5, 12, 20000 / 2000) = 10
        assertEquals(10, timestamps.size)
        assertEquals(500L, timestamps.first())
        assertEquals(19500L, timestamps.last()) // 20000 - 500
    }

    @Test
    fun testLongDurationTimestampCalculation() {
        val extractor = VideoFrameExtractor(context = null)
        val durationMs = 120000L // 120s
        val timestamps = extractor.calculateSamplingTimestamps(durationMs)

        // clamp(5, 12, 120000 / 2000) = clamp(5, 12, 60) = 12
        assertEquals(12, timestamps.size)
        assertEquals(500L, timestamps.first())
        assertEquals(119500L, timestamps.last()) // 120000 - 500
    }

    @Test
    fun testTimestampBoundsOrderAndNoDuplicates() {
        val extractor = VideoFrameExtractor(context = null)
        val testDurations = listOf(3000L, 8000L, 20000L, 60000L, 120000L)

        for (duration in testDurations) {
            val timestamps = extractor.calculateSamplingTimestamps(duration)
            val expectedStart = 500L
            val expectedEnd = maxOf(500L, duration - 500L)

            // Start & end margin bounds
            assertTrue("Timestamp should be >= start margin 500ms", timestamps.all { it >= expectedStart })
            assertTrue("Timestamp should be <= end margin $expectedEnd", timestamps.all { it <= expectedEnd })

            // Ascending order
            for (i in 0 until timestamps.size - 1) {
                assertTrue("Timestamps must be strictly ascending", timestamps[i] < timestamps[i + 1])
            }

            // No duplicates
            assertEquals("Timestamps must have no duplicates", timestamps.size, timestamps.distinct().size)
        }
    }

    @Test
    fun testConfigClamping() {
        val customConfig = VideoFrameExtractionConfig(minFrames = 3, maxFrames = 8)
        val extractor = VideoFrameExtractor(context = null, config = customConfig)

        // Short duration (1s): 1000 / 2000 = 0 -> clamped to minFrames (3)
        val shortTimestamps = extractor.calculateSamplingTimestamps(1000L)
        assertEquals(3, shortTimestamps.size)

        // Medium duration (10s): 10000 / 2000 = 5 -> in range [3, 8] -> 5
        val mediumTimestamps = extractor.calculateSamplingTimestamps(10000L)
        assertEquals(5, mediumTimestamps.size)

        // Long duration (100s): 100000 / 2000 = 50 -> clamped to maxFrames (8)
        val longTimestamps = extractor.calculateSamplingTimestamps(100000L)
        assertEquals(8, longTimestamps.size)
    }

    @Test
    fun testVideoContextPopulationWithMockExtractedFrames() = runTest {
        val extractor = VideoFrameExtractor(context = null)
        val mockUri = "file:///storage/emulated/0/movies/test_video.mp4"
        val mockDuration = 20000L

        val result = extractor.extractFrames(mockUri, durationMs = mockDuration)

        assertNotNull(result)
        assertEquals(mockUri, result.videoUri)
        assertEquals(mockDuration, result.durationMs)
        assertEquals(10, result.extractedCount)
        assertEquals(0, result.skippedCount)
        assertEquals(10, result.frames.size)

        // Populate VideoContext with extracted AIImage frames
        val videoContext = VideoContext(
            videoUri = result.videoUri,
            durationMs = result.durationMs,
            selectedFrames = result.frames
        )

        assertEquals(mockUri, videoContext.videoUri)
        assertEquals(mockDuration, videoContext.durationMs)
        assertEquals(10, videoContext.selectedFrames.size)

        val firstFrame = videoContext.selectedFrames.first()
        assertEquals("image/jpeg", firstFrame.mimeType)
        assertEquals(500L, firstFrame.timestampMs)
        assertTrue(firstFrame.uri?.contains("test_video.mp4#frame_500") == true)
    }
}
