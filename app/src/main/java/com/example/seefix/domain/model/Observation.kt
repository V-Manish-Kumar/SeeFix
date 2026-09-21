package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ObservationSource {
    USER,
    CAMERA,
    VIDEO,
    SENSOR,
    DOCUMENT,
    HISTORY,
    AI_INFERENCE
}

@Serializable
data class Observation(
    val id: String,
    val description: String,
    val source: ObservationSource,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val supportingEvidence: String? = null
)
