package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkAnalysisResult(
    val identifiedObject: String,
    val domain: WorkDomain = WorkDomain.GENERAL,
    val task: String,
    val observations: List<Observation> = emptyList(),
    val detectedIssues: List<String> = emptyList(),
    val recommendedActions: List<WorkAction> = emptyList(),
    val requiredTools: List<ToolItem> = emptyList(),
    val requiredParts: List<ToolItem> = emptyList(),
    val measurements: List<String> = emptyList(),
    val safetyRequirements: List<SafetyRequirement> = emptyList(),
    val confidence: Float = 0f,
    val nextAction: WorkAction? = null
)
