package com.example.seefix.camera

import com.example.seefix.ai.core.VideoContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRecorderTest {

    @Test
    fun testInitialStateIsIdle() {
        val recorder = VideoRecorder(context = null)
        assertEquals(VideoRecordingState.Idle, recorder.recordingState.value)
        assertEquals(VideoRecordingState.Idle, recorder.state.value)
    }

    @Test
    fun testFileNamingGeneration() {
        val recorder = VideoRecorder(context = null)
        val testTimestamp = 1700000000000L
        val file = recorder.generateVideoFile(testTimestamp)

        assertNotNull(file)
        assertEquals("seefix_video_1700000000000.mp4", file.name)
        assertTrue(file.name.startsWith("seefix_video_"))
        assertTrue(file.name.endsWith(".mp4"))
    }

    @Test
    fun testStateTransitions() {
        val recorder = VideoRecorder(context = null)

        // 1. Idle -> Start
        recorder.startRecording()
        val recordingState = recorder.recordingState.value
        assertTrue(recordingState is VideoRecordingState.Recording)
        assertEquals(0L, (recordingState as VideoRecordingState.Recording).durationMs)

        // 2. Recording -> Stop
        recorder.stopRecording()
        assertTrue(recorder.recordingState.value is VideoRecordingState.Stopping || recorder.recordingState.value is VideoRecordingState.Completed || recorder.recordingState.value is VideoRecordingState.Idle)

        // 3. Cancel -> Idle
        recorder.cancelRecording()
        assertEquals(VideoRecordingState.Idle, recorder.recordingState.value)

        // 4. State flow updates
        recorder.updateStateForTest(VideoRecordingState.Starting)
        assertEquals(VideoRecordingState.Starting, recorder.recordingState.value)

        recorder.updateStateForTest(VideoRecordingState.Stopping)
        assertEquals(VideoRecordingState.Stopping, recorder.recordingState.value)

        val errorState = VideoRecordingState.Error("Test error", IllegalStateException("Camera busy"))
        recorder.updateStateForTest(errorState)
        assertEquals(errorState, recorder.recordingState.value)
        assertEquals("Test error", (recorder.recordingState.value as VideoRecordingState.Error).message)
    }

    @Test
    fun testVideoContextInstantiation() {
        val testUri = "file:///storage/emulated/0/Android/data/com.example.seefix/files/videos/seefix_video_12345.mp4"
        val testDuration = 8500L

        val videoContext = VideoContext(
            videoUri = testUri,
            durationMs = testDuration
        )

        assertEquals(testUri, videoContext.videoUri)
        assertEquals(testDuration, videoContext.durationMs)
        assertTrue(videoContext.selectedFrames.isEmpty())
        assertNull(videoContext.transcript)
        assertTrue(videoContext.metadata.isEmpty())
    }
}
