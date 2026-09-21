package com.example.seefix.ai.providers

import android.util.Base64
import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIError
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIProviderConfig
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

class AnthropicAIService(
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
        return providerConfig.apiKey.isNotBlank()
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
            maxContextTokens = 200000,
            maxImages = 20
        )
    }

    override suspend fun loadModel(): Boolean = true

    override suspend fun unloadModel() {}

    override suspend fun generate(request: AIRequest): AIResponse = withContext(Dispatchers.IO) {
        if (providerConfig.apiKey.isBlank()) {
            return@withContext AIResponse(
                text = "",
                modelName = getModelName(),
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.INVALID_API_KEY, "Anthropic API key is not configured.")
            )
        }

        val url = buildEndpointUrl()
        val jsonPayload = buildRequestBodyJson(request, stream = false)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-api-key", providerConfig.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
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
                        error = AIError(errorCode, "Anthropic API returned error code ${response.code}: $bodyString")
                    )
                }

                parseResponse(bodyString)
            }
        } catch (e: Exception) {
            AIResponse(
                text = "",
                modelName = getModelName(),
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.NETWORK_ERROR, e.message ?: "Network error calling Anthropic API", e)
            )
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = callbackFlow {
        if (providerConfig.apiKey.isBlank()) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_API_KEY, "Anthropic API key is not configured.")))
            close()
            return@callbackFlow
        }

        val url = buildEndpointUrl()
        val jsonPayload = buildRequestBodyJson(request, stream = true)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-api-key", providerConfig.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
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
                trySend(AIStreamEvent.Error(AIError(errorCode, "Anthropic SSE stream failed (${response.code}): $errorBody")))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_RESPONSE, "Empty response body from Anthropic stream")))
                close()
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val dataJson = currentLine.removePrefix("data:").trim()
                    if (dataJson == "[DONE]") break
                    if (dataJson.isBlank()) continue

                    try {
                        val parsedChunk = parseStreamChunk(dataJson)
                        if (parsedChunk.isNotEmpty()) {
                            accumulatedText += parsedChunk
                            trySend(AIStreamEvent.Chunk(parsedChunk))
                        }
                    } catch (_: Exception) {
                        // Skip malformed chunks
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

    private fun getModelName(): String {
        return providerConfig.model.ifBlank { "claude-3-5-haiku-20241022" }
    }

    private fun buildEndpointUrl(): String {
        val base = providerConfig.baseUrl.ifBlank { "https://api.anthropic.com" }.trimEnd('/')
        return when {
            base.endsWith("/v1/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
    }

    private fun buildRequestBodyJson(request: AIRequest, stream: Boolean): JSONObject {
        val json = JSONObject()
        json.put("model", getModelName())
        json.put("max_tokens", request.maxTokens)
        json.put("temperature", request.temperature)
        json.put("stream", stream)

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sysPrompt ->
            json.put("system", sysPrompt)
        }

        val messagesArr = JSONArray()

        for (msg in request.conversationHistory) {
            val msgObj = JSONObject()
            val role = if (msg.role == AIRole.ASSISTANT) "assistant" else "user"
            msgObj.put("role", role)

            val contentArr = JSONArray()
            contentArr.put(JSONObject().put("type", "text").put("text", msg.content))

            if (msg.imageBytes != null) {
                val sourceObj = JSONObject().apply {
                    put("type", "base64")
                    put("media_type", "image/jpeg")
                    put("data", Base64.encodeToString(msg.imageBytes, Base64.NO_WRAP))
                }
                contentArr.put(JSONObject().put("type", "image").put("source", sourceObj))
            }
            msgObj.put("content", contentArr)
            messagesArr.put(msgObj)
        }

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

        val userContentArr = JSONArray()
        userContentArr.put(JSONObject().put("type", "text").put("text", promptText))

        if (request.imageBytes != null) {
            val sourceObj = JSONObject().apply {
                put("type", "base64")
                put("media_type", "image/jpeg")
                put("data", Base64.encodeToString(request.imageBytes, Base64.NO_WRAP))
            }
            userContentArr.put(JSONObject().put("type", "image").put("source", sourceObj))
        }

        userMsgObj.put("content", userContentArr)
        messagesArr.put(userMsgObj)

        json.put("messages", messagesArr)

        return json
    }

    private fun parseResponse(jsonString: String): AIResponse {
        val jsonObj = JSONObject(jsonString)
        val contentArr = jsonObj.optJSONArray("content")
        val textBuilder = StringBuilder()

        if (contentArr != null) {
            for (i in 0 until contentArr.length()) {
                val item = contentArr.getJSONObject(i)
                if (item.optString("type") == "text") {
                    textBuilder.append(item.optString("text", ""))
                }
            }
        }

        val stopReason = jsonObj.optString("stop_reason", "completed")
        val usageObj = jsonObj.optJSONObject("usage")
        val totalTokens = usageObj?.optInt("output_tokens", 0) ?: 0

        return AIResponse(
            text = textBuilder.toString(),
            modelName = getModelName(),
            providerName = providerConfig.name,
            finishReason = stopReason,
            usageTokens = totalTokens
        )
    }

    private fun parseStreamChunk(dataJson: String): String {
        val jsonObj = JSONObject(dataJson)
        val type = jsonObj.optString("type")
        if (type == "content_block_delta") {
            val delta = jsonObj.optJSONObject("delta") ?: return ""
            if (delta.optString("type") == "text_delta") {
                return delta.optString("text", "")
            }
        }
        return ""
    }
}
