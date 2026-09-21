package com.example.seefix.ai.local

import android.content.Context
import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIError
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIRole
import com.example.seefix.ai.core.AIService
import com.example.seefix.ai.core.AIStreamEvent
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

open class LocalGemmaAIService(
    private val context: Context? = null,
    val localModelManager: LocalModelManager? = context?.let { LocalModelManager(it) },
    override val providerConfig: AIProviderConfig = AIProviderConfig(
        providerType = AIProviderType.LOCAL_GEMMA,
        name = "Local Gemma AI Engine",
        model = "gemma-2b-it-gpu-int4.bin"
    )
) : AIService {

    @Volatile
    private var llmInference: LlmInference? = null

    private var loadedModelPath: String? = null

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = resolveModelFile()
        val manager = localModelManager ?: return@withContext false
        modelFile != null && modelFile.exists() && manager.getDeviceCompatibilityInfo().isCompatible
    }

    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            supportsText = true,
            supportsImages = false,
            supportsMultipleImages = false,
            supportsAudio = false,
            supportsVideo = false,
            supportsVideoFrames = false,
            supportsToolCalling = false,
            supportsStructuredOutput = true,
            maxContextTokens = 2048,
            maxImages = 0
        )
    }

    override suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = resolveModelFile() ?: return@withContext false
        val manager = localModelManager ?: return@withContext false
        val compat = manager.getDeviceCompatibilityInfo(modelFile.length())
        if (!compat.isCompatible) return@withContext false
        ensureModelLoaded(modelFile.absolutePath, maxTokens = 512, topK = 40)
    }

    override suspend fun unloadModel() {
        withContext(Dispatchers.IO) {
            synchronized(this@LocalGemmaAIService) {
                try {
                    llmInference?.close()
                } catch (_: Exception) {}
                llmInference = null
                loadedModelPath = null
            }
        }
    }

    override suspend fun generate(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        val manager = localModelManager
            ?: return@withContext AIResponse(
                text = "",
                modelName = providerConfig.model,
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.MODEL_NOT_FOUND, "Local model manager unavailable.")
            )

        val modelFile = resolveModelFile()
            ?: return@withContext AIResponse(
                text = "",
                modelName = providerConfig.model,
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.MODEL_NOT_FOUND, "Local model file not found in models directories.")
            )

        val compat = manager.getDeviceCompatibilityInfo(modelFile.length())
        if (!compat.isCompatible) {
            return@withContext AIResponse(
                text = "",
                modelName = modelFile.nameWithoutExtension,
                providerName = providerConfig.name,
                error = AIError(
                    AIErrorCode.INSUFFICIENT_MEMORY,
                    "Device does not meet memory/storage requirements. " + compat.warningMessages.joinToString("; ")
                )
            )
        }

        try {
            val loaded = ensureModelLoaded(
                modelPath = modelFile.absolutePath,
                maxTokens = request.maxTokens,
                topK = request.topK
            )

            if (!loaded) {
                return@withContext AIResponse(
                    text = "",
                    modelName = modelFile.nameWithoutExtension,
                    providerName = providerConfig.name,
                    error = AIError(AIErrorCode.MODEL_LOAD_FAILED, "Failed to load MediaPipe LlmInference model.")
                )
            }

            val activeInference = llmInference
                ?: return@withContext AIResponse(
                    text = "",
                    modelName = modelFile.nameWithoutExtension,
                    providerName = providerConfig.name,
                    error = AIError(AIErrorCode.MODEL_LOAD_FAILED, "LlmInference instance is null.")
                )

            val formattedPrompt = formatPrompt(request)
            val responseText = activeInference.generateResponse(formattedPrompt)

            AIResponse(
                text = responseText ?: "",
                modelName = modelFile.nameWithoutExtension,
                providerName = providerConfig.name,
                finishReason = "completed"
            )

        } catch (e: OutOfMemoryError) {
            AIResponse(
                text = "",
                modelName = modelFile.nameWithoutExtension,
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.INSUFFICIENT_MEMORY, "Out of memory during model inference.", e)
            )
        } catch (e: Exception) {
            AIResponse(
                text = "",
                modelName = modelFile.nameWithoutExtension,
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.INFERENCE_FAILED, e.message ?: "Inference failed.", e)
            )
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = callbackFlow {
        val ctx = context
        val manager = localModelManager
        if (ctx == null || manager == null) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.MODEL_NOT_FOUND, "Context/Manager unavailable.")))
            close()
            return@callbackFlow
        }

        val modelFile = resolveModelFile()
        if (modelFile == null || !modelFile.exists()) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.MODEL_NOT_FOUND, "Local model file not found.")))
            close()
            return@callbackFlow
        }

        val compat = manager.getDeviceCompatibilityInfo(modelFile.length())
        if (!compat.isCompatible) {
            trySend(
                AIStreamEvent.Error(
                    AIError(
                        AIErrorCode.INSUFFICIENT_MEMORY,
                        "Device memory insufficient: " + compat.warningMessages.joinToString("; ")
                    )
                )
            )
            close()
            return@callbackFlow
        }

        var accumulatedText = ""
        var streamingInference: LlmInference? = null

        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(request.maxTokens)
                .setMaxTopK(request.topK)
                .setResultListener { partialResult, done ->
                    if (partialResult != null) {
                        accumulatedText += partialResult
                        trySend(AIStreamEvent.Chunk(partialResult))
                    }
                    if (done) {
                        val response = AIResponse(
                            text = accumulatedText,
                            modelName = modelFile.nameWithoutExtension,
                            providerName = providerConfig.name,
                            finishReason = "completed"
                        )
                        trySend(AIStreamEvent.Completed(response))
                        close()
                    }
                }
                .setErrorListener { error ->
                    trySend(
                        AIStreamEvent.Error(
                            AIError(
                                AIErrorCode.INFERENCE_FAILED,
                                error.message ?: "Inference error during streaming",
                                error
                            )
                        )
                    )
                    close()
                }
                .build()

            streamingInference = LlmInference.createFromOptions(ctx, options)
            val formattedPrompt = formatPrompt(request)
            streamingInference.generateResponseAsync(formattedPrompt)

        } catch (e: OutOfMemoryError) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.INSUFFICIENT_MEMORY, "Out of memory during stream generation", e)))
            close()
        } catch (e: Exception) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.INFERENCE_FAILED, e.message ?: "Streaming inference failed", e)))
            close()
        }

        awaitClose {
            try {
                streamingInference?.close()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    @Synchronized
    private fun ensureModelLoaded(modelPath: String, maxTokens: Int, topK: Int): Boolean {
        if (llmInference != null && loadedModelPath == modelPath) {
            return true
        }

        try {
            llmInference?.close()
        } catch (_: Exception) {}

        llmInference = null
        loadedModelPath = null

        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(maxTokens)
                .setMaxTopK(topK)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            loadedModelPath = modelPath
            true
        } catch (_: OutOfMemoryError) {
            llmInference = null
            loadedModelPath = null
            false
        } catch (_: Exception) {
            llmInference = null
            loadedModelPath = null
            false
        }
    }

    private fun resolveModelFile(): File? {
        return localModelManager?.getModelFile(providerConfig.model)
    }

    private fun formatPrompt(request: AIRequest): String {
        val builder = StringBuilder()

        // System prompt
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sysPrompt ->
            builder.append("<start_of_turn>user\nInstructions: ").append(sysPrompt).append("<end_of_turn>\n")
        }

        // Conversation history
        for (msg in request.conversationHistory) {
            val roleName = when (msg.role) {
                AIRole.USER -> "user"
                AIRole.ASSISTANT -> "model"
                AIRole.SYSTEM -> "user"
            }
            builder.append("<start_of_turn>").append(roleName).append("\n")
                .append(msg.content).append("<end_of_turn>\n")
        }

        // Additional context payload
        val contextInfo = buildString {
            request.machineInfo?.let {
                append("Device: ${it.deviceName} (${it.modelNumber}), Category: ${it.category}\n")
            }
            request.location?.let {
                append("Location: Lat ${it.latitude}, Long ${it.longitude}${it.address?.let { addr -> " ($addr)" } ?: ""}\n")
            }
        }

        // Current prompt
        builder.append("<start_of_turn>user\n")
        if (contextInfo.isNotBlank()) {
            builder.append("[Context: ").append(contextInfo.trim()).append("]\n")
        }
        builder.append(request.prompt).append("<end_of_turn>\n")

        // Prompt end for model completion
        builder.append("<start_of_turn>model\n")

        return builder.toString()
    }
}
