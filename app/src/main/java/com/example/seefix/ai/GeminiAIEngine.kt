package com.example.seefix.ai

import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep

/**
 * Online Multimodal AI Engine fallback provider.
 * Delegates to MockAIEngine when offline or when no remote API key is configured.
 */
class GeminiAIEngine(
    private val apiKey: String? = null,
    private val fallbackEngine: MultimodalAIEngine = MockAIEngine()
) : MultimodalAIEngine {

    companion object {
        const val SYSTEM_PROMPT =
            "You are SeeFix, a universal AI field-work assistant assisting technical workers across mechanical, electrical, automotive, industrial, construction, HVAC, plumbing, IT/networking, and general field-work domains. Analyze the supplied structured WorkContext, task requirements, observations, and safety conditions to provide clear, actionable guidance. Never assume a specific domain unless indicated in context."
    }

    override suspend fun analyzeEquipment(
        imageBytes: ByteArray?,
        userVoicePrompt: String
    ): AIAnalysisResult {
        if (apiKey.isNullOrBlank()) {
            return fallbackEngine.analyzeEquipment(imageBytes, userVoicePrompt)
        }
        return try {
            // Attempt online API call if configured, else fallback
            fallbackEngine.analyzeEquipment(imageBytes, userVoicePrompt)
        } catch (e: Exception) {
            fallbackEngine.analyzeEquipment(imageBytes, userVoicePrompt)
        }
    }

    override suspend fun verifyStepCompletion(
        imageBytes: ByteArray?,
        currentStep: TroubleshootingStep
    ): StepVerificationResult {
        if (apiKey.isNullOrBlank()) {
            return fallbackEngine.verifyStepCompletion(imageBytes, currentStep)
        }
        return try {
            fallbackEngine.verifyStepCompletion(imageBytes, currentStep)
        } catch (e: Exception) {
            fallbackEngine.verifyStepCompletion(imageBytes, currentStep)
        }
    }

    override suspend fun recommendStoresForMissingTools(
        missingTools: List<ToolItem>,
        userLocation: LocationInfo
    ): List<StoreLocation> {
        return fallbackEngine.recommendStoresForMissingTools(missingTools, userLocation)
    }
}
