package com.example.seefix.ai

import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIService
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.providers.AnthropicAIService
import com.example.seefix.ai.providers.GeminiAIService
import com.example.seefix.ai.providers.OpenAICompatibleAIService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIProviderRegistryTest {

    private class TestService(
        override val providerConfig: AIProviderConfig
    ) : AIService {
        override suspend fun generate(request: AIRequest): AIResponse = AIResponse(text = "ok", modelName = "m", providerName = "p")
        override fun stream(request: AIRequest): Flow<AIStreamEvent> = emptyFlow()
        override suspend fun isAvailable(): Boolean = true
        override fun getCapabilities(): AICapabilities = AICapabilities()
        override suspend fun loadModel(): Boolean = true
        override suspend fun unloadModel() {}
    }

    @Test
    fun register_retrieve_andUnregisterProviders() {
        val registry = AIProviderRegistry()
        val geminiConfig = AIProviderConfig(providerType = AIProviderType.GOOGLE_GEMINI, name = "Gemini", apiKey = "key1")
        val openAiConfig = AIProviderConfig(providerType = AIProviderType.OPENAI, name = "OpenAI", apiKey = "key2")

        val geminiService = TestService(geminiConfig)
        val openAiService = TestService(openAiConfig)

        registry.registerProvider(geminiService)
        registry.registerProvider(openAiService)

        assertEquals(2, registry.getAllProviders().size)
        assertEquals(geminiService, registry.getProvider(AIProviderType.GOOGLE_GEMINI))
        assertEquals(openAiService, registry.getProvider(AIProviderType.OPENAI))

        // Unregister
        registry.unregisterProvider(AIProviderType.GOOGLE_GEMINI)
        assertNull(registry.getProvider(AIProviderType.GOOGLE_GEMINI))
        assertEquals(1, registry.getAllProviders().size)

        // Clear
        registry.clear()
        assertTrue(registry.getAllProviders().isEmpty())
    }

    @Test
    fun activeCloudProviderManagement() {
        val registry = AIProviderRegistry()
        assertEquals(AIProviderType.GOOGLE_GEMINI, registry.getActiveCloudProviderType())

        val anthropicConfig = AIProviderConfig(providerType = AIProviderType.ANTHROPIC, name = "Anthropic", apiKey = "ant_key")
        val anthropicService = TestService(anthropicConfig)
        registry.registerProvider(anthropicService)

        registry.setActiveCloudProviderType(AIProviderType.ANTHROPIC)
        assertEquals(AIProviderType.ANTHROPIC, registry.getActiveCloudProviderType())
        assertEquals(anthropicService, registry.getActiveCloudProvider())
    }

    @Test
    fun factory_createProvider_createsGeminiProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Google Gemini",
            apiKey = "gemini_key_123"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is GeminiAIService)
        assertEquals(AIProviderType.GOOGLE_GEMINI, provider.providerConfig.providerType)
    }

    @Test
    fun factory_createProvider_createsOpenAIProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = "sk-12345"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is OpenAICompatibleAIService)
        assertEquals(AIProviderType.OPENAI, provider.providerConfig.providerType)
    }

    @Test
    fun factory_createProvider_createsOpenRouterProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.OPENROUTER,
            name = "OpenRouter",
            apiKey = "sk-or-12345"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is OpenAICompatibleAIService)
        assertEquals(AIProviderType.OPENROUTER, provider.providerConfig.providerType)
    }

    @Test
    fun factory_createProvider_createsAnthropicProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.ANTHROPIC,
            name = "Anthropic",
            apiKey = "sk-ant-12345"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is AnthropicAIService)
        assertEquals(AIProviderType.ANTHROPIC, provider.providerConfig.providerType)
    }

    @Test
    fun factory_createProvider_createsOllamaProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.OLLAMA,
            name = "Ollama Local",
            baseUrl = "http://localhost:11434"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is OpenAICompatibleAIService)
        assertEquals(AIProviderType.OLLAMA, provider.providerConfig.providerType)
    }

    @Test
    fun factory_createProvider_createsCustomProvider() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.CUSTOM,
            name = "Custom Endpoint",
            baseUrl = "https://custom-ai.example.com"
        )
        val provider = registry.createProvider(config)
        assertTrue(provider is OpenAICompatibleAIService)
        assertEquals(AIProviderType.CUSTOM, provider.providerConfig.providerType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun factory_createProvider_localGemmaWithoutContext_throwsException() {
        val registry = AIProviderRegistry()
        val config = AIProviderConfig(
            providerType = AIProviderType.LOCAL_GEMMA,
            name = "Local Gemma"
        )
        registry.createProvider(config, context = null)
    }
}
