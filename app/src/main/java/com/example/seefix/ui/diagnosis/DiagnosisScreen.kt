package com.example.seefix.ui.diagnosis

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.seefix.ai.agent.AgentStatus
import com.example.seefix.ai.agent.ToolRequest
import com.example.seefix.ai.agent.ToolResult
import com.example.seefix.camera.CameraAspectRatio
import com.example.seefix.camera.CameraInteractionMode
import com.example.seefix.camera.CameraPreview
import com.example.seefix.camera.CameraViewportMode
import com.example.seefix.camera.VideoRecordingState
import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.speech.SpeechState
import com.example.seefix.ui.theme.SeeFixTheme
import java.util.Locale
import kotlin.math.roundToInt

enum class MultimodalStage(val label: String) {
    SEE("SEE"),
    THINK("THINK"),
    GUIDE("GUIDE"),
    VERIFY("VERIFY"),
    SOURCE("SOURCE"),
    RESOLVE("RESOLVE")
}

/**
 * Modern Interactive Camera Viewport & Diagnosis Screen:
 * - Solid Background on Diagnose Tab: Dark theme background (Color(0xFF0F172A) / MaterialTheme.colorScheme.background).
 * - No CameraPreview running in background when Diagnose page is shown. High-tech "Camera Inspection Card" acts as entry point.
 * - Fullscreen Camera Viewport opens ONLY when user taps entry point or drags down.
 * - Fullscreen Camera Viewport contains live CameraPreview, AI object detection bounding boxes, reticle overlay, torch toggle (⚡), shutter photo button, video record button, and Close (✕) button.
 * - Tapping Close (✕) or swiping up closes camera completely, stopping camera lifecycle binding.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosisScreen(
    modifier: Modifier = Modifier,
    equipmentName: String = "Smart Washing Machine (iQOO Demo Appliance)",
    initialSafetyMessage: String = "SAFE: Low Voltage Demo Appliance",
    viewModel: TroubleshootingViewModel? = null,
    onNavigateToStores: () -> Unit = {}
) {
    val context = LocalContext.current
    val effectiveViewModel: TroubleshootingViewModel = viewModel ?: viewModel(
        factory = TroubleshootingViewModel.provideFactory(context.applicationContext)
    )

    val uiState by effectiveViewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        effectiveViewModel.startSensors()
        onDispose {
            effectiveViewModel.stopSensors()
        }
    }

    LaunchedEffect(equipmentName) {
        val seeFixSession = uiState.session
        if (seeFixSession.currentTask == null || seeFixSession.workContext.machineInfo?.deviceName != equipmentName) {
            effectiveViewModel.initializeDemoSession(equipmentName)
        }
    }

    var isCameraOpen by remember { mutableStateOf(false) }
    var interactionMode by remember { mutableStateOf(CameraInteractionMode.LIVE_INSPECTION) }
    var aspectRatio by remember { mutableStateOf(CameraAspectRatio.RATIO_FILL) }
    var showGrid by remember { mutableStateOf(false) }

    LaunchedEffect(isCameraOpen, interactionMode) {
        effectiveViewModel.setCameraOpen(isCameraOpen)
        effectiveViewModel.setInteractionMode(interactionMode)
    }

    val seeFixSession = uiState.session
    val troubleSession = seeFixSession.toTroubleshootingSession()
    val currentStep = troubleSession.currentStep
    val totalSteps = troubleSession.steps.size.coerceAtLeast(1)
    val currentStepIndex = troubleSession.currentStepIndex
    val safetyStatus = troubleSession.safetyStatus

    val isListening = uiState.speechState is SpeechState.Listening
    val isSafetyLocked = (currentStep?.requiresSafetyConfirmation == true || safetyStatus.level == SafetyLevel.DANGEROUS_STOP) && currentStep?.isSafetyConfirmed != true

    val hasCriticalStop = seeFixSession.workContext.safetyContext.any { it.severity == SafetySeverity.CRITICAL_STOP } ||
            seeFixSession.status == AgentStatus.STOPPED

    val isRecordingVideo = uiState.videoRecordingState is VideoRecordingState.Recording

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // --- SOLID DARK BACKGROUND DIAGNOSE PAGE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // High-Tech Camera Inspection Entry Point Card
            CameraInspectionCard(
                onOpenFullscreenCamera = { isCameraOpen = true }
            )

            // Task / Domain Header Card
            TaskDomainHeaderCard(
                equipmentName = troubleSession.device?.name ?: equipmentName,
                deviceModel = troubleSession.device?.model ?: "WM-9000X",
                compassHeading = uiState.sensorOrientation.compassHeading,
                cardinalDirection = uiState.sensorOrientation.cardinalDirection,
                isDeviceStable = uiState.isDeviceStable
            )

            // Critical Safety Banner / Safety Alert Banner
            if (hasCriticalStop) {
                CriticalSafetyBanner(
                    hazardMessage = seeFixSession.workContext.safetyContext.firstOrNull { it.severity == SafetySeverity.CRITICAL_STOP }?.hazard
                        ?: "Critical Safety Hazard Detected or Agent Operation Stopped.",
                    precaution = seeFixSession.workContext.safetyContext.firstOrNull { it.severity == SafetySeverity.CRITICAL_STOP }?.precaution
                        ?: "Immediately disconnect power grid and isolate equipment before proceeding.",
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (safetyStatus.level != SafetyLevel.SAFE) {
                SafetyAlertBanner(
                    safetyStatus = safetyStatus,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Agent Status Banner
            AgentStatusBanner(
                status = seeFixSession.status,
                isProcessing = uiState.isProcessing,
                onCancel = { effectiveViewModel.cancelActiveAnalysis() }
            )

            // Tool Execution Progress Chips
            ToolExecutionProgressChips(
                toolResults = seeFixSession.toolResults,
                pendingToolRequest = uiState.pendingConfirmationToolRequest
            )

            // Explicit User Confirmation Card for consequential actions
            if (uiState.pendingConfirmationToolRequest != null || seeFixSession.status == AgentStatus.WAITING_FOR_USER) {
                UserConfirmationCard(
                    pendingToolRequest = uiState.pendingConfirmationToolRequest,
                    pendingAction = uiState.pendingConfirmationAction,
                    onConfirm = { effectiveViewModel.confirmPendingToolAction() },
                    onCancel = { effectiveViewModel.cancelActiveAnalysis() }
                )
            }

            // Completed Analysis Summary Card
            if (seeFixSession.status == AgentStatus.COMPLETED) {
                CompletedAnalysisSummaryCard(session = seeFixSession)
            }

            // Multimodal Stage Tracker Toolbar
            MultimodalStageTracker(
                activeStage = uiState.activeStage,
                onStageSelected = { stage -> effectiveViewModel.setStage(stage) }
            )

            // Hands-Free Voice Status Bar
            VoiceStatusBar(
                isListening = isListening,
                isRecordingVideo = isRecordingVideo,
                statusText = uiState.statusMessage,
                onMicToggle = {
                    if (isListening) {
                        effectiveViewModel.stopSpeechInput()
                    } else {
                        effectiveViewModel.startSpeechInput()
                    }
                },
                onVideoToggle = {
                    if (isRecordingVideo) {
                        effectiveViewModel.stopVideoRecording()
                    } else {
                        effectiveViewModel.startVideoRecording()
                    }
                },
                onSpeakStep = {
                    effectiveViewModel.speakCurrentStepInstruction()
                }
            )

            // Active Step Guidance Card (with required tools checklist)
            FloatingStepGuidanceCard(
                currentStepIndex = currentStepIndex,
                totalSteps = totalSteps,
                deviceModel = troubleSession.device?.model ?: "WM-9000X",
                stepTitle = currentStep?.title ?: "Locate Target Hardware Component",
                stepInstruction = currentStep?.instructionText ?: "Position live camera reticle over component to inspect.",
                toolsList = currentStep?.requiredTools ?: troubleSession.availableTools,
                missingTools = troubleSession.missingTools,
                isSafetyLocked = isSafetyLocked,
                isProcessing = uiState.isProcessing,
                onToggleTool = { toolId -> effectiveViewModel.toggleToolAvailability(toolId) },
                onFindPart = {
                    effectiveViewModel.findStoresForMissingParts()
                    onNavigateToStores()
                },
                onVerifyStep = { effectiveViewModel.verifyCurrentStep() },
                onNextStep = { effectiveViewModel.nextStep() }
            )

            // Store & RAG Shortcuts Row
            StoreRagShortcutsRow(
                onFindStores = {
                    effectiveViewModel.findStoresForMissingParts()
                    onNavigateToStores()
                },
                onSearchRagManuals = {
                    effectiveViewModel.processUserQuery("Retrieve technical manual specs for $equipmentName")
                }
            )
        }

        // Fullscreen Camera Viewport Overlay Dialog
        if (isCameraOpen) {
            Dialog(
                onDismissRequest = { isCameraOpen = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                var accumulatedDragY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { accumulatedDragY = 0f },
                                onDragEnd = { accumulatedDragY = 0f },
                                onDragCancel = { accumulatedDragY = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDragY += dragAmount
                                    if (accumulatedDragY < -25f) { // Swipe up to close camera
                                        isCameraOpen = false
                                        accumulatedDragY = 0f
                                    }
                                }
                            )
                        }
                ) {
                    // 100% Pure Clean CameraPreview with Minimalist Top Bar and Live Captions HUD
                    CameraPreview(
                        analyzer = effectiveViewModel.visualVerificationAnalyzer,
                        cameraManager = effectiveViewModel.cameraManager,
                        recordingState = uiState.videoRecordingState,
                        interactionMode = interactionMode,
                        aspectRatio = aspectRatio,
                        isProcessing = uiState.isProcessing,
                        liveCaptionText = uiState.liveCaptionText,
                        isLiveAiActive = uiState.isLiveAiActive,
                        isTtsSpeaking = uiState.isTtsSpeaking,
                        onInteractionModeChange = { newMode ->
                            interactionMode = newMode
                            effectiveViewModel.setInteractionMode(newMode)
                        },
                        onCloseCamera = {
                            isCameraOpen = false
                            effectiveViewModel.setCameraOpen(false)
                        },
                        onTakePhoto = {
                            effectiveViewModel.cameraManager.capturePhotoBytes(
                                onPhotoCaptured = { bytes ->
                                    effectiveViewModel.processPhotoSnapshot(bytes)
                                },
                                onError = {
                                    effectiveViewModel.processPhotoSnapshot(ByteArray(0))
                                }
                            )
                        },
                        onToggleVideoRecord = {
                            if (isRecordingVideo) {
                                effectiveViewModel.stopVideoRecording()
                            } else {
                                effectiveViewModel.startVideoRecording()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}



/**
 * High-Tech Camera Inspection Card on Diagnose Tab (Entry Point for Fullscreen Camera).
 */
