package com.example.seefix.ai.core

data class AICapabilities(
    val supportsText: Boolean = true,
    val supportsImages: Boolean = false,
    val supportsMultipleImages: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsVideoFrames: Boolean = false,
    val supportsToolCalling: Boolean = false,
    val supportsStructuredOutput: Boolean = true,
    val maxContextTokens: Int? = 2048,
    val maxImages: Int? = 0
) {
    val supportsImage: Boolean get() = supportsImages
    val supportsMultimodal: Boolean get() = supportsImages || supportsAudio || supportsVideo || supportsVideoFrames
    val supportsStreaming: Boolean get() = true

    fun satisfies(requirements: AIRequirements): Boolean {
        if (requirements.requiresText && !supportsText) return false
        if (requirements.requiresImages && !supportsImages) return false
        if (requirements.requiresMultipleImages && (!supportsImages || !supportsMultipleImages)) return false
        if (requirements.requiresAudio && !supportsAudio) return false
        if (requirements.requiresVideo && !supportsVideo) return false
        if (requirements.requiresVideoFrames && !supportsVideoFrames) return false
        if (requirements.requiresToolCalling && !supportsToolCalling) return false
        if (requirements.requiresStructuredOutput && !supportsStructuredOutput) return false

        if (requirements.requiredImageCount > 0) {
            if (!supportsImages) return false
            if (requirements.requiredImageCount > 1 && !supportsMultipleImages) return false
            if (maxImages != null && maxImages < requirements.requiredImageCount) return false
        }

        return true
    }
}
