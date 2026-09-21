package com.example.seefix.ai

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
import com.example.seefix.ai.router.AIMode
import com.example.seefix.ai.router.AIRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AIRouterTest {

    private class MockLocalGemmaService(
        var available: Boolean = true,
        var customCapabilities: AICapabilities = AICapabilities(
            supportsText = true,
            supportsImages = false,
            supportsMultipleImages = false
        )
    ) : LocalGemmaAIService(context = null, localModelManager = null) {

        override suspend fun isAvailable(): Boolean = available
        override fun getCapabilities(): AICapabilities = customCapabilities

        override suspend fun generate(request: AIRequest): AIResponse {
            if (!available) {
                return AIResponse(
                    text = "",
                    modelName = providerConfig.model,
                    providerName = providerConfig.name,
                    error = AIError(
                        AIErrorCode.PROVIDER_UNAVAILABLE,
                        "Local AI unavailable. Install a compatible Gemma model or select another AI mode."
                    )
                )
            }
            return AIResponse(
                text = "Local Gemma: ${request.prompt}",
                modelName = "gemma-2b-local",
                providerName = "Local Gemma"
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            if (!available) {
                return flowOf(
                    AIStreamEvent.Error(
                        AIError(
                            AIErrorCode.PROVIDER_UNAVAILABLE,
                            "Local AI unavailable. Install a compatible Gemma model or select another AI mode."
                        )
                    )
                )
            }
            return flowOf(
                AIStreamEvent.Chunk("Local chunk"),
                AIStreamEvent.Completed(
                    AIResponse(text = "Local chunk", modelName = "gemma-2b-local", providerName = "Local Gemma")
                )
            )
        }
    }

    private class MockCloudService(
        override val providerConfig: AIProviderConfig,
        var available: Boolean = true,
        var customCapabilities: AICapabilities = AICapabilities(
            supportsText = true,
            supportsImages = true,
            supportsMultipleImages = true,
            maxImages = 20
        )
    ) : AIService {
        override suspend fun isAvailable(): Boolean = available
        override fun getCapabilities(): AICapabilities = customCapabilities

        override suspend fun generate(request: AIRequest): AIResponse {
            if (!available) {
                return AIResponse(
                    text = "",
                    modelName = providerConfig.model,
                    providerName = providerConfig.name,
                    error = AIError(
                        AIErrorCode.PROVIDER_UNAVAILABLE,
                        "Cloud AI provider unavailable. Configure a cloud AI provider or select another AI mode."
                    )
                )
            }
            return AIResponse(
                text = "Cloud (${providerConfig.name}): ${request.prompt}",
                modelName = providerConfig.model,
                providerName = providerConfig.name
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            if (!available) {
                return flowOf(
                    AIStreamEvent.Error(
                        AIError(
                            AIErrorCode.PROVIDER_UNAVAILABLE,
                            "Cloud AI provider unavailable. Configure a cloud AI provider or select another AI mode."
                        )
                    )
                )
            }
            return flowOf(
                AIStreamEvent.Chunk("Cloud chunk"),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = "Cloud chunk",
                        modelName = providerConfig.model,
                        providerName = providerConfig.name
                    )
                )
            )
        }

        override suspend fun loadModel(): Boolean = true
        override suspend fun unloadModel() {}
    }

    @Test
    fun localOnly_whenLocalModelAvailable_routesToLocalGemma() = runTest {
        val registry = AIProviderRegistry()
        val localService = MockLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Inspect circuit board")
        val response = router.generate(request, mode = AIMode.LOCAL_ONLY)

        assertNull(response.error)
        assertEquals("Local Gemma: Inspect circuit board", response.text)
        assertEquals("Local Gemma", response.providerName)
    }

    @Test
    fun localOnly_whenLocalModelUnavailable_returnsErrorAndNeverSwitchesToCloud() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Gemini Cloud",
            apiKey = "valid_key"
        )
        val cloudService = MockCloudService(cloudConfig, available = true)
        registry.registerProvider(cloudService)
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val localService = MockLocalGemmaService(available = false)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Check wiring")
        val response = router.generate(request, mode = AIMode.LOCAL_ONLY)

        assertNotNull(response.error)
        assertEquals(AIErrorCode.PROVIDER_UNAVAILABLE, response.error?.code)
        assertEquals(
            "Local AI unavailable. Install a compatible Gemma model or select another AI mode.",
            response.error?.message
        )

        // Stream verification
        val events = router.stream(request, mode = AIMode.LOCAL_ONLY).toList()
        assertEquals(1, events.size)
        assertTrue(events.first() is AIStreamEvent.Error)
        val errEvent = events.first() as AIStreamEvent.Error
        assertEquals(
            "Local AI unavailable. Install a compatible Gemma model or select another AI mode.",
            errEvent.error.message
        )
    }

    @Test
    fun cloudOnly_whenCloudProviderAvailable_routesToSelectedCloudProvider() = runTest {
        val registry = AIProviderRegistry()
        val openAiConfig = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = "sk-test",
            model = "gpt-4o"
        )
        val cloudService = MockCloudService(openAiConfig, available = true)
        registry.registerProvider(cloudService)
        registry.setActiveCloudProviderType(AIProviderType.OPENAI)

        val localService = MockLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Diagnose pressure switch")
        val response = router.generate(request, mode = AIMode.CLOUD_ONLY)

        assertNull(response.error)
        assertEquals("Cloud (OpenAI): Diagnose pressure switch", response.text)
        assertEquals("OpenAI", response.providerName)
    }

    @Test
    fun cloudOnly_whenCloudProviderUnavailable_returnsProviderUnavailableError() = runTest {
        val registry = AIProviderRegistry()
        val openAiConfig = AIProviderConfig(
            providerType = AIProviderType.OPENAI,
            name = "OpenAI",
            apiKey = ""
        )
        val cloudService = MockCloudService(openAiConfig, available = false)
        registry.registerProvider(cloudService)
        registry.setActiveCloudProviderType(AIProviderType.OPENAI)

        val localService = MockLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Diagnose pressure switch")
        val response = router.generate(request, mode = AIMode.CLOUD_ONLY)

        assertNotNull(response.error)
        assertEquals(AIErrorCode.PROVIDER_UNAVAILABLE, response.error?.code)
        assertEquals(
            "Cloud AI provider unavailable. Configure a cloud AI provider or select another AI mode.",
            response.error?.message
        )
    }

    @Test
    fun auto_whenLocalAvailable_forTextRequest_routesToLocalGemma() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Google Gemini",
            apiKey = "key"
        )
        registry.registerProvider(MockCloudService(cloudConfig, available = true))
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val localService = MockLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Text only question about resistor color code")
        val response = router.generate(request, mode = AIMode.AUTO)

        assertNull(response.error)
        assertEquals("Local Gemma", response.providerName)
        assertEquals("Local Gemma: Text only question about resistor color code", response.text)
    }

    @Test
    fun auto_whenLocalUnavailable_fallsBackToActiveCloudProvider() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Google Gemini",
            apiKey = "key"
        )
        registry.registerProvider(MockCloudService(cloudConfig, available = true))
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val localService = MockLocalGemmaService(available = false)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Fallback test question")
        val response = router.generate(request, mode = AIMode.AUTO)

        assertNull(response.error)
        assertEquals("Google Gemini", response.providerName)
        assertEquals("Cloud (Google Gemini): Fallback test question", response.text)
    }

    @Test
    fun auto_whenLocalAvailableButCannotHandleImage_routesToCloudProvider() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.ANTHROPIC,
            name = "Anthropic Claude",
            apiKey = "sk-ant"
        )
        registry.registerProvider(MockCloudService(cloudConfig, available = true))
        registry.setActiveCloudProviderType(AIProviderType.ANTHROPIC)

        val localService = MockLocalGemmaService(
            available = true,
            customCapabilities = AICapabilities(supportsText = true, supportsImages = false, supportsMultipleImages = false)
        )
        val router = AIRouter(registry, localService)

        val request = AIRequest(
            prompt = "What is this image?",
            imageBytes = byteArrayOf(1, 2, 3)
        )

        val response = router.generate(request, mode = AIMode.AUTO)

        assertNull(response.error)
        assertEquals("Anthropic Claude", response.providerName)
    }
}
