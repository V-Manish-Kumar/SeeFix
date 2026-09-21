package com.example.seefix.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Modern Field-Engineer Edge-to-Edge Pure Camera Preview Composable:
 * - 100% pure, clean, native camera feed (like a standard camera app).
 * - Zero artificial laser lines, yellow bounding boxes, reticles, or grid overlays over the image.
 * - Minimalist translucent Top HUD bar (Torch toggle ⚡, Mode Switcher: LIVE, PHOTO, VIDEO, Close ✕).
 * - Floating Bottom HUD Layout with Live Captions, TTS Voice Indicator, Shutter / Record buttons.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    boundingBoxes: List<BoundingBoxWithLabel> = emptyList(),
    isScanning: Boolean = false,
    showOverlays: Boolean = false,
    analyzer: ImageAnalysis.Analyzer? = null,
    cameraManager: CameraManager? = null,
    recordingState: VideoRecordingState = VideoRecordingState.Idle,
    viewportMode: CameraViewportMode = CameraViewportMode.EXPANDED,
    interactionMode: CameraInteractionMode = CameraInteractionMode.LIVE_INSPECTION,
    aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_FILL,
    showGrid: Boolean = false,
    isProcessing: Boolean = false,
    liveCaptionText: String = "",
    isLiveAiActive: Boolean = true,
    isTtsSpeaking: Boolean = false,
    onViewportModeChange: (CameraViewportMode) -> Unit = {},
    onInteractionModeChange: (CameraInteractionMode) -> Unit = {},
    onAspectRatioChange: (CameraAspectRatio) -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onToggleVideoRecord: () -> Unit = {},
    onVerifyReticle: () -> Unit = {},
    onCloseCamera: () -> Unit = {},
    onCameraError: ((Exception) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionsToRequest = remember {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    fun checkCameraGranted(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    fun checkAudioGranted(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    var isCameraGranted by remember { mutableStateOf(checkCameraGranted()) }
    var isAudioGranted by remember { mutableStateOf(checkAudioGranted()) }

    var cameraError by remember { mutableStateOf<Exception?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        isCameraGranted = permissionsMap[Manifest.permission.CAMERA] ?: checkCameraGranted()
        isAudioGranted = permissionsMap[Manifest.permission.RECORD_AUDIO] ?: checkAudioGranted()
    }

    LaunchedEffect(Unit) {
        if (!checkCameraGranted() || !checkAudioGranted()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    val localCameraManager = remember { CameraManager(context) }
    val effectiveCameraManager = cameraManager ?: localCameraManager
    var isTorchOn by remember { mutableStateOf(false) }

    DisposableEffect(effectiveCameraManager) {
        onDispose {
            if (cameraManager == null) {
                localCameraManager.shutdown()
            }
        }
    }

    val isRecordingVideo = recordingState is VideoRecordingState.Recording
    val recordingDurationMs = (recordingState as? VideoRecordingState.Recording)?.durationMs ?: 0L

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isCameraGranted) {
            if (cameraError != null) {
                // Graceful Camera Hardware Error View
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Camera Provider Initialization Issue",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cameraError?.localizedMessage ?: "Unable to initialize camera device streams.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                cameraError = null
                                retryCount++
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Retry Camera Binding", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // 100% Pure Clean Camera Viewfinder Container
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                effectiveCameraManager.startCamera(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = this,
                                    analyzer = analyzer,
                                    onError = { e ->
                                        cameraError = e
                                        onCameraError?.invoke(e)
                                    }
                                )
                            }
                        },
                        update = { previewView ->
                            if (retryCount > 0) {
                                effectiveCameraManager.startCamera(
                                    lifecycleOwner = lifecycleOwner,
                                    previewView = previewView,
                                    analyzer = analyzer,
                                    onError = { e ->
                                        cameraError = e
                                        onCameraError?.invoke(e)
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Minimalist Top Camera HUD Toolbar (Torch toggle ⚡, Mode Switcher: LIVE, PHOTO, VIDEO, Close ✕)
                CameraHUDTopBar(
                    interactionMode = interactionMode,
                    onInteractionModeChange = onInteractionModeChange,
                    isTorchOn = isTorchOn,
                    onToggleTorch = {
                        isTorchOn = effectiveCameraManager.toggleTorch()
                    },
                    onCloseCamera = onCloseCamera,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // Floating Bottom Camera Action Controls & Captions Banner
                CameraHUDBottomControls(
                    interactionMode = interactionMode,
                    liveCaptionText = liveCaptionText,
                    isLiveAiActive = isLiveAiActive,
                    isTtsSpeaking = isTtsSpeaking,
                    isRecordingVideo = isRecordingVideo,
                    recordingDurationMs = recordingDurationMs,
                    isProcessing = isProcessing,
                    onTakePhoto = onTakePhoto,
                    onToggleVideoRecord = onToggleVideoRecord,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        } else {
            // Camera & Microphone Permission Request View
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera & Microphone Permission Needed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SeeFix requires Camera access for real-time visual hardware analysis and Microphone access for hands-free voice diagnostics.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { permissionLauncher.launch(permissionsToRequest) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Camera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Grant Camera & Microphone Permissions", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