@Composable
fun CameraInspectionCard(
    onOpenFullscreenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { accumulatedDragY = 0f },
                    onDragEnd = { accumulatedDragY = 0f },
                    onDragCancel = { accumulatedDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragY += dragAmount
                        if (accumulatedDragY > 25f) { // Drag down to launch full-screen camera
                            onOpenFullscreenCamera()
                            accumulatedDragY = 0f
                        }
                    }
                )
            }
            .clickable { onOpenFullscreenCamera() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drag handle affordance bar
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.35f)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // High-tech glowing camera icon badge
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Camera Inspection",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Open Fullscreen Camera Inspection",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Text(
                        text = "Tap or drag down to launch full-screen camera & AI object detection",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        lineHeight = 16.sp
                    )
                }
            }

            Button(
                onClick = onOpenFullscreenCamera,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF0F172A)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Launch Fullscreen Camera Inspection ⚡",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Task & Domain Header Card displayed at top of content sheet.
 */
@Composable
fun TaskDomainHeaderCard(
    equipmentName: String,
    deviceModel: String,
    compassHeading: Float,
    cardinalDirection: String,
    isDeviceStable: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121824)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "FIELD-WORK DIAGNOSIS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Model: $deviceModel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${compassHeading.roundToInt()}° $cardinalDirection",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isDeviceStable) "• STABLE" else "• MOTION",
                        color = if (isDeviceStable) Color(0xFF4CAF50) else Color(0xFFFFB300),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = equipmentName,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

