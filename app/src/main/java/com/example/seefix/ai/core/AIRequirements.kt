package com.example.seefix.ai.core

data class AIRequirements(
    val requiresText: Boolean = true,
    val requiresImages: Boolean = false,
    val requiresMultipleImages: Boolean = false,
    val requiresAudio: Boolean = false,
    val requiresVideo: Boolean = false,
    val requiresVideoFrames: Boolean = false,
    val requiresToolCalling: Boolean = false,
    val requiresStructuredOutput: Boolean = false,
    val requiredImageCount: Int = 0
)
