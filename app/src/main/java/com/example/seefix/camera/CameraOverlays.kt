package com.example.seefix.camera

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seefix.domain.model.BoundingBox
import java.util.Locale

enum class CameraViewportMode {
    EXPANDED,   // Fullscreen camera inspection mode (~85-100% height)
    STANDARD,   // Balanced camera view (~45-50% height)
    DOCKED      // Minimized camera viewport card (~140dp height) docked above workflow sheet
}

enum class CameraInteractionMode(val label: String) {
    LIVE_INSPECTION("LIVE"),
    PHOTO("PHOTO"),
    VIDEO("VIDEO")
}

enum class CameraAspectRatio(val label: String, val ratio: Float?) {
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_FILL("FILL", null)
}

data class BoundingBoxWithLabel(
    val box: BoundingBox,
    val label: String,
    val color: Color = Color(0xFFFFB300)
)

/**
 * Pure, clean camera feed overlay - artificial reticle laser lines and brackets removed.
 */
@Composable
fun InspectionReticleOverlay(
    modifier: Modifier = Modifier,
    reticleColor: Color = Color(0xFFFFB300),
    isScanning: Boolean = true
) {
    // Zero artificial overlays - pure clean native camera feed
}

/**
 * Pure, clean camera feed overlay - bounding boxes and yellow labels removed.
 */
@Composable
fun BoundingBoxOverlay(
    boundingBoxes: List<BoundingBoxWithLabel>,
    modifier: Modifier = Modifier
) {
    // Zero artificial bounding boxes - pure clean native camera feed
}

/**
 * Pure, clean camera feed overlay - grid lines removed.
 */
@Composable
fun CameraGridOverlay(
    modifier: Modifier = Modifier,
    lineColor: Color = Color.Transparent
) {
    // Zero grid lines - pure clean native camera feed
}

/**
 * Minimalist Translucent Camera HUD Top Bar:
 * Displays Torch toggle (⚡), Camera mode switcher chips (LIVE, PHOTO, VIDEO), and Close button (✕).
 */
@Composable
fun CameraHUDTopBar(
    interactionMode: CameraInteractionMode,
    onInteractionModeChange: (CameraInteractionMode) -> Unit,
    isTorchOn: Boolean,
    onToggleTorch: () -> Unit,
    onCloseCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Torch / Flash Toggle Button (⚡)
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isTorchOn) Color(0xFFFFB300).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Torch Toggle",
                    tint = if (isTorchOn) Color(0xFFFFB300) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Mode Switcher Chips (LIVE, PHOTO, VIDEO)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraInteractionMode.entries.forEach { mode ->
                    val isSelected = mode == interactionMode
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                        label = "ModeChipBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = 0.85f),
                        label = "ModeChipText"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable { onInteractionModeChange(mode) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }

            // Close Button (✕)
            IconButton(
                onClick = onCloseCamera,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Camera",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Clean Live Captions / Subtitles Overlay Banner:
 * Renders real-time AI guidance overlay banner at the bottom of the camera view,
 * complete with "Live AI Active" status chip and TTS Voice Indicator.
 */
@Composable
fun LiveCaptionsBanner(
    captionText: String,
    isLiveAiActive: Boolean = true,
    isTtsSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.80f),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Status Chip ("Live AI Active") + TTS Voice Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Live AI Active" Status Chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E5FF).copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
                        val dotAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseDot"
                        )

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = dotAlpha))
                        )

                        Text(
                            text = if (isLiveAiActive) "LIVE AI ACTIVE" else "LIVE AI READY",
                            color = Color(0xFF00E5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // TTS Voice Indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTtsSpeaking) Color(0xFFFFB300).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isTtsSpeaking) Color(0xFFFFB300) else Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "TtsPulse")
                        val iconScale by infiniteTransition.animateFloat(
                            initialValue = 0.85f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "SpeakerScale"
                        )

                        Icon(
                            imageVector = if (isTtsSpeaking) Icons.Rounded.GraphicEq else Icons.Rounded.VolumeUp,
                            contentDescription = "TTS Voice Indicator",
                            tint = if (isTtsSpeaking) Color(0xFFFFB300) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp)
                        )

                        Text(
                            text = if (isTtsSpeaking) "SPEAKING VOICE..." else "VOICE READY",
                            color = if (isTtsSpeaking) Color(0xFFFFB300) else Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Real-time AI Live Subtitles Guidance Text
            Text(
                text = captionText.ifEmpty { "Live AI: Point camera at component for real-time guidance" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Floating Translucent Bottom Camera Control HUD Layout:
 * - LIVE Mode: Live Captions Banner + TTS Voice Indicator + "Live AI Active" chip.
 * - PHOTO Mode: Large Camera Shutter Button (+ response caption).
 * - VIDEO Mode: Red Video Record Button + Timer (+ response caption).
 */
@Composable
fun CameraHUDBottomControls(
    interactionMode: CameraInteractionMode,
    liveCaptionText: String,
    isLiveAiActive: Boolean = true,
    isTtsSpeaking: Boolean = false,
    isRecordingVideo: Boolean = false,
    recordingDurationMs: Long = 0L,
    isProcessing: Boolean = false,
    onTakePhoto: () -> Unit = {},
    onToggleVideoRecord: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (interactionMode) {
            CameraInteractionMode.LIVE_INSPECTION -> {
                // Live Captions / Subtitles Overlay Banner + Status Chips
                LiveCaptionsBanner(
                    captionText = liveCaptionText,
                    isLiveAiActive = isLiveAiActive,
                    isTtsSpeaking = isTtsSpeaking
                )
            }

            CameraInteractionMode.PHOTO -> {
                // Caption Banner if AI Response available
                if (liveCaptionText.isNotEmpty()) {
                    LiveCaptionsBanner(
                        captionText = liveCaptionText,
                        isLiveAiActive = false,
                        isTtsSpeaking = isTtsSpeaking
                    )
                }

                // Large Camera Shutter Button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !isProcessing) { onTakePhoto() }
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(if (isProcessing) Color.Gray else Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Take Photo Snapshot",
                            tint = Color.Black,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            CameraInteractionMode.VIDEO -> {
                // Caption Banner if AI Response available
                if (liveCaptionText.isNotEmpty()) {
                    LiveCaptionsBanner(
                        captionText = liveCaptionText,
                        isLiveAiActive = false,
                        isTtsSpeaking = isTtsSpeaking
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Red Video Record Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.35f))
                            .clickable { onToggleVideoRecord() }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(if (isRecordingVideo) RoundedCornerShape(10.dp) else CircleShape)
                                .background(Color.Red),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecordingVideo) Icons.Rounded.Stop else Icons.Rounded.Videocam,
                                contentDescription = "Record Video",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Recording Timer Badge
                    if (isRecordingVideo) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Red.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val durationSec = recordingDurationMs / 1000
                                val minutes = durationSec / 60
                                val seconds = durationSec % 60
                                Text(
                                    text = String.format(Locale.US, "REC %02d:%02d", minutes, seconds),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
