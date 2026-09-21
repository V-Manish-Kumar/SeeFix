package com.example.seefix.ai.providers

import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAICompatibleAIService(
    override val providerConfig: AIProviderConfig,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : AIService {

    private val client: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(providerConfig.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(providerConfig.timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun isAvailable(): Boolean {
        return when (providerConfig.providerType) {
            AIProviderType.OLLAMA -> true
            AIProviderType.CUSTOM -> true
            else -> providerConfig.apiKey.isNotBlank()
        }
    }

    override fun getCapabilities(): AICapabilities {
        return AICapabilities(
            supportsText = true,
            supportsImages = true,
            supportsMultipleImages = true,
            supportsAudio = false,
            supportsVideo = false,
            supportsVideoFrames = true,
            supportsToolCalling = true,
            supportsStructuredOutput = true,
            maxContextTokens = 128000,
            maxImages = 20
        )
    }

    override suspend fun loadModel(): Boolean = true

    override suspend fun unloadModel() {}

    override suspend fun generate(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        if (requiresApiKey() && providerConfig.apiKey.isBlank()) {
            return@withContext AIResponse(
                text = "",
                modelName = getModelName(),
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.INVALID_API_KEY, "API key is required for ${providerConfig.name}.")
            )
        }

        val url = buildEndpointUrl()
        val jsonPayload = buildRequestBodyJson(request, stream = false)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyHeaders(this) }
            .post(requestBody)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val errorCode = when (response.code) {
                        401, 403 -> AIErrorCode.INVALID_API_KEY
                        429 -> AIErrorCode.RATE_LIMITED
                        404 -> AIErrorCode.MODEL_NOT_FOUND
                        else -> AIErrorCode.INFERENCE_FAILED
                    }
                    return@withContext AIResponse(
                        text = "",
                        modelName = getModelName(),
                        providerName = providerConfig.name,
                        error = AIError(errorCode, "${providerConfig.name} returned HTTP ${response.code}: $bodyString")
                    )
                }

                parseResponse(bodyString)
            }
        } catch (e: Exception) {
            AIResponse(
                text = "",
                modelName = getModelName(),
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.NETWORK_ERROR, e.message ?: "Network error calling ${providerConfig.name}", e)
            )
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = callbackFlow {
        if (requiresApiKey() && providerConfig.apiKey.isBlank()) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_API_KEY, "API key is required for ${providerConfig.name}.")))
            close()
            return@callbackFlow
        }

        val url = buildEndpointUrl()
        val jsonPayload = buildRequestBodyJson(request, stream = true)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyHeaders(this) }
            .post(requestBody)
            .build()

        var accumulatedText = ""
        var call: Call? = null

        try {
            val activeCall = client.newCall(httpRequest)
            call = activeCall
            val response = activeCall.execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                val errorCode = when (response.code) {
                    401, 403 -> AIErrorCode.INVALID_API_KEY
                    429 -> AIErrorCode.RATE_LIMITED
                    else -> AIErrorCode.INFERENCE_FAILED
                }
                trySend(AIStreamEvent.Error(AIError(errorCode, "${providerConfig.name} SSE stream failed (${response.code}): $errorBody")))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_RESPONSE, "Empty body in SSE response")))
                close()
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val dataJson = currentLine.removePrefix("data:").trim()
                    if (dataJson == "[DONE]") {
                        break
                    }
                    if (dataJson.isBlank()) continue

                    try {
                        val parsedChunk = parseStreamChunk(dataJson)
                        if (parsedChunk.isNotEmpty()) {
                            accumulatedText += parsedChunk
                            trySend(AIStreamEvent.Chunk(parsedChunk))
                        }
                    } catch (_: Exception) {
                        // Skip unparseable chunks
                    }
                }
            }

            val completedResponse = AIResponse(
                text = accumulatedText,
                modelName = getModelName(),
                providerName = providerConfig.name,
                finishReason = "completed"
            )
            trySend(AIStreamEvent.Completed(completedResponse))
            close()

        } catch (e: Exception) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.NETWORK_ERROR, e.message ?: "Streaming error", e)))
            close()
        }

        awaitClose {
            try {
                call?.cancel()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    private fun requiresApiKey(): Boolean {
        return providerConfig.providerType != AIProviderType.OLLAMA &&
                providerConfig.providerType != AIProviderType.CUSTOM
    }

    private fun getModelName(): String {
        if (providerConfig.model.isNotBlank()) return providerConfig.model
        return when (providerConfig.providerType) {
            AIProviderType.OPENAI -> "gpt-4o-mini"
            AIProviderType.OPENROUTER -> "google/gemini-flash-1.5"
            AIProviderType.OLLAMA -> "gemma2"
            else -> "default"
        }
    }

    private fun getDefaultBaseUrl(): String {
        return when (providerConfig.providerType) {
            AIProviderType.OPENAI -> "https://api.openai.com"
            AIProviderType.OPENROUTER -> "https://openrouter.ai/api"
            AIProviderType.OLLAMA -> "http://localhost:11434"
            else -> ""
        }
    }

    private fun buildEndpointUrl(): String {
        val base = providerConfig.baseUrl.ifBlank { getDefaultBaseUrl() }.trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun applyHeaders(builder: Request.Builder) {
        builder.header("Content-Type", "application/json")
        if (providerConfig.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${providerConfig.apiKey}")
        }
        if (providerConfig.providerType == AIProviderType.OPENROUTER) {
            builder.header("HTTP-Referer", "https://seefix.example.com")
            builder.header("X-Title", "SeeFix AI")
        }
        providerConfig.organizationId?.takeIf { it.isNotBlank() }?.let { orgId ->
            builder.header("OpenAI-Organization", orgId)
        }
    }

    private fun buildRequestBodyJson(request: AIRequest, stream: Boolean): JSONObject {
        val json = JSONObject()
        json.put("model", getModelName())
        json.put("temperature", request.temperature)
        json.put("max_tokens", request.maxTokens)
        json.put("stream", stream)

        val messagesArr = JSONArray()

        // System message
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sysPrompt ->
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", sysPrompt)
            messagesArr.put(sysObj)
        }

        // Conversation history
        for (msg in request.conversationHistory) {
            val msgObj = JSONObject()
            val role = when (msg.role) {
                AIRole.SYSTEM -> "system"
                AIRole.USER -> "user"
                AIRole.ASSISTANT -> "assistant"
            }
            msgObj.put("role", role)

            if (msg.imageBytes != null) {
                val contentArr = JSONArray()
                contentArr.put(JSONObject().put("type", "text").put("text", msg.content))

                val base64Img = Base64.encodeToString(msg.imageBytes, Base64.NO_WRAP)
                val imageUrlObj = JSONObject().put("url", "data:image/jpeg;base64,$base64Img")
                contentArr.put(JSONObject().put("type", "image_url").put("image_url", imageUrlObj))

                msgObj.put("content", contentArr)
            } else {
                msgObj.put("content", msg.content)
            }
            messagesArr.put(msgObj)
        }

        // Current request message
        val userMsgObj = JSONObject()
        userMsgObj.put("role", "user")

        val promptText = buildString {
            val contextInfo = buildString {
                request.machineInfo?.let { append("Device: ${it.deviceName} (${it.modelNumber}), Category: ${it.category}\n") }
                request.location?.let { append("Location: Lat ${it.latitude}, Long ${it.longitude}${it.address?.let { addr -> " ($addr)" } ?: ""}\n") }
            }
            if (contextInfo.isNotBlank()) {
                append("[Context: ").append(contextInfo.trim()).append("]\n")
            }
            append(request.prompt)
        }

        if (request.imageBytes != null) {
            val contentArr = JSONArray()
            contentArr.put(JSONObject().put("type", "text").put("text", promptText))

            val base64Img = Base64.encodeToString(request.imageBytes, Base64.NO_WRAP)
            val imageUrlObj = JSONObject().put("url", "data:image/jpeg;base64,$base64Img")
            contentArr.put(JSONObject().put("type", "image_url").put("image_url", imageUrlObj))

            userMsgObj.put("content", contentArr)
        } else {
            userMsgObj.put("content", promptText)
        }

        messagesArr.put(userMsgObj)
        json.put("messages", messagesArr)

        return json
    }

    private fun parseResponse(jsonString: String): AIResponse {
        val jsonObj = JSONObject(jsonString)
        val choices = jsonObj.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val messageObj = firstChoice.optJSONObject("message")
            val textContent = messageObj?.optString("content", "") ?: ""
            val finishReason = firstChoice.optString("finish_reason", "completed")
            val usageObj = jsonObj.optJSONObject("usage")
            val totalTokens = usageObj?.optInt("total_tokens", 0) ?: 0

            return AIResponse(
                text = textContent,
                modelName = getModelName(),
                providerName = providerConfig.name,
                finishReason = finishReason,
                usageTokens = totalTokens
            )
        }

        return AIResponse(
            text = "",
            modelName = getModelName(),
            providerName = providerConfig.name,
            error = AIError(AIErrorCode.INVALID_RESPONSE, "No choices returned from ${providerConfig.name}.")
        )
    }

    private fun parseStreamChunk(dataJson: String): String {
        val jsonObj = JSONObject(dataJson)
        val choices = jsonObj.optJSONArray("choices") ?: return ""
        if (choices.length() > 0) {
            val firstChoice = choices.getJSONObject(0)
            val delta = firstChoice.optJSONObject("delta") ?: return ""
            return delta.optString("content", "")
        }
        return ""
    }
}
