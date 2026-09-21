package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SafetyLevel {
    SAFE,
    CAUTION,
    DANGEROUS_STOP
}

@Serializable
data class SafetyStatus(
    val level: SafetyLevel = SafetyLevel.SAFE,
    val message: String = "Operating conditions are nominal and safe."
) {
    companion object {
        val SAFE_DEFAULT = SafetyStatus(SafetyLevel.SAFE, "Operating conditions are nominal and safe.")
        val CAUTION_DEFAULT = SafetyStatus(SafetyLevel.CAUTION, "Proceed with caution. Isolate power source if required.")
        val DANGEROUS_STOP_DEFAULT = SafetyStatus(SafetyLevel.DANGEROUS_STOP, "HAZARD DETECTED! STOP OPERATION IMMEDIATELY!")
    }
}
