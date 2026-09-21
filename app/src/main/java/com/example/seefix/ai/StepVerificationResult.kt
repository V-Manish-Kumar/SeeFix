package com.example.seefix.ai

import kotlinx.serialization.Serializable

@Serializable
data class DetectedBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float,
    val label: String
)

@Serializable
data class StepVerificationResult(
    val isVerified: Boolean,
    val confidence: Float,
    val feedbackMessage: String,
    val detectedObjects: List<String>,
    val nextRecommendedAction: String,
    val detectedBoxes: List<DetectedBox> = emptyList()
)
