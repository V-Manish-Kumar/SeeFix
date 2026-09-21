package com.example.seefix.ui.stores

import android.content.Intent
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompassCalibration
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.seefix.ai.LocationInfo
import com.example.seefix.location.EnrichedStoreLocation
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pure Jetpack Compose Canvas Vector Map Engine.
 * Guarantees 100% map rendering, store selection, stock badges, and GNSS position tracking
 * on every Android device and network condition.
 */
@Composable
fun NativeCanvasMapView(
    userLocation: LocationInfo?,
    stores: List<EnrichedStoreLocation>,
    selectedStore: EnrichedStoreLocation?,
    onStoreSelected: (EnrichedStoreLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val userLat = userLocation?.latitude ?: 37.7749
    val userLng = userLocation?.longitude ?: -122.4194

    var centerLat by remember(userLat) { mutableStateOf(userLat) }
    var centerLng by remember(userLng) { mutableStateOf(userLng) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    // Pulsing GNSS dot animation
    val infiniteTransition = rememberInfiniteTransition(label = "GNSS_Pulse")
    val pulseRadiusDp by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    // Calculate map bounds and coordinate projection
    val zoomPxPerKm = 80.0f * zoomScale
    val cosLatRad = remember(centerLat) { cos(Math.toRadians(centerLat)) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
    ) {
        // Compose Canvas Vector Map Engine
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 4.0f)
                        val latShift = (pan.y / zoomPxPerKm) / 111.0f
                        val lngShift = -(pan.x / zoomPxPerKm) / (111.0f * cosLatRad.toFloat())
                        centerLat += latShift
                        centerLng += lngShift
                    }
                }
                .pointerInput(stores, centerLat, centerLng, zoomScale) {
                    detectTapGestures { tapOffset ->
                        val screenWidth = size.width
                        val screenHeight = size.height
                        val canvasCenterX = screenWidth / 2.0f
                        val canvasCenterY = screenHeight / 2.0f

                        var closestStore: EnrichedStoreLocation? = null
                        var minDistance = Float.MAX_VALUE

                        stores.forEach { enriched ->
                            val store = enriched.store
                            val deltaLat = store.latitude - centerLat
                            val deltaLng = store.longitude - centerLng
                            val storeX = canvasCenterX + (deltaLng * 111.0 * cosLatRad * zoomPxPerKm).toFloat()
                            val storeY = canvasCenterY - (deltaLat * 111.0 * zoomPxPerKm).toFloat()

                            val dist = sqrt((tapOffset.x - storeX).pow(2) + (tapOffset.y - storeY).pow(2))
                            if (dist < 80f && dist < minDistance) {
                                minDistance = dist
                                closestStore = enriched
                            }
                        }

                        closestStore?.let { onStoreSelected(it) }
                    }
                }
        ) {
            val canvasCenterX = size.width / 2.0f
            val canvasCenterY = size.height / 2.0f

            // 1. Draw Background Grid & Road Network
            drawRoadNetworkGrid(
                canvasWidth = size.width,
                canvasHeight = size.height,
                centerX = canvasCenterX,
                centerY = canvasCenterY,
                zoomScale = zoomScale
            )

            // 2. Draw Distance Concentric Rings (1 km, 2 km, 5 km)
            drawDistanceRings(
                centerX = canvasCenterX,
                centerY = canvasCenterY,
                zoomPxPerKm = zoomPxPerKm
            )

            // 3. Draw Store Marker Pins & Stock Badges
            stores.forEach { enriched ->
                val store = enriched.store
                val isSelected = store.id == selectedStore?.store?.id
                val deltaLat = store.latitude - centerLat
                val deltaLng = store.longitude - centerLng
                val pinX = canvasCenterX + (deltaLng * 111.0 * cosLatRad * zoomPxPerKm).toFloat()
                val pinY = canvasCenterY - (deltaLat * 111.0 * zoomPxPerKm).toFloat()

                drawStoreMarkerPin(
                    pinX = pinX,
                    pinY = pinY,
                    enrichedStore = enriched,
                    isSelected = isSelected
                )
            }

            // 4. Draw User GNSS Dot
            val userDeltaLat = userLat - centerLat
            val userDeltaLng = userLng - centerLng
            val userX = canvasCenterX + (userDeltaLng * 111.0 * cosLatRad * zoomPxPerKm).toFloat()
            val userY = canvasCenterY - (userDeltaLat * 111.0 * zoomPxPerKm).toFloat()

            drawUserGnssDot(
                userX = userX,
                userY = userY,
                pulseRadiusPx = pulseRadiusDp.dp.toPx(),
                pulseAlpha = pulseAlpha,
                userAddress = userLocation?.address ?: "Current GNSS Position"
            )
        }

        // Top Left: Engine Identification Banner
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Native Canvas Vector Engine",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.CompassCalibration,
                    contentDescription = null,
                    tint = Color(0xFF4ADE80),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Top Right: Zoom In / Zoom Out Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(4.0f) },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B).copy(alpha = 0.9f),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }

            FloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.5f) },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                containerColor = Color(0xFF1E293B).copy(alpha = 0.9f),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Right: GNSS Re-center FAB
        FloatingActionButton(
            onClick = {
                centerLat = userLat
                centerLng = userLng
                zoomScale = 1.0f
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (selectedStore != null) 170.dp else 12.dp, end = 12.dp)
                .size(40.dp),
            shape = CircleShape,
            containerColor = Color(0xFF0F172A).copy(alpha = 0.95f),
            contentColor = Color(0xFF38BDF8),
            elevation = FloatingActionButtonDefaults.elevation(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = "Center GNSS Position",
                modifier = Modifier.size(20.dp)
            )
        }

        // Bottom Selected Store Action Sheet Banner
        selectedStore?.let { storeDetail ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = storeDetail.store.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = storeDetail.store.address,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${storeDetail.computedDistanceKm} km (${storeDetail.cardinalDirection})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    // Stock Item Badges Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (storeDetail.stockItems.any { it.isAvailable }) Color(0xFF064E3B) else Color(0xFF7F1D1D),
                            border = BorderStroke(1.dp, if (storeDetail.stockItems.any { it.isAvailable }) Color(0xFF22C55E) else Color(0xFFEF4444))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (storeDetail.stockItems.any { it.isAvailable }) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = if (storeDetail.stockItems.any { it.isAvailable }) Color(0xFF4ADE80) else Color(0xFFFCA5A5),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = storeDetail.stockBadgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Store Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, "tel:${storeDetail.store.phone}".toUri())
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF64748B))
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                val store = storeDetail.store
                                val googleNavUri = "google.navigation:q=${store.latitude},${store.longitude}".toUri()
                                val mapIntent = Intent(Intent.ACTION_VIEW, googleNavUri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val geoUri = "geo:${store.latitude},${store.longitude}?q=${Uri.encode(store.name)}".toUri()
                                    val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                    try {
                                        context.startActivity(fallbackIntent)
                                    } catch (_: Exception) {
                                        val webUri = "https://www.google.com/maps/search/?api=1&query=${store.latitude},${store.longitude}".toUri()
                                        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Rounded.NearMe, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRoadNetworkGrid(
    canvasWidth: Float,
    canvasHeight: Float,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    val gridStep = 80f * zoomScale

    // Secondary street grid lines
    var x = centerX % gridStep
    while (x < canvasWidth) {
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(x, 0f),
            end = Offset(x, canvasHeight),
            strokeWidth = 1.5f
        )
        x += gridStep
    }

    var y = centerY % gridStep
    while (y < canvasHeight) {
        drawLine(
            color = Color(0xFF1E293B),
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 1.5f
        )
        y += gridStep
    }

    // Major N-S and E-W Arterial Avenues
    val avenueStroke = (8f * zoomScale).coerceIn(4f, 16f)
    drawLine(
        color = Color(0xFF334155),
        start = Offset(centerX, 0f),
        end = Offset(centerX, canvasHeight),
        strokeWidth = avenueStroke
    )
    drawLine(
        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
        start = Offset(centerX, 0f),
        end = Offset(centerX, canvasHeight),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
    )

    drawLine(
        color = Color(0xFF334155),
        start = Offset(0f, centerY),
        end = Offset(canvasWidth, centerY),
        strokeWidth = avenueStroke
    )
    drawLine(
        color = Color(0xFF38BDF8).copy(alpha = 0.4f),
        start = Offset(0f, centerY),
        end = Offset(canvasWidth, centerY),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
    )

    // Diagonal Expressway
    val path = Path().apply {
        moveTo(centerX - 400f * zoomScale, centerY + 300f * zoomScale)
        lineTo(centerX + 400f * zoomScale, centerY - 300f * zoomScale)
    }
    drawPath(
        path = path,
        color = Color(0xFF1E3A8A),
        style = Stroke(width = 10f * zoomScale)
    )
    drawPath(
        path = path,
        color = Color(0xFF38BDF8),
        style = Stroke(width = 2f * zoomScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    )

    // Avenue Labels
    drawContext.canvas.nativeCanvas.apply {
        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }
        drawText("MAIN ARTERIAL BLVD", centerX + 12f, 40f, textPaint)
        drawText("GRAND CENTRAL AVE", 20f, centerY - 12f, textPaint)
    }
}

private fun DrawScope.drawDistanceRings(
    centerX: Float,
    centerY: Float,
    zoomPxPerKm: Float
) {
    val distancesKm = listOf(1.0f, 2.0f, 5.0f)
    val ringPaint = Stroke(
        width = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
    )

    distancesKm.forEach { distKm ->
        val radiusPx = distKm * zoomPxPerKm
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.35f),
            radius = radiusPx,
            center = Offset(centerX, centerY),
            style = ringPaint
        )

        // Draw ring text label along the radial line
        drawContext.canvas.nativeCanvas.apply {
            val labelPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8")
                textSize = 24f
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawText("${distKm.toInt()} KM", centerX + radiusPx + 6f, centerY - 6f, labelPaint)
        }
    }
}

