package com.example.seefix.ui.debug

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.DeviceCompatibilityInfo
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.local.LocalModelInfo
import com.example.seefix.ai.local.LocalModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocalAITestUiState(
    val isModelLoaded: Boolean = false,
    val activeModelInfo: LocalModelInfo? = null,
    val compatibilityInfo: DeviceCompatibilityInfo? = null,
    val isOnline: Boolean = true,
    val isGenerating: Boolean = false,
    val prompt: String = "Why is motor overheating?",
    val generatedText: String = "",
    val statusMessage: String = "Ready for local Gemma AI evaluation",
    val availableModels: List<LocalModelInfo> = emptyList(),
    val capabilities: List<String> = listOf("TEXT", "STREAMING", "LOCAL_OFFLINE")
)

class LocalAITestViewModel(
    private val context: Context? = null,
    val localGemmaService: LocalGemmaAIService = LocalGemmaAIService(context),
    val localModelManager: LocalModelManager = LocalModelManager(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalAITestUiState())
    val uiState: StateFlow<LocalAITestUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            val models = localModelManager.getAvailableModels()
            val activeModel = models.firstOrNull()
            val compat = localModelManager.getDeviceCompatibilityInfo(activeModel?.sizeBytes ?: 0L)
            val online = checkInternetStatus(context)

            val isAvailable = localGemmaService.isAvailable()

            _uiState.update { currentState ->
                currentState.copy(
                    activeModelInfo = activeModel,
                    availableModels = models,
                    compatibilityInfo = compat,
                    isOnline = online,
                    isModelLoaded = isAvailable,
                    statusMessage = if (isAvailable) {
                        "Local Gemma model file detected and ready"
                    } else if (models.isNotEmpty()) {
                        "Model file present. Tap 'Load Model' to initialize"
                    } else {
                        "No local model (.bin / .task) found. Import a model file to begin."
                    }
                )
            }
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Loading local Gemma model into LiteRT/MediaPipe...") }
            val loaded = localGemmaService.loadModel()
            _uiState.update {
                it.copy(
                    isModelLoaded = loaded,
                    statusMessage = if (loaded) {
                        "Model loaded successfully on GPU/LiteRT engine!"
                    } else {
                        "Failed to load model. Ensure valid .bin/.task file and adequate RAM."
                    }
                )
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Unloading model from RAM...") }
            localGemmaService.unloadModel()
            _uiState.update {
                it.copy(
                    isModelLoaded = false,
                    statusMessage = "Model unloaded from memory"
                )
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Importing model file from URI...") }
            val result = localModelManager.importModelFromUri(uri)
            if (result.isSuccess) {
                val imported = result.getOrNull()
                _uiState.update {
                    it.copy(
                        statusMessage = "Successfully imported ${imported?.name ?: "model file"}!"
                    )
                }
                refreshState()
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "Model import failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun updatePrompt(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt) }
    }

    fun generateResponse(promptToUse: String = _uiState.value.prompt) {
        if (promptToUse.isBlank()) return

        generationJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = true,
                generatedText = "",
                prompt = promptToUse,
                statusMessage = "Streaming local Gemma response token-by-token..."
            )
        }

        generationJob = viewModelScope.launch {
            try {
                localGemmaService.stream(AIRequest(prompt = promptToUse)).collect { event ->
                    when (event) {
                        is AIStreamEvent.Chunk -> {
                            _uiState.update { currentState ->
                                currentState.copy(generatedText = currentState.generatedText + event.text)
                            }
                        }
                        is AIStreamEvent.Completed -> {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isGenerating = false,
                                    statusMessage = "Generation completed successfully"
                                )
                            }
                        }
                        is AIStreamEvent.Error -> {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isGenerating = false,
                                    statusMessage = "Generation error: ${event.error.message}"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isGenerating = false,
                        statusMessage = "Stream error: ${e.message}"
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update {
            it.copy(
                isGenerating = false,
                statusMessage = "Generation stopped by user"
            )
        }
    }

    fun clearGeneratedText() {
        _uiState.update { it.copy(generatedText = "") }
    }

    private fun checkInternetStatus(context: Context?): Boolean {
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LocalAITestViewModel(context.applicationContext) as T
            }
        }
    }
}