/**
 * Store & RAG Manual Search Shortcuts Row.
 */
@Composable
fun StoreRagShortcutsRow(
    onFindStores: () -> Unit,
    onSearchRagManuals: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onFindStores,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(0xFF00E5FF)
            ),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Rounded.ShoppingBag,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Find Nearby Parts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onSearchRagManuals,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("RAG Specs & Manual", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Floating Translucent Step Guidance Card.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingStepGuidanceCard(
    currentStepIndex: Int,
    totalSteps: Int,
    deviceModel: String,
    stepTitle: String,
    stepInstruction: String,
    toolsList: List<ToolItem>,
    missingTools: List<ToolItem>,
    isSafetyLocked: Boolean,
    isProcessing: Boolean,
    onToggleTool: (String) -> Unit,
    onFindPart: () -> Unit,
    onVerifyStep: () -> Unit,
    onNextStep: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.82f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Step Counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "STEP ${currentStepIndex + 1} OF $totalSteps",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = "Model: $deviceModel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E5FF)
                )
            }

            // Step Instruction Text
            Text(
                text = stepTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = stepInstruction,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 17.sp
            )

            // Required Tools Checklist with Simulated Stock Labeling
            if (toolsList.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Required Tools & Safety Gear:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        SimulatedStockChip(inventorySource = InventoryDataSource.SIMULATED_DEMO)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        toolsList.forEach { tool ->
                            ToolCheckChip(
                                tool = tool,
                                onToggle = { onToggleTool(tool.id) }
                            )
                        }
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (missingTools.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onFindPart,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ShoppingBag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Find Part",
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Button(
                    onClick = onVerifyStep,
                    enabled = !isSafetyLocked && !isProcessing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        imageVector = if (isSafetyLocked) Icons.Rounded.Lock else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSafetyLocked) "Locked" else "Verify Step",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onNextStep,
                    enabled = !isSafetyLocked && !isProcessing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = if (currentStepIndex < totalSteps - 1) "Next Step" else "Finish",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentStatusBanner(
    status: AgentStatus,
    isProcessing: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, contentColor, labelText, icon) = when (status) {
        AgentStatus.ANALYZING -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Agent Analyzing...",
            Icons.Rounded.AutoAwesome
        )
        AgentStatus.WAITING_FOR_TOOL -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Executing Tool Request...",
            Icons.Rounded.AutoAwesome
        )
        AgentStatus.WAITING_FOR_USER -> Quadruple(
            Color(0xFF3E2723),
            Color(0xFFFFB74D),
            "Waiting for User Confirmation",
            Icons.Rounded.Warning
        )
        AgentStatus.ACTION_PROPOSED -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Action Proposed",
            Icons.Rounded.Info
        )
        AgentStatus.COMPLETED -> Quadruple(
            Color(0xFF1E3A20),
            Color(0xFF81C784),
            "Analysis Completed",
            Icons.Rounded.CheckCircle
        )
        AgentStatus.STOPPED -> Quadruple(
            Color(0xFF3B1212),
            Color(0xFFE57373),
            "Agent Stopped",
            Icons.Rounded.Stop
        )
        AgentStatus.ERROR -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Analysis Error",
            Icons.Rounded.Warning
        )
        AgentStatus.IDLE -> Quadruple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Agent Idle",
            Icons.Rounded.Info
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isProcessing || status == AgentStatus.ANALYZING || status == AgentStatus.WAITING_FOR_TOOL) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = labelText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            if (isProcessing || status == AgentStatus.ANALYZING || status == AgentStatus.WAITING_FOR_TOOL) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = "Cancel Analysis",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolExecutionProgressChips(
    toolResults: List<ToolResult>,
    pendingToolRequest: ToolRequest?,
    modifier: Modifier = Modifier
) {
    val chips = mutableListOf<String>()

    if (pendingToolRequest != null) {
        val label = when (pendingToolRequest.toolName) {
            "location_tool" -> "📍 Getting location..."
            "sensor_tool" -> "📡 Reading sensors..."
            "store_search_tool" -> "🏪 Searching nearby stores..."
            "rag_search_tool" -> "📚 Searching manuals..."
            "history_search_tool" -> "📜 Searching history..."
            "document_tool" -> "📄 Reading document..."
            else -> "🔧 Executing ${pendingToolRequest.toolName}..."
        }
        chips.add(label)
    }

    toolResults.takeLast(3).forEach { res ->
        val label = when (res.toolName) {
            "location_tool" -> "📍 Location Acquired"
            "sensor_tool" -> "📡 Sensor Telemetry Read"
            "store_search_tool" -> "🏪 Stores Searched"
            "rag_search_tool" -> "📚 Manual Retrieved"
            "history_search_tool" -> "📜 History Retrieved"
            "document_tool" -> "📄 Document Parsed"
            else -> "🔧 ${res.toolName} Completed"
        }
        chips.add(label)
    }

    if (chips.isNotEmpty()) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            chips.forEach { chipText ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = chipText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun UserConfirmationCard(
    pendingToolRequest: ToolRequest?,
    pendingAction: WorkAction?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val toolName = pendingToolRequest?.toolName ?: ""
    val action = pendingToolRequest?.arguments?.get("action") ?: ""
    val storeName = pendingToolRequest?.arguments?.get("storeName") ?: "Hardware Store"
    val distanceKm = pendingToolRequest?.arguments?.get("distanceKm") ?: "1.5"

    val isNavigation = action == "open_navigation" || toolName.contains("navigation")
    val isPhone = action == "dial_phone" || toolName.contains("phone")

    val title = when {
        isNavigation -> "Navigation Confirmation Required"
        isPhone -> "Phone Call Confirmation Required"
        else -> "Explicit User Confirmation Required"
    }

    val bodyText = when {
        isNavigation -> "SeeFix wants to open navigation to $storeName ($distanceKm km away)."
        isPhone -> "SeeFix wants to call $storeName."
        else -> "SeeFix is requesting authorization to execute ${pendingToolRequest?.toolName ?: pendingAction?.description ?: "action"}."
    }

    val confirmButtonText = when {
        isNavigation -> "Open Navigation"
        isPhone -> "Call Store"
        else -> "Confirm Action"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3E2723)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB74D))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isNavigation) Icons.Rounded.Navigation else if (isPhone) Icons.Rounded.Phone else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB74D),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFE0B2)
                )
            }

            Text(
                text = bodyText,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.9f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(confirmButtonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CompletedAnalysisSummaryCard(
    session: SeeFixSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E3A20)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF81C784))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Analysis & Diagnosis Completed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5D6A7)
                )
            }

            Text(
                text = "Identified Issue: ${session.currentTask?.title ?: session.currentTask?.description ?: "Appliance Diagnostic Complete"}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            if (session.observations.isNotEmpty()) {
                Text(
                    text = "Key Evidence & Observations:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA5D6A7)
                )
                session.observations.take(3).forEach { obs ->
                    Text(
                        text = "• ${obs.description}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            if (session.toolResults.isNotEmpty()) {
                Text(
                    text = "Tools Executed: ${session.toolResults.joinToString { it.toolName }}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SimulatedStockChip(
    inventorySource: InventoryDataSource,
    modifier: Modifier = Modifier
) {
    val text = if (inventorySource == InventoryDataSource.SIMULATED_DEMO) "In Stock (SIMULATED)" else "In Stock (CONFIRMED)"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2C3E50),
        border = BorderStroke(1.dp, Color(0xFF00E5FF))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E5FF)
        )
    }
}