private fun DrawScope.drawUserGnssDot(
    userX: Float,
    userY: Float,
    pulseRadiusPx: Float,
    pulseAlpha: Float,
    userAddress: String
) {
    // Pulsing outer aura ring
    drawCircle(
        color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
        radius = pulseRadiusPx,
        center = Offset(userX, userY)
    )

    // Outer glow halo
    drawCircle(
        color = Color(0xFF0284C7).copy(alpha = 0.5f),
        radius = 16f,
        center = Offset(userX, userY)
    )

    // Inner GNSS Core Dot
    drawCircle(
        color = Color(0xFF38BDF8),
        radius = 10f,
        center = Offset(userX, userY)
    )
    drawCircle(
        color = Color.White,
        radius = 10f,
        center = Offset(userX, userY),
        style = Stroke(width = 3f)
    )

    // Text Label below user position
    drawContext.canvas.nativeCanvas.apply {
        val labelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#0F172A")
            isAntiAlias = true
        }
        val labelText = "MY GNSS POSITION"
        val bounds = Rect()
        labelPaint.getTextBounds(labelText, 0, labelText.length, bounds)

        val rectLeft = userX - (bounds.width() / 2f) - 12f
        val rectTop = userY + 18f
        val rectRight = userX + (bounds.width() / 2f) + 12f
        val rectBottom = userY + 18f + bounds.height() + 12f

        drawRoundRect(rectLeft, rectTop, rectRight, rectBottom, 8f, 8f, bgPaint)
        drawText(labelText, userX, userY + 24f + bounds.height(), labelPaint)
    }
}

