package com.example.seefix.ai.core

data class VideoContext(
    val videoUri: String? = null,
    val durationMs: Long = 0L,
    val selectedFrames: List<AIImage> = emptyList(),
    val transcript: String? = null,
    val metadata: Map<String, String> = emptyMap()
)
