package com.example.seefix

import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.DeviceCompatibilityInfo
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.local.LocalModelInfo
import com.example.seefix.ai.local.LocalModelManager
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.router.AIMode
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.ui.debug.LocalAITestViewModel
import com.example.seefix.ui.settings.AISettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAIAndSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class TestMockGemmaAIService : LocalGemmaAIService(
        context = null,
        localModelManager = null
    ) {
        var isModelLoadedState = false

        override suspend fun isAvailable(): Boolean = true
        override suspend fun loadModel(): Boolean {
            isModelLoadedState = true
            return true
        }

        override suspend fun unloadModel() {
            isModelLoadedState = false
        }

        override suspend fun generate(request: AIRequest): AIResponse {
            return AIResponse(
                text = "Mock Gemma response to ${request.prompt}",
                modelName = "gemma-2b-test",
                providerName = "Local Gemma Test"
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            return flowOf(
                AIStreamEvent.Chunk("Streaming token 1 "),
                AIStreamEvent.Chunk("Streaming token 2"),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = "Streaming token 1 Streaming token 2",
                        modelName = "gemma-2b-test",
                        providerName = "Local Gemma Test"
                    )
                )
            )
        }
    }

    private class TestMockModelManager : LocalModelManager(
        context = null
    ) {
        override fun getAvailableModels(): List<LocalModelInfo> = emptyList()
        override fun getDeviceCompatibilityInfo(modelSizeBytes: Long): DeviceCompatibilityInfo {
            return DeviceCompatibilityInfo(
                isCompatible = true,
                totalRamMb = 8000L,
                freeRamMb = 4000L,
                freeStorageMb = 10000L,
                hasGpuSupport = true,
                warningMessages = emptyList()
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun localAITestViewModel_promptUpdateAndGeneration_streamsResponse() = runTest {
        val mockGemma = TestMockGemmaAIService()
        val mockManager = TestMockModelManager()

        val viewModel = LocalAITestViewModel(
            context = null,
            localGemmaService = mockGemma,
            localModelManager = mockManager
        )

        viewModel.updatePrompt("How to test capacitor?")
        assertEquals("How to test capacitor?", viewModel.uiState.value.prompt)

        viewModel.loadModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(mockGemma.isModelLoadedState)

        viewModel.generateResponse()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Streaming token 1 Streaming token 2", viewModel.uiState.value.generatedText)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun localAITestViewModel_stopGeneration_resetsGeneratingState() = runTest {
        val mockGemma = TestMockGemmaAIService()
        val mockManager = TestMockModelManager()

        val viewModel = LocalAITestViewModel(
            context = null,
            localGemmaService = mockGemma,
            localModelManager = mockManager
        )

        viewModel.generateResponse("Why is motor overheating?")
        viewModel.stopGeneration()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals("Generation stopped by user", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun aiSettingsViewModel_updatesAIModeAndTestsConnection() = runTest {
        val mockPrefs = AIPreferenceManager(context = null)
        val mockManager = TestMockModelManager()
        val mockRegistry = AIProviderRegistry()
        val mockGemma = TestMockGemmaAIService()

        val viewModel = AISettingsViewModel(
            context = null,
            aiPreferenceManager = mockPrefs,
            localModelManager = mockManager,
            aiProviderRegistry = mockRegistry,
            localGemmaService = mockGemma
        )

        viewModel.updateAIMode(AIMode.LOCAL_ONLY)
        assertEquals(AIMode.LOCAL_ONLY, viewModel.uiState.value.aiMode)

        viewModel.updateActiveCloudProvider(AIProviderType.GOOGLE_GEMINI)
        assertEquals(AIProviderType.GOOGLE_GEMINI, viewModel.uiState.value.activeCloudProviderType)

        viewModel.updateCloudConfig(
            baseUrl = "https://generativelanguage.googleapis.com",
            apiKey = "AIzaSyTest12345678",
            modelName = "gemini-1.5-flash",
            timeoutSeconds = 45L
        )

        assertEquals("https://generativelanguage.googleapis.com", viewModel.uiState.value.baseUrl)
        assertEquals("gemini-1.5-flash", viewModel.uiState.value.modelName)
        assertEquals(45L, viewModel.uiState.value.timeoutSeconds)
    }
}
