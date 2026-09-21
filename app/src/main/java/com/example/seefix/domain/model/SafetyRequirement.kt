package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SafetySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL_STOP
}

@Serializable
data class SafetyRequirement(
    val hazard: String,
    val severity: SafetySeverity,
    val precaution: String,
    val ppe: List<String> = emptyList(),
    val stopCondition: String? = null
)
