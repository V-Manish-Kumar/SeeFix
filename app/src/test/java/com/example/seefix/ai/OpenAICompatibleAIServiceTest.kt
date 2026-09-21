package com.example.seefix.ai

import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.providers.OpenAICompatibleAIService
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OpenAICompatibleAIServiceTest {

    private class MockInterceptor : Interceptor {
        var capturedRequest: Request? = null
        var capturedRequestBodyString: String? = null
        var responseCode: Int = 200
        var responseBodyString: String = ""
        var responseContentType: String = "application/json; charset=utf-8"
        var throwNetworkError: Boolean = false

        override fun intercept(chain: Interceptor.Chain): Response {
            if (throwNetworkError) {
                throw IOException("Simulated network connection failure")
            }

            val request = chain.request()
            capturedRequest = request

            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                capturedRequestBodyString = buffer.readUtf8()
            }

            val responseBody = responseBodyString.toResponseBody(responseContentType.toMediaType())

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message(if (responseCode in 200..299) "OK" else "Error")
                .body(responseBody)
                .build()
        }
    }

    @Test
    fun requestBodyFormatting_correctlyFormatsJsonPayload() = runTest {
        val mockInterceptor = MockInterceptor().apply {
            responseBodyString = """
                {
                  "choices": [{
                    "message": {"role": "assistant", "content": "Capacitor looks blown."},
                    "finish_reason": "stop"
                  }],
                  "usage": {"total_tokens": 128}
                }
            """.trimIndent()
        }
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()

        val config = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = "sk-test-key-123",
            model = "gpt-4o-mini"
        )
        val service = OpenAICompatibleAIService(config, client)

        val request = AIRequest(
            prompt = "Diagnose bulging capacitor",
            systemPrompt = "You are a hardware specialist.",
            temperature = 0.5f,
            maxTokens = 300
        )

        val response = service.generate(request)

        assertNull(response.error)
        assertEquals("Capacitor looks blown.", response.text)
        assertEquals(128, response.usageTokens)

        val bodyJson = JSONObject(mockInterceptor.capturedRequestBodyString!!)
        assertEquals("gpt-4o-mini", bodyJson.getString("model"))
        assertEquals(0.5, bodyJson.getDouble("temperature"), 0.01)
        assertEquals(300, bodyJson.getInt("max_tokens"))
        assertEquals(false, bodyJson.getBoolean("stream"))

        val messages = bodyJson.getJSONArray("messages")
        assertEquals(2, messages.length())

        val sysMsg = messages.getJSONObject(0)
        assertEquals("system", sysMsg.getString("role"))
        assertEquals("You are a hardware specialist.", sysMsg.getString("content"))

        val userMsg = messages.getJSONObject(1)
        assertEquals("user", userMsg.getString("role"))
        assertEquals("Diagnose bulging capacitor", userMsg.getString("content"))
    }

    @Test
    fun authorizationAndHeaders_passedCorrectlyForOpenAIAndOpenRouter() = runTest {
        val mockInterceptor = MockInterceptor().apply {
            responseBodyString = """{"choices":[{"message":{"content":"OK"}}]}"""
        }
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()

        // 1. OpenAI with Organization ID
        val openAiConfig = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = "sk-proj-abc",
            organizationId = "org-seefix-123"
        )
        val openAiService = OpenAICompatibleAIService(openAiConfig, client)
        openAiService.generate(AIRequest(prompt = "Test"))

        var req = mockInterceptor.capturedRequest!!
        assertEquals("Bearer sk-proj-abc", req.header("Authorization"))
        assertEquals("application/json", req.header("Content-Type"))
        assertEquals("org-seefix-123", req.header("OpenAI-Organization"))

        // 2. OpenRouter with Referer and Title
        val openRouterConfig = AIProviderConfig(
            providerType = AIProviderType.OPENROUTER,
            name = "OpenRouter",
            apiKey = "sk-or-v1-xyz"
        )
        val openRouterService = OpenAICompatibleAIService(openRouterConfig, client)
        openRouterService.generate(AIRequest(prompt = "Test"))

        req = mockInterceptor.capturedRequest!!
        assertEquals("Bearer sk-or-v1-xyz", req.header("Authorization"))
        assertEquals("https://seefix.example.com", req.header("HTTP-Referer"))
        assertEquals("SeeFix AI", req.header("X-Title"))
    }

    @Test
    fun customBaseUrlConfiguration_constructsEndpointUrlCorrectly() = runTest {
        val mockInterceptor = MockInterceptor().apply {
            responseBodyString = """{"choices":[{"message":{"content":"OK"}}]}"""
        }
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()

        // Default OpenAI URL
        val openAiConfig = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "key")
        OpenAICompatibleAIService(openAiConfig, client).generate(AIRequest(prompt = "Hi"))
        assertEquals("https://api.openai.com/v1/chat/completions", mockInterceptor.capturedRequest!!.url.toString())

        // Default OpenRouter URL
        val openRouterConfig = AIProviderConfig(providerType = AIProviderType.OPENROUTER, name = "OpenRouter", apiKey = "key")
        OpenAICompatibleAIService(openRouterConfig, client).generate(AIRequest(prompt = "Hi"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", mockInterceptor.capturedRequest!!.url.toString())

        // Custom base URL with /v1
        val customConfig1 = AIProviderConfig(
            providerType = AIProviderType.CUSTOM,
            name = "Custom",
            baseUrl = "https://my-llm.internal.net/v1"
        )
        OpenAICompatibleAIService(customConfig1, client).generate(AIRequest(prompt = "Hi"))
        assertEquals("https://my-llm.internal.net/v1/chat/completions", mockInterceptor.capturedRequest!!.url.toString())

        // Custom base URL already ending with /chat/completions
        val customConfig2 = AIProviderConfig(
            providerType = AIProviderType.CUSTOM,
            name = "Custom",
            baseUrl = "https://my-llm.internal.net/api/v1/chat/completions"
        )
        OpenAICompatibleAIService(customConfig2, client).generate(AIRequest(prompt = "Hi"))
        assertEquals("https://my-llm.internal.net/api/v1/chat/completions", mockInterceptor.capturedRequest!!.url.toString())
    }

    @Test
    fun sseStreamingResponseParsing_emitsChunksAndCompletedEvent() = runTest {
        val mockInterceptor = MockInterceptor().apply {
            responseContentType = "text/event-stream"
            responseBodyString = """
                data: {"choices":[{"delta":{"content":"Check "}}]}
                data: {"choices":[{"delta":{"content":"power "}}]}
                data: {"choices":[{"delta":{"content":"supply."}}]}
                data: [DONE]
            """.trimIndent()
        }
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()

        val config = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "sk-stream-key")
        val service = OpenAICompatibleAIService(config, client)

        val events = service.stream(AIRequest(prompt = "Stream test")).toList()

        assertEquals(4, events.size)
        assertTrue(events[0] is AIStreamEvent.Chunk)
        assertEquals("Check ", (events[0] as AIStreamEvent.Chunk).text)

        assertTrue(events[1] is AIStreamEvent.Chunk)
        assertEquals("power ", (events[1] as AIStreamEvent.Chunk).text)

        assertTrue(events[2] is AIStreamEvent.Chunk)
        assertEquals("supply.", (events[2] as AIStreamEvent.Chunk).text)

        assertTrue(events[3] is AIStreamEvent.Completed)
        val completed = events[3] as AIStreamEvent.Completed
        assertEquals("Check power supply.", completed.response.text)
    }

    @Test
    fun errorHandling_missingApiKey_forRequiredProviders() = runTest {
        val config = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "")
        val service = OpenAICompatibleAIService(config)

        val response = service.generate(AIRequest(prompt = "No key"))
        assertNotNull(response.error)
        assertEquals(AIErrorCode.INVALID_API_KEY, response.error?.code)
    }

    @Test
    fun errorHandling_ollamaAndCustom_doNotRequireApiKey() = runTest {
        val ollamaConfig = AIProviderConfig(providerType = AIProviderType.OLLAMA, name = "Ollama", apiKey = "")
        val ollamaService = OpenAICompatibleAIService(ollamaConfig)
        assertTrue(ollamaService.isAvailable())

        val customConfig = AIProviderConfig(providerType = AIProviderType.CUSTOM, name = "Custom", apiKey = "")
        val customService = OpenAICompatibleAIService(customConfig)
        assertTrue(customService.isAvailable())
    }

    @Test
    fun errorHandling_httpErrorCodes() = runTest {
        val mockInterceptor = MockInterceptor()
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val config = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "sk-key")
        val service = OpenAICompatibleAIService(config, client)

        // HTTP 401
        mockInterceptor.responseCode = 401
        mockInterceptor.responseBodyString = """{"error":"Unauthorized"}"""
        var response = service.generate(AIRequest(prompt = "Test"))
        assertEquals(AIErrorCode.INVALID_API_KEY, response.error?.code)

        // HTTP 429
        mockInterceptor.responseCode = 429
        mockInterceptor.responseBodyString = """{"error":"Rate limit exceeded"}"""
        response = service.generate(AIRequest(prompt = "Test"))
        assertEquals(AIErrorCode.RATE_LIMITED, response.error?.code)

        // HTTP 404
        mockInterceptor.responseCode = 404
        mockInterceptor.responseBodyString = """{"error":"Model not found"}"""
        response = service.generate(AIRequest(prompt = "Test"))
        assertEquals(AIErrorCode.MODEL_NOT_FOUND, response.error?.code)

        // HTTP 500
        mockInterceptor.responseCode = 500
        mockInterceptor.responseBodyString = """{"error":"Internal server error"}"""
        response = service.generate(AIRequest(prompt = "Test"))
        assertEquals(AIErrorCode.INFERENCE_FAILED, response.error?.code)
    }

    @Test
    fun errorHandling_networkException() = runTest {
        val mockInterceptor = MockInterceptor().apply { throwNetworkError = true }
        val client = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val config = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "sk-key")
        val service = OpenAICompatibleAIService(config, client)

        val response = service.generate(AIRequest(prompt = "Network error test"))
        assertNotNull(response.error)
        assertEquals(AIErrorCode.NETWORK_ERROR, response.error?.code)
    }
}
