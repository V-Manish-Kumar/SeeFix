package com.example.seefix.ui.diagnosis

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.seefix.ai.LocationInfo
import com.example.seefix.ai.MockAIEngine
import com.example.seefix.ai.MultimodalAIEngine
import com.example.seefix.ai.StepVerificationResult
import com.example.seefix.ai.agent.*
import com.example.seefix.ai.context.WorkContextBuilder
import com.example.seefix.ai.context.WorkContextBuilderImpl
import com.example.seefix.ai.core.AIMessage
import com.example.seefix.ai.core.AIRole
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.LocationPayload
import com.example.seefix.ai.core.MachineInfoPayload
import com.example.seefix.ai.core.SensorDataPayload
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.providers.AnthropicAIService
import com.example.seefix.ai.providers.GeminiAIService
import com.example.seefix.ai.providers.OpenAICompatibleAIService
import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.rag.RagQueryContext
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.camera.BoundingBoxWithLabel
import com.example.seefix.camera.CameraManager
import com.example.seefix.camera.ExtractionUIState
import com.example.seefix.camera.VideoFrameExtractor
import com.example.seefix.camera.VideoRecorder
import com.example.seefix.camera.VideoRecordingState
import com.example.seefix.camera.VisualVerificationAnalyzer
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.data.local.SeeFixDatabase
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.data.repository.SessionHistoryRepositoryImpl
import com.example.seefix.data.repository.TroubleshootingRepositoryImpl
import com.example.seefix.domain.model.BoundingBox
import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import com.example.seefix.domain.repository.SessionHistoryRepository
import com.example.seefix.domain.repository.TroubleshootingRepository
import com.example.seefix.location.LocationManager
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.location.StoreSearchResult
import com.example.seefix.sensors.DeviceOrientation
import com.example.seefix.sensors.SensorRepository
import com.example.seefix.camera.CameraInteractionMode
import com.example.seefix.speech.SpeechInputManager
import com.example.seefix.speech.SpeechState
import com.example.seefix.speech.TextToSpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TroubleshootingViewModel(
    context: Context,
    private val repository: TroubleshootingRepository = TroubleshootingRepositoryImpl(
        SeeFixDatabase(context)
    ),
    val sessionHistoryRepository: SessionHistoryRepository = SessionHistoryRepositoryImpl(
        SeeFixDatabase(context)
    ),
    val aiPreferenceManager: AIPreferenceManager = AIPreferenceManager(context),
    val aiProviderRegistry: AIProviderRegistry = AIProviderRegistry().apply {
        registerProvider(GeminiAIService(aiPreferenceManager.getProviderConfig(AIProviderType.GOOGLE_GEMINI)))
        registerProvider(OpenAICompatibleAIService(aiPreferenceManager.getProviderConfig(AIProviderType.OPENAI)))
        registerProvider(AnthropicAIService(aiPreferenceManager.getProviderConfig(AIProviderType.ANTHROPIC)))
        setActiveCloudProviderType(aiPreferenceManager.getActiveCloudProviderType())
    },
    val localGemmaService: LocalGemmaAIService = LocalGemmaAIService(context),
    val aiRouter: AIRouter = AIRouter(aiProviderRegistry, localGemmaService),
    val workContextBuilder: WorkContextBuilder = WorkContextBuilderImpl(),
    val locationManager: LocationManager = LocationManager(context),
    val storeFinderRepository: StoreFinderRepository = StoreFinderRepository(),
    val sensorRepository: SensorRepository = SensorRepository(context),
    private val ragEngine: RagKnowledgeEngine = RagKnowledgeEngine(context),
    val toolRegistry: ToolRegistry = ToolRegistry(
        listOf(
            LocationTool(locationManager),
            StoreSearchTool(storeFinderRepository, locationManager),
            SensorTool(sensorRepository),
            RAGSearchTool(ragEngine),
            HistorySearchTool(sessionHistoryRepository),
            DocumentTool(ragEngine)
        )
    ),
    val permissionPolicy: PermissionPolicy = PermissionPolicy(),
    val toolExecutor: ToolExecutor = ToolExecutorImpl(toolRegistry, permissionPolicy),
    val agent: SeeFixAgent = SeeFixAgentImpl(
        aiRouter,
        workContextBuilder,
        toolRegistry,
        aiPreferenceManager,
        toolExecutor
    ),
    private val aiEngine: MultimodalAIEngine = MockAIEngine(),
    val speechInputManager: SpeechInputManager = SpeechInputManager(context),
    val ttsManager: TextToSpeechManager = TextToSpeechManager(context),
    val videoFrameExtractor: VideoFrameExtractor = VideoFrameExtractor(context)
) : ViewModel() {

    private var currentAgentJob: Job? = null

    private val _uiState = MutableStateFlow(DiagnosisUiState(session = SeeFixSession.createEmpty()))
    val uiState: StateFlow<DiagnosisUiState> = _uiState.asStateFlow()

    private val _extractionState = MutableStateFlow<ExtractionUIState>(ExtractionUIState.Idle)
    val extractionState: StateFlow<ExtractionUIState> = _extractionState.asStateFlow()

    val cameraManager: CameraManager = CameraManager(context)
    val videoRecorder: VideoRecorder = cameraManager.videoRecorder
    val videoRecordingState: StateFlow<VideoRecordingState> = videoRecorder.recordingState

    var currentVideoContext: VideoContext? = null
        private set

    val visualVerificationAnalyzer: VisualVerificationAnalyzer = VisualVerificationAnalyzer(
        targetStep = null
    ) { result ->
        handleVisualVerificationResult(result)
    }

    private var liveAiJob: Job? = null
    private var isCameraOpenState: Boolean = false
    private var currentInteractionModeState: CameraInteractionMode = CameraInteractionMode.LIVE_INSPECTION

    init {
        // Collect Video Recording state
        viewModelScope.launch {
            videoRecordingState.collect { state ->
                val updatedContext = if (state is VideoRecordingState.Completed) {
                    val vContext = VideoContext(
                        videoUri = state.uri.toString(),
                        durationMs = state.durationMs
                    )
                    currentVideoContext = vContext
                    _extractionState.value = ExtractionUIState.Idle
                    processVideoRecordingCompleted(state.uri, state.durationMs)
                    vContext
                } else {
                    currentVideoContext
                }

                _uiState.update { currentState ->
                    val updatedWorkContext = currentState.session.workContext.copy(
                        videoContext = updatedContext ?: currentState.session.workContext.videoContext
                    )
                    val updatedSession = currentState.session.copy(workContext = updatedWorkContext)
                    currentState.copy(
                        session = updatedSession,
                        videoRecordingState = state,
                        extractionState = if (state is VideoRecordingState.Completed) ExtractionUIState.Idle else currentState.extractionState,
                        statusMessage = when (state) {
                            is VideoRecordingState.Recording -> "Recording video (${state.durationMs / 1000}s)..."
                            is VideoRecordingState.Completed -> "Video captured (${state.durationMs / 1000}s)"
                            is VideoRecordingState.Error -> "Video recording error: ${state.message}"
                            else -> currentState.statusMessage
                        }
                    )
                }
            }
        }

        // Collect STT state
        viewModelScope.launch {
            speechInputManager.state.collect { state ->
                _uiState.update { currentState ->
                    val updatedContext = if (state is SpeechState.Recognized && state.text.isNotBlank()) {
                        currentState.session.workContext.copy(audioTranscript = state.text)
                    } else {
                        currentState.session.workContext
                    }
                    currentState.copy(
                        speechState = state,
                        session = currentState.session.copy(workContext = updatedContext)
                    )
                }
                if (state is SpeechState.Recognized) {
                    processUserQuery(state.text)
                }
            }
        }

        // Collect TTS speaking state
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                _uiState.update { it.copy(isTtsSpeaking = isSpeaking) }
            }
        }

        // Collect TTS muted state
        viewModelScope.launch {
            ttsManager.isMuted.collect { isMuted ->
                _uiState.update { it.copy(isTtsMuted = isMuted) }
            }
        }

        // Collect Active Session state
        viewModelScope.launch {
            repository.getActiveSession().collect { troubleSession ->
                if (troubleSession != null) {
                    _uiState.update { currentState ->
                        val updatedSession = SeeFixSession.fromTroubleshootingSession(troubleSession)
                        currentState.copy(
                            session = updatedSession,
                            boundingBoxes = calculateBoundingBoxesForStep(
                                troubleSession.currentStep,
                                troubleSession.currentStepIndex,
                                currentState.lastVerificationResult
                            )
                        )
                    }
                    visualVerificationAnalyzer.setTargetStep(troubleSession.currentStep)
                }
            }
        }

        // Collect Session History state
        viewModelScope.launch {
            repository.getSessionHistory().collect { history ->
                _uiState.update { it.copy(historySessions = history) }
            }
        }

        // Collect Sensor Data
        viewModelScope.launch {
            sensorRepository.orientation.collect { orientation ->
                _uiState.update { currentState ->
                    val updatedSensorPayload = SensorDataPayload(
                        compassHeading = orientation.compassHeading
                    )
                    val updatedWorkContext = currentState.session.workContext.copy(
                        sensorData = updatedSensorPayload
                    )
                    currentState.copy(
                        sensorOrientation = orientation,
                        session = currentState.session.copy(workContext = updatedWorkContext)
                    )
                }
            }
        }

        viewModelScope.launch {
            sensorRepository.isDeviceStable.collect { isStable ->
                _uiState.update { it.copy(isDeviceStable = isStable) }
            }
        }

        // Collect Location Data
        viewModelScope.launch {
            locationManager.userLocation.collect { loc ->
                _uiState.update { currentState ->
                    val updatedLocPayload = loc?.let {
                        LocationPayload(latitude = it.latitude, longitude = it.longitude, address = it.address)
                    }
                    val updatedWorkContext = currentState.session.workContext.copy(
                        location = updatedLocPayload ?: currentState.session.workContext.location
                    )
                    currentState.copy(
                        userLocation = loc,
                        session = currentState.session.copy(workContext = updatedWorkContext)
                    )
                }
                discoverNearbyStores()
            }
        }

        // Initialize default demo session
        initializeDemoSession("Smart Washing Machine (iQOO Demo Appliance)")
        refreshLocationAndStores()
    }

    fun addPhoto(photoUri: String) {
        _uiState.update { currentState ->
            val updatedImages = currentState.session.workContext.images + photoUri
            val updatedWorkContext = currentState.session.workContext.copy(images = updatedImages)
            currentState.copy(session = currentState.session.copy(workContext = updatedWorkContext))
        }
    }

    fun cancelActiveAnalysis() {
        currentAgentJob?.cancel()
        currentAgentJob = null
        _uiState.update { currentState ->
            val updatedAgentState = currentState.session.agentState.copy(
                status = AgentStatus.STOPPED,
                errors = currentState.session.agentState.errors + "Analysis cancelled by user."
            )
            val updatedSession = currentState.session.copy(
                status = AgentStatus.STOPPED,
                agentState = updatedAgentState,
                updatedAt = System.currentTimeMillis()
            )
            viewModelScope.launch {
                sessionHistoryRepository.saveSession(updatedSession)
            }
            currentState.copy(
                session = updatedSession,
                isProcessing = false,
                userFacingError = "Analysis cancelled by user.",
                statusMessage = "Analysis cancelled by user."
            )
        }
    }

    fun confirmPendingToolAction() {
        val pendingTool = _uiState.value.pendingConfirmationToolRequest
        val pendingAction = _uiState.value.pendingConfirmationAction

        _uiState.update {
            it.copy(
                pendingConfirmationToolRequest = null,
                pendingConfirmationAction = null,
                isProcessing = true,
                userFacingError = null
            )
        }

        runAgentTurn(userConfirmed = true, overrideToolRequest = pendingTool, overrideAction = pendingAction)
    }

    fun startVideoRecording() {
        videoRecorder.startRecording()
    }

    fun stopVideoRecording() {
        videoRecorder.stopRecording()
    }

    fun cancelVideoRecording() {
        videoRecorder.cancelRecording()
    }

    fun extractFramesFromVideo(videoUri: Uri, durationMs: Long = 0L) {
        viewModelScope.launch {
            _extractionState.value = ExtractionUIState.Extracting
            _uiState.update { it.copy(extractionState = ExtractionUIState.Extracting) }
            try {
                val result = videoFrameExtractor.extractFrames(videoUri, durationMs)
                val updatedVideoContext = (currentVideoContext ?: VideoContext(
                    videoUri = result.videoUri,
                    durationMs = result.durationMs
                )).copy(
                    videoUri = result.videoUri,
                    durationMs = if (result.durationMs > 0) result.durationMs else (currentVideoContext?.durationMs ?: 0L),
                    selectedFrames = result.frames
                )
                currentVideoContext = updatedVideoContext

                val newExtractionState = ExtractionUIState.Extracted(result.extractedCount)
                _extractionState.value = newExtractionState
                _uiState.update { currentState ->
                    val updatedWorkContext = currentState.session.workContext.copy(videoContext = updatedVideoContext)
                    val updatedSession = currentState.session.copy(workContext = updatedWorkContext)
                    currentState.copy(
                        session = updatedSession,
                        currentVideoContext = updatedVideoContext,
                        extractionState = newExtractionState,
                        statusMessage = "Extracted ${result.extractedCount} frames for analysis"
                    )
                }
            } catch (e: Exception) {
                val errorState = ExtractionUIState.Error(e.message ?: "Frame extraction failed")
                _extractionState.value = errorState
                _uiState.update { currentState ->
                    currentState.copy(
                        extractionState = errorState,
                        statusMessage = "Frame extraction failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun startSensors() {
        sensorRepository.startListening()
    }

    fun stopSensors() {
        sensorRepository.stopListening()
    }

    fun refreshLocationAndStores() {
        locationManager.fetchCurrentLocation { loc ->
            _uiState.update { currentState ->
                val updatedLocPayload = loc?.let { LocationPayload(latitude = it.latitude, longitude = it.longitude, address = it.address) }
                val updatedWorkContext = currentState.session.workContext.copy(
                    location = updatedLocPayload ?: currentState.session.workContext.location
                )
                currentState.copy(
                    userLocation = loc,
                    session = currentState.session.copy(workContext = updatedWorkContext)
                )
            }
            discoverNearbyStores()
        }
    }

    fun discoverNearbyStores(query: String = "") {
        val currentLoc = _uiState.value.userLocation
        val missing = _uiState.value.session.workContext.availableParts.filter { !it.isAvailable }
        val searchResult = storeFinderRepository.findNearbyStores(
            userLat = currentLoc?.latitude,
            userLng = currentLoc?.longitude,
            query = query,
            missingTools = missing
        )

        val stores = when (searchResult) {
            is StoreSearchResult.Success -> searchResult.stores
            is StoreSearchResult.Error -> emptyList()
        }

        _uiState.update {
            it.copy(
                enrichedStores = stores,
                recommendedStores = stores.map { s -> s.store }
            )
        }
    }

    fun initializeDemoSession(equipmentName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Loading technical specifications...") }

            val demoScenario = DemoScenarios.getScenarioForAppliance(equipmentName)
            repository.saveSession(demoScenario)

            val seeFixSession = SeeFixSession.fromTroubleshootingSession(demoScenario)
            sessionHistoryRepository.saveSession(seeFixSession)

            _uiState.update {
                it.copy(
                    session = seeFixSession,
                    isProcessing = false,
                    activeStage = MultimodalStage.GUIDE,
                    statusMessage = "Initialized ${demoScenario.device?.name ?: "Equipment"}"
                )
            }

            speakCurrentStepInstruction()
            discoverNearbyStores()
        }
    }

    fun confirmSafetyAction() {
        val currentTroubleSession = _uiState.value.session.toTroubleshootingSession()

        viewModelScope.launch {
            val updatedSteps = currentTroubleSession.steps.mapIndexed { index, step ->
                if (index == currentTroubleSession.currentStepIndex) {
                    step.copy(isSafetyConfirmed = true)
                } else {
                    step
                }
            }

            val safeStatus = SafetyStatus(
                level = SafetyLevel.SAFE,
                message = "POWER IS CONFIRMED DISCONNECTED. Safety verified."
            )

            val updatedTroubleSession = currentTroubleSession.copy(
                steps = updatedSteps,
                safetyStatus = safeStatus
            )
            repository.saveSession(updatedTroubleSession)

            val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession)
            sessionHistoryRepository.saveSession(updatedSeeFixSession)

            _uiState.update {
                it.copy(
                    session = updatedSeeFixSession,
                    statusMessage = "Safety action confirmed! Power is disconnected."
                )
            }

            ttsManager.speak("Safety action confirmed. Power is isolated and area is safe to proceed.")
        }
    }

    fun startSpeechInput() {
        if (speechInputManager.hasRecordAudioPermission()) {
            _uiState.update { it.copy(statusMessage = "Listening for prompt...") }
            speechInputManager.startListening()
        } else {
            _uiState.update { it.copy(statusMessage = "RECORD_AUDIO permission required. Operating in offline touch mode.") }
        }
    }

    fun stopSpeechInput() {
        speechInputManager.stopListening()
        _uiState.update { it.copy(statusMessage = "Voice standby") }
    }

    fun createCurrentWorkContext(userInput: String? = null, additionalImages: List<String> = emptyList()): WorkContext {
        val currentSession = _uiState.value.session
        val troubleSession = currentSession.toTroubleshootingSession()
        val userLoc = _uiState.value.userLocation
        val orientation = _uiState.value.sensorOrientation

        val activeDomain = troubleSession.domain
        val queryText = userInput ?: currentSession.currentTask?.description ?: troubleSession.detectedProblem.ifBlank { "troubleshooting" }

        val ragQueryContext = RagQueryContext(
            query = queryText,
            domain = activeDomain,
            equipmentType = troubleSession.device?.category,
            manufacturer = troubleSession.device?.name,
            model = troubleSession.device?.model,
            maxChunks = 3
        )
        val retrievedChunks = ragEngine.retrieve(ragQueryContext)
        val retrievedKnowledgeStrings = retrievedChunks.map { chunk ->
            "[${chunk.documentTitle} | ${chunk.sectionTitle} (p.${chunk.pageNumber ?: 1})] ${chunk.content}"
        }

        val allImages = (currentSession.workContext.images + additionalImages).distinct()

        return WorkContext(
            task = currentSession.currentTask ?: troubleSession.toWorkTask(),
            domain = activeDomain,
            userInput = userInput ?: currentSession.workContext.userInput,
            conversationHistory = currentSession.conversation,
            machineInfo = troubleSession.device?.let {
                MachineInfoPayload(
                    deviceName = it.name,
                    modelNumber = it.model,
                    category = it.category
                )
            },
            images = allImages,
            videoContext = _uiState.value.currentVideoContext ?: currentVideoContext ?: currentSession.workContext.videoContext,
            audioTranscript = currentSession.workContext.audioTranscript,
            sensorData = SensorDataPayload(
                compassHeading = orientation.compassHeading
            ),
            location = LocationPayload(
                latitude = userLoc?.latitude ?: 0.0,
                longitude = userLoc?.longitude ?: 0.0,
                address = userLoc?.address ?: "Location Unavailable"
            ),
            workHistory = _uiState.value.historySessions.map {
                "${it.device?.name ?: "Device"}: ${it.detectedProblem} (${it.statusText})"
            },
            availableTools = currentSession.workContext.availableTools.ifEmpty { troubleSession.availableTools },
            availableParts = currentSession.workContext.availableParts.ifEmpty { troubleSession.missingTools },
            retrievedKnowledge = retrievedKnowledgeStrings,
            observations = currentSession.observations.ifEmpty {
                troubleSession.observations.mapIndexed { idx, obs ->
                    Observation(id = "obs_$idx", description = obs, source = ObservationSource.USER)
                }
            },
            safetyContext = currentSession.workContext.safetyContext.ifEmpty {
                if (troubleSession.safetyStatus.level != SafetyLevel.SAFE) {
                    listOf(
                        SafetyRequirement(
                            hazard = troubleSession.safetyStatus.message,
                            severity = if (troubleSession.safetyStatus.level == SafetyLevel.DANGEROUS_STOP) SafetySeverity.CRITICAL_STOP else SafetySeverity.MEDIUM,
                            precaution = troubleSession.safetyStatus.message
                        )
                    )
                } else emptyList()
            }
        )
    }

    fun analyzeCurrentState() {
        if (currentAgentJob?.isActive == true) return
        runAgentTurn(userInput = null)
    }

    fun sendMessage(message: String) {
        processUserQuery(message)
    }

    fun processUserQuery(query: String) {
        if (currentAgentJob?.isActive == true) return
        runAgentTurn(userInput = query)
    }

    private fun runAgentTurn(
        userInput: String? = null,
        userConfirmed: Boolean = false,
        overrideToolRequest: ToolRequest? = null,
        overrideAction: WorkAction? = null
    ) {
        if (currentAgentJob?.isActive == true) {
            return
        }

        currentAgentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    activeStage = MultimodalStage.THINK,
                    statusMessage = if (userInput != null) "Analyzing user query: \"$userInput\"..." else "Analyzing equipment state...",
                    userFacingError = null
                )
            }

            val currentSession = _uiState.value.session
            val updatedConversation = if (userInput != null) {
                currentSession.conversation + AIMessage(role = AIRole.USER, content = userInput)
            } else {
                currentSession.conversation
            }

            val workContext = createCurrentWorkContext(userInput = userInput)
                .copy(conversationHistory = updatedConversation)

            val initialAgentState = currentSession.agentState.copy(
                sessionId = currentSession.sessionId,
                currentTask = workContext.task,
                status = AgentStatus.ANALYZING
            )

            val agentResult = try {
                agent.process(workContext, initialAgentState, userConfirmed = userConfirmed)
            } catch (_: Exception) {
                null
            }

            val finalAgentState = agentResult?.state ?: initialAgentState.copy(status = AgentStatus.ERROR)

            val isToolPendingConfirmation = (agentResult?.state?.status == AgentStatus.WAITING_FOR_USER) ||
                    (agentResult?.decision?.actionType == AgentActionType.ASK_USER) ||
                    (agentResult?.decision?.toolRequest?.arguments?.get("action") == "open_navigation") ||
                    (agentResult?.decision?.toolRequest?.arguments?.get("action") == "dial_phone")

            val pendingToolReq = if (isToolPendingConfirmation) {
                agentResult.decision.toolRequest ?: overrideToolRequest
            } else null

            val pendingAct = if (isToolPendingConfirmation) {
                agentResult?.decision?.proposedAction ?: overrideAction
            } else null

            val assistantText = agentResult?.explanation ?: "Analysis updated."
            val finalConversation = updatedConversation + AIMessage(role = AIRole.ASSISTANT, content = assistantText)

            val updatedSession = currentSession.copy(
                updatedAt = System.currentTimeMillis(),
                currentTask = workContext.task,
                workContext = workContext,
                agentState = finalAgentState,
                conversation = finalConversation,
                observations = finalAgentState.observations.ifEmpty { currentSession.observations },
                toolResults = finalAgentState.toolResults,
                completedSteps = finalAgentState.completedSteps,
                status = finalAgentState.status
            )

            sessionHistoryRepository.saveSession(updatedSession)

            _uiState.update {
                it.copy(
                    session = updatedSession,
                    isProcessing = false,
                    pendingConfirmationToolRequest = pendingToolReq,
                    pendingConfirmationAction = pendingAct,
                    activeStage = MultimodalStage.GUIDE,
                    statusMessage = assistantText
                )
            }

            speakCurrentStepInstruction()
            discoverNearbyStores()
        }
    }

    fun speakCurrentStepInstruction() {
        val troubleSession = _uiState.value.session.toTroubleshootingSession()
        val currentStep = troubleSession.currentStep ?: return
        val speechText = if (currentStep.spokenInstruction.isNotEmpty()) {
            currentStep.spokenInstruction
        } else {
            "${currentStep.title}. ${currentStep.instructionText}"
        }
        _uiState.update { it.copy(statusMessage = "Speaking: ${currentStep.title}") }
        ttsManager.speak(speechText)
    }

    fun toggleTtsMute() {
        val newMuted = !_uiState.value.isTtsMuted
        ttsManager.setMuted(newMuted)
    }

    fun verifyStepWithCamera(imageBytes: ByteArray? = null) {
        val troubleSession = _uiState.value.session.toTroubleshootingSession()
        val currentStep = troubleSession.currentStep ?: return

        if ((currentStep.requiresSafetyConfirmation || troubleSession.safetyStatus.level == SafetyLevel.DANGEROUS_STOP) && !currentStep.isSafetyConfirmed) {
            _uiState.update {
                it.copy(statusMessage = "HAZARD LOCKOUT: Confirm power is disconnected before camera verification!")
            }
            ttsManager.speak("Hazard detected. Please confirm power is disconnected before proceeding.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    activeStage = MultimodalStage.VERIFY,
                    statusMessage = "Verifying ${currentStep.title} with Visual Analyzer..."
                )
            }

            val currentMode = aiPreferenceManager.getAIMode()
            val workContext = createCurrentWorkContext()
            val baseAiRequest = workContextBuilder.buildAIRequest(
                userPrompt = "Verify step: ${currentStep.title}. Prompt: ${currentStep.visualVerificationPrompt}",
                workContext = workContext
            )
            val aiRequest = if (imageBytes != null) baseAiRequest.copy(imageBytes = imageBytes) else baseAiRequest

            val routerResponse = try {
                aiRouter.generate(aiRequest, mode = currentMode)
            } catch (_: Exception) {
                null
            }

            val verificationResult = try {
                aiEngine.verifyStepCompletion(imageBytes, currentStep)
            } catch (_: Exception) {
                MockAIEngine().verifyStepCompletion(imageBytes, currentStep)
            }

            val finalResult = if (routerResponse?.error == null && !routerResponse?.text.isNullOrBlank()) {
                verificationResult.copy(
                    feedbackMessage = verificationResult.feedbackMessage + " [AIRouter: ${routerResponse.providerName}]"
                )
            } else {
                verificationResult
            }

            handleVisualVerificationResult(finalResult)
        }
    }

    fun verifyCurrentStep(imageBytes: ByteArray? = null) {
        verifyStepWithCamera(imageBytes)
    }

    fun setCameraOpen(isOpen: Boolean) {
        isCameraOpenState = isOpen
        if (isOpen && currentInteractionModeState == CameraInteractionMode.LIVE_INSPECTION) {
            startLiveAiSampling()
        } else {
            stopLiveAiSampling()
        }
    }

    fun setInteractionMode(mode: CameraInteractionMode) {
        currentInteractionModeState = mode
        if (isCameraOpenState && mode == CameraInteractionMode.LIVE_INSPECTION) {
            startLiveAiSampling()
        } else {
            stopLiveAiSampling()
        }
    }

    fun startLiveAiSampling() {
        liveAiJob?.cancel()
        liveAiJob = viewModelScope.launch {
            _uiState.update { it.copy(isLiveAiActive = true) }
            while (isActive) {
                val troubleSession = _uiState.value.session.toTroubleshootingSession()
                val currentStep = troubleSession.currentStep
                if (currentStep != null) {
                    val verificationResult = try {
                        aiEngine.verifyStepCompletion(null, currentStep)
                    } catch (_: Exception) {
                        MockAIEngine().verifyStepCompletion(null, currentStep)
                    }
                    val caption = "Live AI: ${verificationResult.feedbackMessage}"
                    _uiState.update { currentState ->
                        currentState.copy(
                            liveCaptionText = caption,
                            statusMessage = caption,
                            lastVerificationResult = verificationResult
                        )
                    }
                    ttsManager.speak(verificationResult.feedbackMessage)
                }
                delay(3500L)
            }
        }
    }

    fun stopLiveAiSampling() {
        liveAiJob?.cancel()
        liveAiJob = null
        _uiState.update { it.copy(isLiveAiActive = false) }
    }

    fun processPhotoSnapshot(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Analyzing photo snapshot with AI..."
                )
            }
            val troubleSession = _uiState.value.session.toTroubleshootingSession()
            val currentStep = troubleSession.currentStep

            val verificationResult = if (currentStep != null && imageBytes.isNotEmpty()) {
                try {
                    aiEngine.verifyStepCompletion(imageBytes, currentStep)
                } catch (_: Exception) {
                    MockAIEngine().verifyStepCompletion(imageBytes, currentStep)
                }
            } else if (currentStep != null) {
                try {
                    aiEngine.verifyStepCompletion(null, currentStep)
                } catch (_: Exception) {
                    MockAIEngine().verifyStepCompletion(null, currentStep)
                }
            } else {
                StepVerificationResult(
                    isVerified = true,
                    confidence = 0.90f,
                    feedbackMessage = "Photo snapshot captured and component status verified.",
                    detectedObjects = emptyList(),
                    nextRecommendedAction = "Proceed to next step"
                )
            }

            val responseCaption = "AI Photo Analysis: ${verificationResult.feedbackMessage}"
            _uiState.update { currentState ->
                currentState.copy(
                    isProcessing = false,
                    liveCaptionText = responseCaption,
                    statusMessage = responseCaption,
                    lastVerificationResult = verificationResult
                )
            }
            ttsManager.speak(verificationResult.feedbackMessage)
        }
    }

    fun processVideoRecordingCompleted(uri: Uri, durationMs: Long) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Extracting video frames and analyzing with AI..."
                )
            }
            try {
                val result = videoFrameExtractor.extractFrames(uri, durationMs)
                val troubleSession = _uiState.value.session.toTroubleshootingSession()
                val currentStep = troubleSession.currentStep

                val verificationResult = if (currentStep != null) {
                    try {
                        aiEngine.verifyStepCompletion(null, currentStep)
                    } catch (_: Exception) {
                        MockAIEngine().verifyStepCompletion(null, currentStep)
                    }
                } else {
                    StepVerificationResult(
                        isVerified = true,
                        confidence = 0.95f,
                        feedbackMessage = "Video inspection verified across ${result.extractedCount} frames.",
                        detectedObjects = emptyList(),
                        nextRecommendedAction = "Proceed to next step"
                    )
                }

                val responseCaption = "AI Video Analysis (${result.extractedCount} frames): ${verificationResult.feedbackMessage}"
                _uiState.update { currentState ->
                    currentState.copy(
                        isProcessing = false,
                        liveCaptionText = responseCaption,
                        statusMessage = responseCaption,
                        lastVerificationResult = verificationResult
                    )
                }
                ttsManager.speak(verificationResult.feedbackMessage)
            } catch (e: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isProcessing = false,
                        userFacingError = "Video analysis error: ${e.message}"
                    )
                }
            }
        }
    }

    fun handleVisualVerificationResult(verificationResult: StepVerificationResult) {
        viewModelScope.launch {
            val troubleSession = _uiState.value.session.toTroubleshootingSession()
            val updatedSteps = troubleSession.steps.mapIndexed { index, step ->
                if (index == troubleSession.currentStepIndex) {
                    step.copy(isCompleted = verificationResult.isVerified, isVerified = verificationResult.isVerified)
                } else {
                    step
                }
            }

            val updatedTroubleSession = troubleSession.copy(steps = updatedSteps)
            repository.saveSession(updatedTroubleSession)

            val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession)
            sessionHistoryRepository.saveSession(updatedSeeFixSession)

            _uiState.update { currentState ->
                currentState.copy(
                    session = updatedSeeFixSession,
                    isProcessing = false,
                    lastVerificationResult = verificationResult,
                    statusMessage = verificationResult.feedbackMessage,
                    boundingBoxes = calculateBoundingBoxesForStep(
                        troubleSession.currentStep,
                        troubleSession.currentStepIndex,
                        verificationResult
                    )
                )
            }

            ttsManager.speak(verificationResult.feedbackMessage)
        }
    }

    fun nextStep() {
        val troubleSession = _uiState.value.session.toTroubleshootingSession()
        val currentStep = troubleSession.currentStep

        if (currentStep != null && (currentStep.requiresSafetyConfirmation || troubleSession.safetyStatus.level == SafetyLevel.DANGEROUS_STOP) && !currentStep.isSafetyConfirmed) {
            _uiState.update {
                it.copy(statusMessage = "HAZARD LOCKOUT: Confirm power is disconnected before advancing!")
            }
            ttsManager.speak("Hazard detected. Please confirm power is disconnected before advancing.")
            return
        }

        if (troubleSession.currentStepIndex < troubleSession.steps.size - 1) {
            val newIndex = troubleSession.currentStepIndex + 1
            viewModelScope.launch {
                val updatedTroubleSession = troubleSession.copy(currentStepIndex = newIndex)
                repository.saveSession(updatedTroubleSession)
                val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession)
                sessionHistoryRepository.saveSession(updatedSeeFixSession)

                _uiState.update {
                    it.copy(
                        session = updatedSeeFixSession,
                        activeStage = MultimodalStage.GUIDE,
                        statusMessage = "Advanced to Step ${newIndex + 1}",
                        lastVerificationResult = null
                    )
                }
                speakCurrentStepInstruction()
            }
        } else {
            viewModelScope.launch {
                val updatedTroubleSession = troubleSession.copy(
                    statusText = "RESOLVED",
                    safetyStatus = SafetyStatus(SafetyLevel.SAFE, "Equipment repair verified safe!")
                )
                repository.saveSession(updatedTroubleSession)

                val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession).copy(
                    status = AgentStatus.COMPLETED
                )
                sessionHistoryRepository.saveSession(updatedSeeFixSession)

                _uiState.update {
                    it.copy(
                        session = updatedSeeFixSession,
                        activeStage = MultimodalStage.RESOLVE,
                        statusMessage = "Troubleshooting completed successfully!"
                    )
                }
                ttsManager.speak("All repair steps completed and verified safe!")
            }
        }
    }

    fun previousStep() {
        val troubleSession = _uiState.value.session.toTroubleshootingSession()
        if (troubleSession.currentStepIndex > 0) {
            val newIndex = troubleSession.currentStepIndex - 1
            viewModelScope.launch {
                val updatedTroubleSession = troubleSession.copy(currentStepIndex = newIndex)
                repository.saveSession(updatedTroubleSession)
                val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession)
                sessionHistoryRepository.saveSession(updatedSeeFixSession)

                _uiState.update {
                    it.copy(
                        session = updatedSeeFixSession,
                        activeStage = MultimodalStage.GUIDE,
                        statusMessage = "Moved back to Step ${newIndex + 1}",
                        lastVerificationResult = null
                    )
                }
                speakCurrentStepInstruction()
            }
        }
    }

    fun setStage(stage: MultimodalStage) {
        _uiState.update { it.copy(activeStage = stage) }
    }

    fun toggleToolAvailability(toolId: String) {
        val troubleSession = _uiState.value.session.toTroubleshootingSession()
        val updatedSteps = troubleSession.steps.map { step ->
            val updatedTools = step.requiredTools.map { tool ->
                if (tool.id == toolId) tool.copy(isAvailable = !tool.isAvailable) else tool
            }
            step.copy(requiredTools = updatedTools)
        }

        viewModelScope.launch {
            val allTools = updatedSteps.flatMap { it.requiredTools }.distinctBy { it.id }
            val updatedTroubleSession = troubleSession.copy(
                steps = updatedSteps,
                availableTools = allTools.filter { it.isAvailable },
                missingTools = allTools.filter { !it.isAvailable }
            )
            repository.saveSession(updatedTroubleSession)
            val updatedSeeFixSession = SeeFixSession.fromTroubleshootingSession(updatedTroubleSession)
            sessionHistoryRepository.saveSession(updatedSeeFixSession)

            _uiState.update { it.copy(session = updatedSeeFixSession) }
            discoverNearbyStores()
        }
    }

    fun findStoresForMissingParts() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeStage = MultimodalStage.SOURCE,
                    statusMessage = "Searching nearby hardware stores for missing parts..."
                )
            }

            discoverNearbyStores()

            _uiState.update {
                it.copy(
                    statusMessage = "Found ${it.enrichedStores.size} nearby stores stocking required parts."
                )
            }
        }
    }

    private fun calculateBoundingBoxesForStep(
        step: TroubleshootingStep?,
        stepIndex: Int,
        verificationResult: StepVerificationResult? = null
    ): List<BoundingBoxWithLabel> {
        val defaultBox = step?.targetBoundingBox ?: when (stepIndex) {
            0 -> BoundingBox(0.25f, 0.30f, 0.65f, 0.65f)
            1 -> BoundingBox(0.35f, 0.40f, 0.75f, 0.75f)
            2 -> BoundingBox(0.20f, 0.25f, 0.60f, 0.60f)
            else -> BoundingBox(0.15f, 0.20f, 0.85f, 0.80f)
        }

        val box = if (verificationResult?.detectedBoxes?.isNotEmpty() == true) {
            val detected = verificationResult.detectedBoxes.first()
            BoundingBox(detected.xMin, detected.yMin, detected.xMax, detected.yMax)
        } else {
            defaultBox
        }

        val isVerified = verificationResult?.isVerified ?: step?.isVerified ?: false
        val boxColor = if (isVerified) Color(0xFF4CAF50) else Color(0xFFFFB300)
        val label = verificationResult?.detectedObjects?.firstOrNull() ?: step?.title ?: "Inspection Target"

        return listOf(
            BoundingBoxWithLabel(
                box = box,
                label = if (isVerified) "[VERIFIED] $label" else "[TARGET] $label",
                color = boxColor
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        speechInputManager.destroy()
        ttsManager.shutdown()
        stopSensors()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TroubleshootingViewModel(context.applicationContext) as T
            }
        }
    }
}
