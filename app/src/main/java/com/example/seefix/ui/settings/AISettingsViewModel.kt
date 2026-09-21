package com.example.seefix.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.local.DeviceCompatibilityInfo
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.local.LocalModelInfo
import com.example.seefix.ai.local.LocalModelManager
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.router.AIMode
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.data.local.AIPreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AISettingsUiState(
    val aiMode: AIMode = AIMode.AUTO,
    val activeCloudProviderType: AIProviderType = AIProviderType.GOOGLE_GEMINI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val maskedApiKey: String = "",
    val modelName: String = "",
    val timeoutSeconds: Long = 30L,
    val availableLocalModels: List<LocalModelInfo> = emptyList(),
    val activeLocalModel: LocalModelInfo? = null,
    val compatibilityInfo: DeviceCompatibilityInfo? = null,
    val isTestingConnection: Boolean = false,
    val testConnectionResult: String? = null,
    val statusMessage: String = "Settings synced"
)

class AISettingsViewModel(
    private val context: Context? = null,
    val aiPreferenceManager: AIPreferenceManager = AIPreferenceManager(context),
    val localModelManager: LocalModelManager = LocalModelManager(context),
    val aiProviderRegistry: AIProviderRegistry = AIProviderRegistry(),
    val localGemmaService: LocalGemmaAIService = LocalGemmaAIService(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AISettingsUiState())
    val uiState: StateFlow<AISettingsUiState> = _uiState.asStateFlow()

    private val aiRouter: AIRouter = AIRouter(aiProviderRegistry, localGemmaService)

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            val mode = aiPreferenceManager.getAIMode()
            val activeCloudType = aiPreferenceManager.getActiveCloudProviderType()
            val config = aiPreferenceManager.getProviderConfig(activeCloudType)
            val maskedKey = aiPreferenceManager.getMaskedApiKey(activeCloudType)

            val localModels = localModelManager.getAvailableModels()
            val activeLocal = localModels.firstOrNull()
            val compat = localModelManager.getDeviceCompatibilityInfo(activeLocal?.sizeBytes ?: 0L)

            // Register provider instance into registry
            val service = aiProviderRegistry.createProvider(config, context)
            aiProviderRegistry.registerProvider(service)
            aiProviderRegistry.setActiveCloudProviderType(activeCloudType)

            _uiState.update {
                it.copy(
                    aiMode = mode,
                    activeCloudProviderType = activeCloudType,
                    baseUrl = config.baseUrl,
                    apiKey = config.apiKey,
                    maskedApiKey = maskedKey,
                    modelName = config.model,
                    timeoutSeconds = config.timeoutSeconds,
                    availableLocalModels = localModels,
                    activeLocalModel = activeLocal,
                    compatibilityInfo = compat,
                    statusMessage = "Loaded preferences for ${config.name}"
                )
            }
        }
    }

    fun updateAIMode(mode: AIMode) {
        aiPreferenceManager.setAIMode(mode)
        _uiState.update {
            it.copy(
                aiMode = mode,
                statusMessage = "AI Routing mode set to ${mode.name}"
            )
        }
    }

    fun updateActiveCloudProvider(providerType: AIProviderType) {
        aiPreferenceManager.setActiveCloudProviderType(providerType)
        aiProviderRegistry.setActiveCloudProviderType(providerType)

        val config = aiPreferenceManager.getProviderConfig(providerType)
        val maskedKey = aiPreferenceManager.getMaskedApiKey(providerType)

        // Ensure service is registered
        val service = aiProviderRegistry.createProvider(config, context)
        aiProviderRegistry.registerProvider(service)

        _uiState.update {
            it.copy(
                activeCloudProviderType = providerType,
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                maskedApiKey = maskedKey,
                modelName = config.model,
                timeoutSeconds = config.timeoutSeconds,
                statusMessage = "Active provider changed to ${config.name}"
            )
        }
    }

    fun updateCloudConfig(
        baseUrl: String = _uiState.value.baseUrl,
        apiKey: String = _uiState.value.apiKey,
        modelName: String = _uiState.value.modelName,
        timeoutSeconds: Long = _uiState.value.timeoutSeconds
    ) {
        val providerType = _uiState.value.activeCloudProviderType
        val newConfig = AIProviderConfig(
            providerType = providerType,
            name = getProviderName(providerType),
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = modelName,
            timeoutSeconds = timeoutSeconds,
            streamingEnabled = true
        )

        aiPreferenceManager.saveProviderConfig(newConfig)

        val updatedService = aiProviderRegistry.createProvider(newConfig, context)
        aiProviderRegistry.registerProvider(updatedService)

        val maskedKey = aiPreferenceManager.getMaskedApiKey(providerType)

        _uiState.update {
            it.copy(
                baseUrl = baseUrl,
                apiKey = apiKey,
                maskedApiKey = maskedKey,
                modelName = modelName,
                timeoutSeconds = timeoutSeconds,
                statusMessage = "Saved configuration for ${newConfig.name}"
            )
        }
    }

    fun scanLocalModels() {
        viewModelScope.launch {
            val localModels = localModelManager.getAvailableModels()
            val activeLocal = localModels.firstOrNull()
            val compat = localModelManager.getDeviceCompatibilityInfo(activeLocal?.sizeBytes ?: 0L)

            _uiState.update {
                it.copy(
                    availableLocalModels = localModels,
                    activeLocalModel = activeLocal,
                    compatibilityInfo = compat,
                    statusMessage = "Scanned ${localModels.size} local model files"
                )
            }
        }
    }

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Importing model file from $uri...") }
            val result = localModelManager.importModelFromUri(uri)
            if (result.isSuccess) {
                scanLocalModels()
                _uiState.update {
                    it.copy(statusMessage = "Successfully imported local model!")
                }
            } else {
                _uiState.update {
                    it.copy(statusMessage = "Import failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun testProviderConnection() {
        _uiState.update {
            it.copy(
                isTestingConnection = true,
                testConnectionResult = null,
                statusMessage = "Testing AI provider connection..."
            )
        }

        viewModelScope.launch {
            val currentMode = _uiState.value.aiMode
            val request = AIRequest(prompt = "Hello! Connection diagnostic check. Reply in one line.")

            try {
                val response = aiRouter.generate(request, mode = currentMode)
                if (response.error == null && response.text.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            testConnectionResult = "SUCCESS (${response.providerName} - ${response.modelName}): \"${response.text.take(100).trim()}\"",
                            statusMessage = "Provider connection verified successfully!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            testConnectionResult = "FAILED: ${response.error?.message ?: "Unknown provider error"}",
                            statusMessage = "Connection test failed."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingConnection = false,
                        testConnectionResult = "FAILED EXCEPTION: ${e.message}",
                        statusMessage = "Connection test threw an exception."
                    )
                }
            }
        }
    }

    private fun getProviderName(type: AIProviderType): String {
        return when (type) {
            AIProviderType.LOCAL_GEMMA -> "Local Gemma On-Device"
            AIProviderType.GOOGLE_GEMINI -> "Google Gemini Cloud"
            AIProviderType.OPENAI -> "OpenAI"
            AIProviderType.OPENROUTER -> "OpenRouter"
            AIProviderType.ANTHROPIC -> "Anthropic Claude"
            AIProviderType.OLLAMA -> "Ollama Local API"
            AIProviderType.CUSTOM -> "Custom OpenAI-Compatible API"
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AISettingsViewModel(context.applicationContext) as T
            }
        }
    }
}
