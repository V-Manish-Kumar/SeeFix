package com.example.seefix

import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIError
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIService
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.providers.AnthropicAIService
import com.example.seefix.ai.providers.GeminiAIService
import com.example.seefix.ai.providers.OpenAICompatibleAIService
import com.example.seefix.ai.router.AIMode
import com.example.seefix.ai.router.AIRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAndRouterAITest {

    private class TestMockAIService(
        override val providerConfig: AIProviderConfig,
        private val available: Boolean = true,
        private val capabilities: AICapabilities = AICapabilities(supportsText = true, supportsImages = true, supportsMultipleImages = true)
    ) : AIService {
        override suspend fun generate(request: AIRequest): AIResponse {
            return AIResponse(
                text = "Mock response to: ${request.prompt}",
                modelName = providerConfig.model,
                providerName = providerConfig.name
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            return flowOf(
                AIStreamEvent.Chunk("Mock chunk"),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = "Mock chunk",
                        modelName = providerConfig.model,
                        providerName = providerConfig.name
                    )
                )
            )
        }

        override suspend fun isAvailable(): Boolean = available
        override fun getCapabilities(): AICapabilities = capabilities
        override suspend fun loadModel(): Boolean = true
        override suspend fun unloadModel() {}
    }

    private class TestLocalGemmaAIService(
        private val available: Boolean = false,
        private val capabilities: AICapabilities = AICapabilities(supportsText = true, supportsImages = false, supportsMultipleImages = false)
    ) : LocalGemmaAIService(
        context = null,
        localModelManager = null
    ) {
        override suspend fun isAvailable(): Boolean = available
        override fun getCapabilities(): AICapabilities = capabilities
        override suspend fun generate(request: AIRequest): AIResponse {
            if (!available) {
                return AIResponse(
                    text = "",
                    modelName = providerConfig.model,
                    providerName = providerConfig.name,
                    error = AIError(AIErrorCode.PROVIDER_UNAVAILABLE, "Local AI unavailable. Install a compatible Gemma model or select another AI mode.")
                )
            }
            return AIResponse(
                text = "Local Gemma response to: ${request.prompt}",
                modelName = providerConfig.model,
                providerName = providerConfig.name
            )
        }
    }

    @Test
    fun registry_registersAndRetrievesServices() {
        val registry = AIProviderRegistry()
        val geminiConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Gemini",
            apiKey = "test_key"
        )
        val geminiService = registry.createProvider(geminiConfig)
        registry.registerProvider(geminiService)

        val retrieved = registry.getProvider(AIProviderType.GOOGLE_GEMINI)
        assertNotNull(retrieved)
        assertEquals(AIProviderType.GOOGLE_GEMINI, retrieved?.providerConfig?.providerType)
    }

    @Test
    fun router_localOnly_returnsErrorWhenLocalUnavailable() = runTest {
        val registry = AIProviderRegistry()
        val mockLocalGemma = TestLocalGemmaAIService(available = false)

        val router = AIRouter(registry, mockLocalGemma)
        val request = AIRequest(prompt = "Diagnose HVAC")

        val response = router.generate(request, mode = AIMode.LOCAL_ONLY)
        assertNotNull(response.error)
        assertEquals(AIErrorCode.PROVIDER_UNAVAILABLE, response.error?.code)
        assertTrue(response.error!!.message.contains("Local AI unavailable"))
    }

    @Test
    fun router_cloudOnly_routesToActiveCloudProvider() = runTest {
        val registry = AIProviderRegistry()
        val geminiConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Google Gemini",
            apiKey = "key_123",
            model = "gemini-1.5-flash"
        )
        val mockCloudService = TestMockAIService(geminiConfig)
        registry.registerProvider(mockCloudService)
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val mockLocalGemma = TestLocalGemmaAIService(available = false)

        val router = AIRouter(registry, mockLocalGemma)
        val request = AIRequest(prompt = "Check motor noise")

        val response = router.generate(request, mode = AIMode.CLOUD_ONLY)
        assertNull(response.error)
        assertEquals("Mock response to: Check motor noise", response.text)
        assertEquals("Google Gemini", response.providerName)
    }

    @Test
    fun router_auto_routesToCloudWhenMultimodalImageRequest() = runTest {
        val registry = AIProviderRegistry()
        val openAiConfig = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = "sk-test",
            model = "gpt-4o-mini"
        )
        val mockCloudService = TestMockAIService(openAiConfig)
        registry.registerProvider(mockCloudService)
        registry.setActiveCloudProviderType(AIProviderType.OPENAI)

        val mockLocalGemma = TestLocalGemmaAIService(available = true)

        val router = AIRouter(registry, mockLocalGemma)
        val request = AIRequest(
            prompt = "Identify this burned component",
            imageBytes = byteArrayOf(1, 2, 3, 4)
        )

        val response = router.generate(request, mode = AIMode.AUTO)
        assertEquals("OpenAI", response.providerName)
    }

    @Test
    fun providerAvailability_checksApiKeysCorrectly() = runTest {
        val emptyGeminiConfig = AIProviderConfig(AIProviderType.GOOGLE_GEMINI, "Gemini", apiKey = "")
        val geminiService = GeminiAIService(emptyGeminiConfig)
        assertFalse(geminiService.isAvailable())

        val validGeminiConfig = AIProviderConfig(AIProviderType.GOOGLE_GEMINI, "Gemini", apiKey = "key")
        val validGeminiService = GeminiAIService(validGeminiConfig)
        assertTrue(validGeminiService.isAvailable())

        val ollamaConfig = AIProviderConfig(AIProviderType.OLLAMA, "Ollama", baseUrl = "http://localhost:11434")
        val ollamaService = OpenAICompatibleAIService(ollamaConfig)
        assertTrue(ollamaService.isAvailable())

        val anthropicConfig = AIProviderConfig(AIProviderType.ANTHROPIC, "Claude", apiKey = "sk-ant-123")
        val anthropicService = AnthropicAIService(anthropicConfig)
        assertTrue(anthropicService.isAvailable())
    }
}
