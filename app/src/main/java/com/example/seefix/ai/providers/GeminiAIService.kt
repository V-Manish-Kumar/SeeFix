package com.example.seefix.ai.providers

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

class GeminiAIService(
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
            supportsAudio = true,
            supportsVideo = false,
            supportsVideoFrames = true,
            supportsToolCalling = true,
            supportsStructuredOutput = true,
            maxContextTokens = 30720,
            maxImages = 16
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
                error = AIError(AIErrorCode.INVALID_API_KEY, "Gemini API key is not configured.")
            )
        }

        val url = buildUrl(isStreaming = false)
        val jsonPayload = buildRequestBodyJson(request)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
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
                        error = AIError(errorCode, "Gemini API returned error code ${response.code}: $bodyString")
                    )
                }

                parseGeminiResponse(bodyString)
            }
        } catch (e: Exception) {
            AIResponse(
                text = "",
                modelName = getModelName(),
                providerName = providerConfig.name,
                error = AIError(AIErrorCode.NETWORK_ERROR, e.message ?: "Network error calling Gemini API", e)
            )
        }
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> = callbackFlow {
        if (providerConfig.apiKey.isBlank()) {
            trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_API_KEY, "Gemini API key is not configured.")))
            close()
            return@callbackFlow
        }

        val url = buildUrl(isStreaming = true)
        val jsonPayload = buildRequestBodyJson(request)
        val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
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
                trySend(AIStreamEvent.Error(AIError(errorCode, "Gemini SSE stream failed (${response.code}): $errorBody")))
                close()
                return@callbackFlow
            }

            val body = response.body
            if (body == null) {
                trySend(AIStreamEvent.Error(AIError(AIErrorCode.INVALID_RESPONSE, "Empty body in Gemini SSE response")))
                close()
                return@callbackFlow
            }

            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val dataJson = currentLine.removePrefix("data:").trim()
                    if (dataJson.isBlank() || dataJson == "[DONE]") continue

                    try {
                        val parsedChunk = parseGeminiChunkText(dataJson)
                        if (parsedChunk.isNotEmpty()) {
                            accumulatedText += parsedChunk
                            trySend(AIStreamEvent.Chunk(parsedChunk))
                        }
                    } catch (_: Exception) {
                        // Skip malformed SSE chunks
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
        return providerConfig.model.ifBlank { "gemini-1.5-flash" }
    }

    private fun buildUrl(isStreaming: Boolean): String {
        val baseUrl = providerConfig.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }.trimEnd('/')
        val model = getModelName()
        return if (isStreaming) {
            "$baseUrl/v1beta/models/$model:streamGenerateContent?key=${providerConfig.apiKey}&alt=sse"
        } else {
            "$baseUrl/v1beta/models/$model:generateContent?key=${providerConfig.apiKey}"
        }
    }

    private fun buildRequestBodyJson(request: AIRequest): JSONObject {
        val json = JSONObject()

        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sysPrompt ->
            val sysObj = JSONObject()
            val partsArr = JSONArray()
            partsArr.put(JSONObject().put("text", sysPrompt))
            sysObj.put("parts", partsArr)
            json.put("systemInstruction", sysObj)
        }

        val contentsArr = JSONArray()

        for (msg in request.conversationHistory) {
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.role == AIRole.ASSISTANT) "model" else "user")
            val partsArr = JSONArray()
            partsArr.put(JSONObject().put("text", msg.content))
            if (msg.imageBytes != null) {
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", android.util.Base64.encodeToString(msg.imageBytes, android.util.Base64.NO_WRAP))
                partsArr.put(JSONObject().put("inlineData", inlineData))
            }
            contentObj.put("parts", partsArr)
            contentsArr.put(contentObj)
        }

        val userContentObj = JSONObject()
        userContentObj.put("role", "user")
        val userPartsArr = JSONArray()

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

        userPartsArr.put(JSONObject().put("text", promptText))

        if (request.imageBytes != null) {
            val inlineData = JSONObject()
            inlineData.put("mimeType", "image/jpeg")
            inlineData.put("data", android.util.Base64.encodeToString(request.imageBytes, android.util.Base64.NO_WRAP))
            userPartsArr.put(JSONObject().put("inlineData", inlineData))
        }

        userContentObj.put("parts", userPartsArr)
        contentsArr.put(userContentObj)

        json.put("contents", contentsArr)

        val genConfig = JSONObject()
        genConfig.put("temperature", request.temperature)
        genConfig.put("maxOutputTokens", request.maxTokens)
        genConfig.put("topK", request.topK)
        json.put("generationConfig", genConfig)

        return json
    }

    private fun parseGeminiResponse(jsonString: String): AIResponse {
        val jsonObj = JSONObject(jsonString)
        val candidates = jsonObj.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCand = candidates.getJSONObject(0)
            val content = firstCand.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    textBuilder.append(part.optString("text", ""))
                }
            }
            val finishReason = firstCand.optString("finishReason", "completed")
            val usageMetadata = jsonObj.optJSONObject("usageMetadata")
            val totalTokens = usageMetadata?.optInt("totalTokenCount", 0) ?: 0

            return AIResponse(
                text = textBuilder.toString(),
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
            error = AIError(AIErrorCode.INVALID_RESPONSE, "Gemini response contained no candidates.")
        )
    }

    private fun parseGeminiChunkText(dataJson: String): String {
        val jsonObj = JSONObject(dataJson)
        val candidates = jsonObj.optJSONArray("candidates") ?: return ""
        if (candidates.length() > 0) {
            val firstCand = candidates.getJSONObject(0)
            val content = firstCand.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                sb.append(part.optString("text", ""))
            }
            return sb.toString()
        }
        return ""
    }
}
