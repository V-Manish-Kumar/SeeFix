package com.example.seefix.ai

import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep
import kotlinx.serialization.Serializable

@Serializable
data class AIAnalysisResult(
    val device: HardwareDevice?,
    val problem: String,
    val observations: List<String>,
    val steps: List<TroubleshootingStep>,
    val requiredTools: List<ToolItem>,
    val confidence: Float,
    val safetyStatus: SafetyStatus
)
