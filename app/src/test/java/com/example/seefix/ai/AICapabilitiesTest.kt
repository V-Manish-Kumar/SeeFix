package com.example.seefix.ai

import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.core.AIImage
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequirements
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIService
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.router.AIMode
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AICapabilitiesTest {

    private class TestLocalGemmaService(
        var available: Boolean = true
    ) : LocalGemmaAIService(context = null, localModelManager = null) {
        override suspend fun isAvailable(): Boolean = available

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

        override suspend fun generate(request: AIRequest): AIResponse {
            return AIResponse(
                text = "Local Gemma response",
                modelName = "gemma-2b-it",
                providerName = "Local Gemma"
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            return flowOf(
                AIStreamEvent.Chunk("Local Gemma response"),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = "Local Gemma response",
                        modelName = "gemma-2b-it",
                        providerName = "Local Gemma"
                    )
                )
            )
        }
    }

    private class TestCloudService(
        override val providerConfig: AIProviderConfig,
        var available: Boolean = true,
        var customCapabilities: AICapabilities = AICapabilities(
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
    ) : AIService {
        override suspend fun isAvailable(): Boolean = available
        override fun getCapabilities(): AICapabilities = customCapabilities

        override suspend fun generate(request: AIRequest): AIResponse {
            return AIResponse(
                text = "Cloud AI response",
                modelName = providerConfig.model,
                providerName = providerConfig.name
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            return flowOf(
                AIStreamEvent.Chunk("Cloud AI response"),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = "Cloud AI response",
                        modelName = providerConfig.model,
                        providerName = providerConfig.name
                    )
                )
            )
        }

        override suspend fun loadModel(): Boolean = true
        override suspend fun unloadModel() {}
    }

    // 1. Requirement Computation Tests
    @Test
    fun computeRequirements_forTextOnlyRequest_returnsDefaultTextRequirements() {
        val request = AIRequest(prompt = "How do I diagnose a circuit breaker?")
        val reqs = request.computeRequirements()

        assertTrue(reqs.requiresText)
        assertFalse(reqs.requiresImages)
        assertFalse(reqs.requiresMultipleImages)
        assertFalse(reqs.requiresVideoFrames)
        assertFalse(reqs.requiresToolCalling)
        assertFalse(reqs.requiresStructuredOutput)
        assertEquals(0, reqs.requiredImageCount)
    }

    @Test
    fun computeRequirements_forSingleImageBytes_returnsSingleImageRequirement() {
        val request = AIRequest(
            prompt = "What is this component?",
            imageBytes = byteArrayOf(1, 2, 3, 4)
        )
        val reqs = request.computeRequirements()

        assertTrue(reqs.requiresText)
        assertTrue(reqs.requiresImages)
        assertFalse(reqs.requiresMultipleImages)
        assertEquals(1, reqs.requiredImageCount)
    }

    @Test
    fun computeRequirements_forMultipleImages_returnsMultipleImageRequirement() {
        val request = AIRequest(
            prompt = "Compare these two components",
            images = listOf(
                AIImage(uri = "file://img1.jpg"),
                AIImage(uri = "file://img2.jpg")
            )
        )
        val reqs = request.computeRequirements()

        assertTrue(reqs.requiresImages)
        assertTrue(reqs.requiresMultipleImages)
        assertEquals(2, reqs.requiredImageCount)
    }

    @Test
    fun computeRequirements_forVideoContext_returnsVideoFrameRequirements() {
        val request = AIRequest(
            prompt = "Analyze video frames",
            videoContext = VideoContext(
                videoUri = "file://video.mp4",
                selectedFrames = listOf(
                    AIImage(uri = "frame1.jpg"),
                    AIImage(uri = "frame2.jpg"),
                    AIImage(uri = "frame3.jpg")
                )
            )
        )
        val reqs = request.computeRequirements()

        assertTrue(reqs.requiresImages)
        assertTrue(reqs.requiresMultipleImages)
        assertTrue(reqs.requiresVideo)
        assertTrue(reqs.requiresVideoFrames)
        assertEquals(3, reqs.requiredImageCount)
    }

    @Test
    fun computeRequirements_forToolCallingAndWorkContext_returnsToolAndStructuredRequirements() {
        val tool = ToolItem(id = "multimeter", name = "Multimeter", isAvailable = true)
        val workCtx = WorkContext(
            domain = WorkDomain.ELECTRICAL,
            userInput = "Test circuit"
        )
        val request = AIRequest(
            prompt = "Measure voltage",
            availableTools = listOf(tool),
            workContext = workCtx
        )
        val reqs = request.computeRequirements()

        assertTrue(reqs.requiresToolCalling)
        assertTrue(reqs.requiresStructuredOutput)
    }

    // 2. Capability Matching Tests
    @Test
    fun satisfies_whenAllRequirementsMet_returnsTrue() {
        val caps = AICapabilities(
            supportsText = true,
            supportsImages = true,
            supportsMultipleImages = true,
            supportsVideoFrames = true,
            maxImages = 10
        )
        val reqs = AIRequirements(
            requiresText = true,
            requiresImages = true,
            requiresMultipleImages = true,
            requiredImageCount = 5
        )

        assertTrue(caps.satisfies(reqs))
    }

    @Test
    fun satisfies_whenImageRequiredButUnsupported_returnsFalse() {
        val caps = AICapabilities(supportsText = true, supportsImages = false)
        val reqs = AIRequirements(requiresImages = true, requiredImageCount = 1)

        assertFalse(caps.satisfies(reqs))
    }

    @Test
    fun satisfies_whenMultipleImagesRequiredButUnsupported_returnsFalse() {
        val caps = AICapabilities(supportsText = true, supportsImages = true, supportsMultipleImages = false)
        val reqs = AIRequirements(requiresImages = true, requiresMultipleImages = true, requiredImageCount = 2)

        assertFalse(caps.satisfies(reqs))
    }

    @Test
    fun satisfies_whenImageCountExceedsMaxImages_returnsFalse() {
        val caps = AICapabilities(
            supportsText = true,
            supportsImages = true,
            supportsMultipleImages = true,
            maxImages = 5
        )
        val reqs = AIRequirements(requiresImages = true, requiresMultipleImages = true, requiredImageCount = 10)

        assertFalse(caps.satisfies(reqs))
    }

    // 3. Local Gemma Restrictions
    @Test
    fun localGemmaCapabilities_satisfiesTextOnlyRequest() {
        val localGemma = TestLocalGemmaService()
        val caps = localGemma.getCapabilities()
        val textReqs = AIRequirements(requiresText = true, requiresImages = false, requiredImageCount = 0)

        assertTrue(caps.satisfies(textReqs))
    }

    @Test
    fun localGemmaCapabilities_doesNotSatisfyVisualOrToolRequests() {
        val localGemma = TestLocalGemmaService()
        val caps = localGemma.getCapabilities()

        val imageReqs = AIRequirements(requiresImages = true, requiredImageCount = 1)
        val videoFrameReqs = AIRequirements(requiresVideoFrames = true, requiredImageCount = 3)
        val toolReqs = AIRequirements(requiresToolCalling = true)

        assertFalse(caps.satisfies(imageReqs))
        assertFalse(caps.satisfies(videoFrameReqs))
        assertFalse(caps.satisfies(toolReqs))
    }

    // 4. Router Capability Decision Tests
    @Test
    fun router_autoMode_routesTextOnlyToLocalGemma() = runTest {
        val registry = AIProviderRegistry()
        val localService = TestLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(prompt = "Explain Ohm's Law")
        val response = router.generate(request, mode = AIMode.AUTO)

        assertNull(response.error)
        assertEquals("Local Gemma", response.providerName)
    }

    @Test
    fun router_autoMode_routesImageRequestToCloudProvider() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Gemini AI",
            apiKey = "valid_key"
        )
        val cloudService = TestCloudService(cloudConfig, available = true)
        registry.registerProvider(cloudService)
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val localService = TestLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(
            prompt = "What is shown in this picture?",
            images = listOf(AIImage(uri = "file://capacitor.jpg"))
        )
        val response = router.generate(request, mode = AIMode.AUTO)

        assertNull(response.error)
        assertEquals("Gemini AI", response.providerName)
    }

    @Test
    fun router_autoMode_returnsUnsupportedCapability_whenNoProviderSupportsImages() = runTest {
        val registry = AIProviderRegistry()
        val localService = TestLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(
            prompt = "Inspect circuit",
            images = listOf(AIImage(uri = "file://board.jpg"))
        )
        val response = router.generate(request, mode = AIMode.AUTO)

        assertNotNull(response.error)
        assertEquals(AIErrorCode.UNSUPPORTED_CAPABILITY, response.error?.code)
        assertTrue(response.error?.message?.contains("No active AI provider supports the required capabilities") == true)
        assertTrue(response.error?.message?.contains("images: 1") == true)
    }

    @Test
    fun router_localOnlyMode_returnsTextOnlyError_whenVisualInputProvided() = runTest {
        val registry = AIProviderRegistry()
        val localService = TestLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(
            prompt = "Analyze thermal video",
            videoContext = VideoContext(
                videoUri = "file://thermal.mp4",
                selectedFrames = listOf(AIImage(uri = "frame1.jpg"))
            )
        )
        val response = router.generate(request, mode = AIMode.LOCAL_ONLY)

        assertNotNull(response.error)
        assertEquals(AIErrorCode.UNSUPPORTED_CAPABILITY, response.error?.code)
        assertEquals(
            "Local Gemma engine is text-only and cannot process visual inputs. Select AUTO or CLOUD_ONLY AI mode for image/video analysis.",
            response.error?.message
        )
    }

    @Test
    fun router_cloudOnlyMode_returnsUnsupportedCapability_whenActiveCloudDoesNotSatisfy() = runTest {
        val registry = AIProviderRegistry()
        val cloudConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Gemini AI",
            apiKey = "key"
        )
        // Cloud service with maxImages = 1
        val cloudService = TestCloudService(
            cloudConfig,
            available = true,
            customCapabilities = AICapabilities(supportsText = true, supportsImages = true, supportsMultipleImages = false, maxImages = 1)
        )
        registry.registerProvider(cloudService)
        registry.setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)

        val localService = TestLocalGemmaService(available = true)
        val router = AIRouter(registry, localService)

        val request = AIRequest(
            prompt = "Compare 3 components",
            images = listOf(
                AIImage(uri = "1.jpg"),
                AIImage(uri = "2.jpg"),
                AIImage(uri = "3.jpg")
            )
        )
        val response = router.generate(request, mode = AIMode.CLOUD_ONLY)

        assertNotNull(response.error)
        assertEquals(AIErrorCode.UNSUPPORTED_CAPABILITY, response.error?.code)
        assertTrue(response.error?.message?.contains("No active AI provider supports the required capabilities") == true)
    }
}