@Composable
fun CriticalSafetyBanner(
    hazardMessage: String,
    precaution: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF3B1212),
        border = BorderStroke(2.dp, Color(0xFFE57373))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "CRITICAL SAFETY HAZARD DETECTED",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFCDD2)
                )
            }
            Text(
                text = hazardMessage,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Precaution: $precaution",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun SafetyAlertBanner(
    safetyStatus: SafetyStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, contentColor, icon) = when (safetyStatus.level) {
        SafetyLevel.SAFE -> Triple(
            Color(0xFF1E3A20).copy(alpha = 0.9f),
            Color(0xFF81C784),
            Icons.Rounded.CheckCircle
        )
        SafetyLevel.CAUTION -> Triple(
            Color(0xFF3E2723).copy(alpha = 0.9f),
            Color(0xFFFFB74D),
            Icons.Rounded.Warning
        )
        SafetyLevel.DANGEROUS_STOP -> Triple(
            Color(0xFF3B1212).copy(alpha = 0.95f),
            Color(0xFFE57373),
            Icons.Rounded.Warning
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = safetyStatus.message,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MultimodalStageTracker(
    activeStage: MultimodalStage,
    onStageSelected: (MultimodalStage) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MultimodalStage.entries.forEach { stage ->
            val isSelected = activeStage == stage
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                label = "StageBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.8f),
                label = "StageText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .clickable { onStageSelected(stage) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stage.label,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
fun VoiceStatusBar(
    isListening: Boolean,
    isRecordingVideo: Boolean = false,
    statusText: String,
    onMicToggle: () -> Unit,
    onVideoToggle: () -> Unit = {},
    onSpeakStep: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onMicToggle,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isListening) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = "Mic Toggle",
                    tint = if (isListening) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onVideoToggle,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isRecordingVideo) Color.Red else Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isRecordingVideo) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                    contentDescription = "Video Record Toggle",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onSpeakStep,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "Speak Instruction",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ToolCheckChip(
    tool: ToolItem,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (tool.isAvailable) Color.White.copy(alpha = 0.15f) else Color(0xFF3B1C1C),
        border = BorderStroke(
            1.dp,
            if (tool.isAvailable) Color.White.copy(alpha = 0.3f) else Color(0xFFE57373)
        ),
        modifier = Modifier.clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (tool.isAvailable) Icons.Rounded.CheckCircleOutline else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (tool.isAvailable) Color(0xFF4CAF50) else Color(0xFFE57373),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = tool.name + if (!tool.isAvailable) " (Missing)" else "",
                fontSize = 11.sp,
                color = if (tool.isAvailable) Color.White else Color(0xFFFFCDD2),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiagnosisScreenPreview() {
    SeeFixTheme {
        DiagnosisScreen()
    }
}
