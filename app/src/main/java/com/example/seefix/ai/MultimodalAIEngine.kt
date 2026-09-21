package com.example.seefix.ai

import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep

/**
 * Multimodal AI Engine interface for hardware equipment diagnosis,
 * step verification, and tool/store recommendations.
 */
interface MultimodalAIEngine {
    suspend fun analyzeEquipment(
        imageBytes: ByteArray?,
        userVoicePrompt: String
    ): AIAnalysisResult

    suspend fun verifyStepCompletion(
        imageBytes: ByteArray?,
        currentStep: TroubleshootingStep
    ): StepVerificationResult

    suspend fun recommendStoresForMissingTools(
        missingTools: List<ToolItem>,
        userLocation: LocationInfo
    ): List<StoreLocation>
}
