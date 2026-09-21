package com.example.seefix.ai.router

import com.example.seefix.ai.core.AIError
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class AIMode {
    AUTO,
    LOCAL_ONLY,
    CLOUD_ONLY
}

class AIRouter(
    val registry: AIProviderRegistry,
    val localGemmaService: LocalGemmaAIService
) {

    suspend fun generate(request: AIRequest, mode: AIMode = AIMode.AUTO): AIResponse {
        val requirements = request.computeRequirements()

        return when (mode) {
            AIMode.LOCAL_ONLY -> {
                if (!localGemmaService.isAvailable()) {
                    AIResponse(
                        text = "",
                        modelName = localGemmaService.providerConfig.model,
                        providerName = localGemmaService.providerConfig.name,
                        error = AIError(
                            AIErrorCode.PROVIDER_UNAVAILABLE,
                            "Local AI unavailable. Install a compatible Gemma model or select another AI mode."
                        )
                    )
                } else if (!localGemmaService.getCapabilities().satisfies(requirements)) {
                    val isVisual = requirements.requiresImages || requirements.requiresVideoFrames || requirements.requiredImageCount > 0
                    val errorMessage = if (isVisual) {
                        "Local Gemma engine is text-only and cannot process visual inputs. Select AUTO or CLOUD_ONLY AI mode for image/video analysis."
                    } else {
                        "Local Gemma engine does not support the required capabilities for this request."
                    }
                    AIResponse(
                        text = "",
                        modelName = localGemmaService.providerConfig.model,
                        providerName = localGemmaService.providerConfig.name,
                        error = AIError(
                            AIErrorCode.UNSUPPORTED_CAPABILITY,
                            errorMessage
                        )
                    )
                } else {
                    localGemmaService.generate(request)
                }
            }

            AIMode.CLOUD_ONLY -> {
                val activeCloudService = registry.getActiveCloudProvider()
                if (activeCloudService == null || !activeCloudService.isAvailable()) {
                    val providerName = activeCloudService?.providerConfig?.name ?: "Cloud AI"
                    val modelName = activeCloudService?.providerConfig?.model ?: "Unknown"
                    AIResponse(
                        text = "",
                        modelName = modelName,
                        providerName = providerName,
                        error = AIError(
                            AIErrorCode.PROVIDER_UNAVAILABLE,
                            "Cloud AI provider unavailable. Configure a cloud AI provider or select another AI mode."
                        )
                    )
                } else if (!activeCloudService.getCapabilities().satisfies(requirements)) {
                    AIResponse(
                        text = "",
                        modelName = activeCloudService.providerConfig.model,
                        providerName = activeCloudService.providerConfig.name,
                        error = AIError(
                            AIErrorCode.UNSUPPORTED_CAPABILITY,
                            "No active AI provider supports the required capabilities (images: ${requirements.requiredImageCount}, video frames: ${requirements.requiresVideoFrames})"
                        )
                    )
                } else {
                    activeCloudService.generate(request)
                }
            }

            AIMode.AUTO -> {
                val isLocalAvailable = localGemmaService.isAvailable()
                val canHandleLocally = isLocalAvailable && localGemmaService.getCapabilities().satisfies(requirements)

                if (canHandleLocally) {
                    localGemmaService.generate(request)
                } else {
                    val activeCloudService = registry.getActiveCloudProvider()
                    if (activeCloudService != null && activeCloudService.isAvailable() && activeCloudService.getCapabilities().satisfies(requirements)) {
                        activeCloudService.generate(request)
                    } else {
                        val candidate = registry.getAllProviders().values.firstOrNull {
                            it.isAvailable() && it.getCapabilities().satisfies(requirements)
                        }
                        if (candidate != null) {
                            candidate.generate(request)
                        } else {
                            val anyAvailable = isLocalAvailable || activeCloudService?.isAvailable() == true || registry.getAllProviders().values.any { it.isAvailable() }
                            if (anyAvailable) {
                                AIResponse(
                                    text = "",
                                    modelName = activeCloudService?.providerConfig?.model ?: localGemmaService.providerConfig.model,
                                    providerName = activeCloudService?.providerConfig?.name ?: localGemmaService.providerConfig.name,
                                    error = AIError(
                                        AIErrorCode.UNSUPPORTED_CAPABILITY,
                                        "No active AI provider supports the required capabilities (images: ${requirements.requiredImageCount}, video frames: ${requirements.requiresVideoFrames})"
                                    )
                                )
                            } else {
                                AIResponse(
                                    text = "",
                                    modelName = "None",
                                    providerName = "None",
                                    error = AIError(
                                        AIErrorCode.PROVIDER_UNAVAILABLE,
                                        "No available AI provider found for request. Please configure cloud or local AI."
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun stream(request: AIRequest, mode: AIMode = AIMode.AUTO): Flow<AIStreamEvent> = flow {
        val requirements = request.computeRequirements()

        when (mode) {
            AIMode.LOCAL_ONLY -> {
                if (!localGemmaService.isAvailable()) {
                    emit(
                        AIStreamEvent.Error(
                            AIError(
                                AIErrorCode.PROVIDER_UNAVAILABLE,
                                "Local AI unavailable. Install a compatible Gemma model or select another AI mode."
                            )
                        )
                    )
                } else if (!localGemmaService.getCapabilities().satisfies(requirements)) {
                    val isVisual = requirements.requiresImages || requirements.requiresVideoFrames || requirements.requiredImageCount > 0
                    val errorMessage = if (isVisual) {
                        "Local Gemma engine is text-only and cannot process visual inputs. Select AUTO or CLOUD_ONLY AI mode for image/video analysis."
                    } else {
                        "Local Gemma engine does not support the required capabilities for this request."
                    }
                    emit(
                        AIStreamEvent.Error(
                            AIError(
                                AIErrorCode.UNSUPPORTED_CAPABILITY,
                                errorMessage
                            )
                        )
                    )
                } else {
                    localGemmaService.stream(request).collect { emit(it) }
                }
            }

            AIMode.CLOUD_ONLY -> {
                val activeCloudService = registry.getActiveCloudProvider()
                if (activeCloudService == null || !activeCloudService.isAvailable()) {
                    emit(
                        AIStreamEvent.Error(
                            AIError(
                                AIErrorCode.PROVIDER_UNAVAILABLE,
                                "Cloud AI provider unavailable. Configure a cloud AI provider or select another AI mode."
                            )
                        )
                    )
                } else if (!activeCloudService.getCapabilities().satisfies(requirements)) {
                    emit(
                        AIStreamEvent.Error(
                            AIError(
                                AIErrorCode.UNSUPPORTED_CAPABILITY,
                                "No active AI provider supports the required capabilities (images: ${requirements.requiredImageCount}, video frames: ${requirements.requiresVideoFrames})"
                            )
                        )
                    )
                } else {
                    activeCloudService.stream(request).collect { emit(it) }
                }
            }

            AIMode.AUTO -> {
                val isLocalAvailable = localGemmaService.isAvailable()
                val canHandleLocally = isLocalAvailable && localGemmaService.getCapabilities().satisfies(requirements)

                if (canHandleLocally) {
                    localGemmaService.stream(request).collect { emit(it) }
                } else {
                    val activeCloudService = registry.getActiveCloudProvider()
                    if (activeCloudService != null && activeCloudService.isAvailable() && activeCloudService.getCapabilities().satisfies(requirements)) {
                        activeCloudService.stream(request).collect { emit(it) }
                    } else {
                        val candidate = registry.getAllProviders().values.firstOrNull {
                            it.isAvailable() && it.getCapabilities().satisfies(requirements)
                        }
                        if (candidate != null) {
                            candidate.stream(request).collect { emit(it) }
                        } else {
                            val anyAvailable = isLocalAvailable || activeCloudService?.isAvailable() == true || registry.getAllProviders().values.any { it.isAvailable() }
                            if (anyAvailable) {
                                emit(
                                    AIStreamEvent.Error(
                                        AIError(
                                            AIErrorCode.UNSUPPORTED_CAPABILITY,
                                            "No active AI provider supports the required capabilities (images: ${requirements.requiredImageCount}, video frames: ${requirements.requiresVideoFrames})"
                                        )
                                    )
                                )
                            } else {
                                emit(
                                    AIStreamEvent.Error(
                                        AIError(
                                            AIErrorCode.PROVIDER_UNAVAILABLE,
                                            "No available AI provider found for request. Please configure cloud or local AI."
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
