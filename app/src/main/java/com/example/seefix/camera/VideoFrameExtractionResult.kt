package com.example.seefix.camera

import com.example.seefix.ai.core.AIImage

data class VideoFrameExtractionResult(
    val videoUri: String,
    val durationMs: Long,
    val frames: List<AIImage>,
    val extractedCount: Int,
    val skippedCount: Int
)
