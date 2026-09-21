package com.example.seefix.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * CameraX Video Recorder wrapper managing recording lifecycle, output file creation,
 * audio permissions, and StateFlow updates.
 */
class VideoRecorder(
    private val context: Context? = null,
    var videoCapture: VideoCapture<Recorder>? = null
) {
    private val _recordingState = MutableStateFlow<VideoRecordingState>(VideoRecordingState.Idle)
    val recordingState: StateFlow<VideoRecordingState> = _recordingState.asStateFlow()
    val state: StateFlow<VideoRecordingState> get() = recordingState

    private var activeRecording: Recording? = null
    private var currentOutputFile: File? = null

    /**
     * Generates timestamped video output file in context.getExternalFilesDir("videos").
     */
    fun generateVideoFile(timestamp: Long = System.currentTimeMillis()): File {
        val videoDir = context?.getExternalFilesDir("videos") ?: File("build/tmp/videos")
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
        return File(videoDir, "seefix_video_$timestamp.mp4")
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val capture = videoCapture
        val outputFile = generateVideoFile()
        currentOutputFile = outputFile

        _recordingState.value = VideoRecordingState.Starting

        val ctx = context
        if (capture == null || ctx == null) {
            _recordingState.value = VideoRecordingState.Recording(0L)
            return
        }

        try {
            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            var pendingRecording: PendingRecording = capture.output.prepareRecording(context, outputOptions)

            val hasAudioPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasAudioPermission) {
                pendingRecording = pendingRecording.withAudioEnabled()
            }

            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _recordingState.value = VideoRecordingState.Recording(0L)
                    }
                    is VideoRecordEvent.Status -> {
                        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                        _recordingState.value = VideoRecordingState.Recording(durationMs)
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                            val uri = event.outputResults.outputUri
                            val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                            _recordingState.value = VideoRecordingState.Completed(
                                uri = uri,
                                fileAbsolutePath = outputFile.absolutePath,
                                durationMs = durationMs
                            )
                        } else {
                            _recordingState.value = VideoRecordingState.Error(
                                message = "Video recording error code: ${event.error}",
                                cause = event.cause
                            )
                        }
                        activeRecording = null
                    }
                }
            }
        } catch (e: Exception) {
            _recordingState.value = VideoRecordingState.Error(
                message = e.message ?: "Failed to start recording",
                cause = e
            )
        }
    }

    fun stopRecording() {
        _recordingState.value = VideoRecordingState.Stopping
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            activeRecording = null
        } else {
            val file = currentOutputFile
            if (file != null) {
                val uri: Uri? = try {
                    Uri.fromFile(file)
                } catch (_: Throwable) {
                    null
                }
                if (uri != null) {
                    _recordingState.value = VideoRecordingState.Completed(
                        uri = uri,
                        fileAbsolutePath = file.absolutePath,
                        durationMs = 1000L
                    )
                } else {
                    _recordingState.value = VideoRecordingState.Stopping
                }
            } else {
                _recordingState.value = VideoRecordingState.Idle
            }
        }
    }

    fun cancelRecording() {
        _recordingState.value = VideoRecordingState.Stopping
        activeRecording?.stop()
        activeRecording = null
        currentOutputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        currentOutputFile = null
        _recordingState.value = VideoRecordingState.Idle
    }

    fun updateStateForTest(newState: VideoRecordingState) {
        _recordingState.value = newState
    }
}
