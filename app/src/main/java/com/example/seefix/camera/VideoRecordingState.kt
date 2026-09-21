package com.example.seefix.camera

import android.net.Uri

/**
 * Represents state of CameraX video recording operations.
 */
sealed interface VideoRecordingState {
    data object Idle : VideoRecordingState
    data object Starting : VideoRecordingState
    data class Recording(val durationMs: Long = 0L) : VideoRecordingState
    data object Stopping : VideoRecordingState
    data class Completed(
        val uri: Uri,
        val fileAbsolutePath: String,
        val durationMs: Long
    ) : VideoRecordingState
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : VideoRecordingState
}
