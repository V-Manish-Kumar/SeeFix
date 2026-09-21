package com.example.seefix.ui.diagnosis

import com.example.seefix.ai.LocationInfo
import com.example.seefix.ai.StepVerificationResult
import com.example.seefix.ai.agent.ToolRequest
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.camera.BoundingBoxWithLabel
import com.example.seefix.camera.ExtractionUIState
import com.example.seefix.camera.VideoRecordingState
import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.location.EnrichedStoreLocation
import com.example.seefix.sensors.DeviceOrientation
import com.example.seefix.speech.SpeechState

data class DiagnosisUiState(
    val session: SeeFixSession,
    val isProcessing: Boolean = false,
    val pendingConfirmationToolRequest: ToolRequest? = null,
    val pendingConfirmationAction: WorkAction? = null,
    val userFacingError: String? = null,
    val videoRecordingState: VideoRecordingState = VideoRecordingState.Idle,
    val extractionState: ExtractionUIState = ExtractionUIState.Idle,
    val activeTab: Int = 0,
    val speechState: SpeechState = SpeechState.Idle,
    val isTtsMuted: Boolean = false,
    val isTtsSpeaking: Boolean = false,
    val statusMessage: String = "Ready for voice prompt or visual scan",
    val activeStage: MultimodalStage = MultimodalStage.GUIDE,
    val boundingBoxes: List<BoundingBoxWithLabel> = emptyList(),
    val recommendedStores: List<StoreLocation> = emptyList(),
    val enrichedStores: List<EnrichedStoreLocation> = emptyList(),
    val userLocation: LocationInfo? = null,
    val sensorOrientation: DeviceOrientation = DeviceOrientation(),
    val isDeviceStable: Boolean = true,
    val lastVerificationResult: StepVerificationResult? = null,
    val historySessions: List<TroubleshootingSession> = emptyList(),
    val currentVideoContext: VideoContext? = null,
    val liveCaptionText: String = "Live AI: Point camera at main AC power plug before disconnecting",
    val isLiveAiActive: Boolean = false
)