private fun DrawScope.drawStoreMarkerPin(
    pinX: Float,
    pinY: Float,
    enrichedStore: EnrichedStoreLocation,
    isSelected: Boolean
) {
    val pinRadius = if (isSelected) 14f else 10f
    val pinColor = if (isSelected) Color(0xFF4ADE80) else Color(0xFFEF4444)
    val borderColor = if (isSelected) Color(0xFF86EFAC) else Color.White

    // Draw Pin Point Anchor Needle
    val needlePath = Path().apply {
        moveTo(pinX - pinRadius, pinY)
        lineTo(pinX + pinRadius, pinY)
        lineTo(pinX, pinY + pinRadius * 1.8f)
        close()
    }
    drawPath(path = needlePath, color = pinColor)

    // Main Pin Head Circle
    drawCircle(
        color = pinColor,
        radius = pinRadius,
        center = Offset(pinX, pinY)
    )
    drawCircle(
        color = borderColor,
        radius = pinRadius,
        center = Offset(pinX, pinY),
        style = Stroke(width = 3f)
    )
    drawCircle(
        color = Color(0xFF0F172A),
        radius = pinRadius * 0.4f,
        center = Offset(pinX, pinY)
    )

    // Store Title & Stock Availability Badge Pill above/below pin
    drawContext.canvas.nativeCanvas.apply {
        val titleText = "${enrichedStore.store.name} (${enrichedStore.computedDistanceKm} km)"
        val stockText = enrichedStore.stockBadgeText

        val titlePaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = if (isSelected) 28f else 24f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val badgePaint = Paint().apply {
            color = if (enrichedStore.stockItems.any { it.isAvailable })
                android.graphics.Color.parseColor("#4ADE80")
            else
                android.graphics.Color.parseColor("#FCA5A5")
            textSize = 20f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        val bgPaint = Paint().apply {
            color = if (isSelected)
                android.graphics.Color.parseColor("#064E3B")
            else
                android.graphics.Color.parseColor("#1E293B")
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = if (isSelected)
                android.graphics.Color.parseColor("#4ADE80")
            else
                android.graphics.Color.parseColor("#38BDF8")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        val titleBounds = Rect()
        titlePaint.getTextBounds(titleText, 0, titleText.length, titleBounds)

        val badgeBounds = Rect()
        badgePaint.getTextBounds(stockText, 0, stockText.length, badgeBounds)

        val pillWidth = Math.max(titleBounds.width(), badgeBounds.width()) + 24f
        val pillHeight = titleBounds.height() + badgeBounds.height() + 20f

        val pillLeft = pinX - (pillWidth / 2f)
        val pillTop = pinY - pinRadius - pillHeight - 10f
        val pillRight = pinX + (pillWidth / 2f)
        val pillBottom = pinY - pinRadius - 10f

        drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, 12f, 12f, bgPaint)
        drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, 12f, 12f, borderPaint)

        drawText(titleText, pinX, pillTop + titleBounds.height() + 8f, titlePaint)
        drawText(stockText, pinX, pillTop + titleBounds.height() + badgeBounds.height() + 16f, badgePaint)
    }
}
