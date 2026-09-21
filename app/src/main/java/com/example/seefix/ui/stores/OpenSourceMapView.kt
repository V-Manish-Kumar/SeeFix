package com.example.seefix.ui.stores

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CompassCalibration
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.seefix.ai.LocationInfo
import com.example.seefix.location.EnrichedStoreLocation

enum class MapEngineMode(val displayName: String) {
    WEB_VIEW("WebView Tile Engine"),
    NATIVE_CANVAS("Native Compose Vector Engine")
}

enum class MapTileProvider(
    val displayName: String,
    val description: String,
    val tileUrlPattern: String,
    val attribution: String,
    val subdomains: String = "abc"
) {
    CARTO_DARK(
        displayName = "Dark Field Engineer",
        description = "High contrast dark mode matching SeeFix theme",
        tileUrlPattern = "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
        attribution = "&copy; OpenStreetMap &copy; CARTO"
    ),
    CARTO_VOYAGER(
        displayName = "CartoDB Voyager",
        description = "Clear street level color tiles",
        tileUrlPattern = "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png",
        attribution = "&copy; OpenStreetMap &copy; CARTO"
    ),
    CARTO_POSITRON(
        displayName = "CartoDB Positron",
        description = "Minimalist light field style",
        tileUrlPattern = "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}@2x.png",
        attribution = "&copy; OpenStreetMap &copy; CARTO"
    ),
    OPEN_STREET_MAP(
        displayName = "OpenStreetMap HTTPS",
        description = "Community driven raster map engine",
        tileUrlPattern = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        attribution = "&copy; OpenStreetMap contributors"
    )
}

class OpenSourceMapWebBridge(
    private val onStoreSelected: (String) -> Unit
) {
    @JavascriptInterface
    fun onStoreClick(storeId: String) {
        onStoreSelected(storeId)
    }
}

/**
 * High-performance, 100% FREE open-source mapping engine for SeeFix field engineers.
 * Combines hardened WebView with inline Leaflet JS/CSS and Native Jetpack Compose Canvas fallback.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun OpenSourceMapView(
    userLocation: LocationInfo?,
    stores: List<EnrichedStoreLocation>,
    selectedStore: EnrichedStoreLocation?,
    onStoreSelected: (EnrichedStoreLocation) -> Unit,
    modifier: Modifier = Modifier,
    initialTileProvider: MapTileProvider = MapTileProvider.CARTO_DARK,
    initialEngineMode: MapEngineMode = MapEngineMode.WEB_VIEW
) {
    var activeEngineMode by remember { mutableStateOf(initialEngineMode) }
    var activeTileProvider by remember { mutableStateOf(initialTileProvider) }
    var showTileMenu by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewErrorOccurred by remember { mutableStateOf(false) }

    val userLat = userLocation?.latitude ?: 37.7749
    val userLng = userLocation?.longitude ?: -122.4194

    val htmlContent = remember(userLat, userLng, stores, selectedStore?.store?.id, activeTileProvider) {
        generateOpenSourceMapHtml(
            userLat = userLat,
            userLng = userLng,
            userAddress = userLocation?.address ?: "Current Position",
            stores = stores,
            selectedStoreId = selectedStore?.store?.id,
            tileProvider = activeTileProvider
        )
    }

    val webBridge = remember(stores) {
        OpenSourceMapWebBridge { storeId ->
            stores.find { it.store.id == storeId }?.let { onStoreSelected(it) }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
    ) {
        if (activeEngineMode == MapEngineMode.NATIVE_CANVAS || webViewErrorOccurred) {
            // Render Pure Jetpack Compose Native Vector Canvas Map Engine
            NativeCanvasMapView(
                userLocation = userLocation,
                stores = stores,
                selectedStore = selectedStore,
                onStoreSelected = onStoreSelected,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Render Hardened WebView with Inline Leaflet CSS & JS
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                        }
                        addJavascriptInterface(webBridge, "AndroidBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    webViewErrorOccurred = true
                                }
                            }
                        }
                        loadDataWithBaseURL("https://openfreemap.org", htmlContent, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                    webView.loadDataWithBaseURL("https://openfreemap.org", htmlContent, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Top Control Overlay: Engine Toggle & Tile Switcher
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .clickable { showTileMenu = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (activeEngineMode == MapEngineMode.NATIVE_CANVAS) Color(0xFF38BDF8) else Color(0xFF4ADE80))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeEngineMode == MapEngineMode.NATIVE_CANVAS)
                        "Native Canvas Vector Engine"
                    else
                        "${activeTileProvider.displayName} (Leaflet Engine)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = "Switch Engine/Tiles",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(14.dp)
                )

                DropdownMenu(
                    expanded = showTileMenu,
                    onDismissRequest = { showTileMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    // Engine Switcher Options
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CompassCalibration,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Native Canvas Vector Engine",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        trailingIcon = {
                            if (activeEngineMode == MapEngineMode.NATIVE_CANVAS) {
                                Icon(Icons.Rounded.Check, contentDescription = "Active", tint = Color(0xFF4ADE80))
                            }
                        },
                        onClick = {
                            activeEngineMode = MapEngineMode.NATIVE_CANVAS
                            showTileMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Web,
                                    contentDescription = null,
                                    tint = Color(0xFF4ADE80),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WebView Tile Engine",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        trailingIcon = {
                            if (activeEngineMode == MapEngineMode.WEB_VIEW) {
                                Icon(Icons.Rounded.Check, contentDescription = "Active", tint = Color(0xFF4ADE80))
                            }
                        },
                        onClick = {
                            activeEngineMode = MapEngineMode.WEB_VIEW
                            webViewErrorOccurred = false
                            showTileMenu = false
                        }
                    )

                    if (activeEngineMode == MapEngineMode.WEB_VIEW) {
                        MapTileProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = {
                                    Column(modifier = Modifier.padding(start = 24.dp)) {
                                        Text(
                                            text = provider.displayName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = provider.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (provider == activeTileProvider) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF4ADE80)
                                        )
                                    }
                                },
                                onClick = {
                                    activeTileProvider = provider
                                    showTileMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // GNSS Center Position FAB (Bottom Right)
        if (activeEngineMode == MapEngineMode.WEB_VIEW && !webViewErrorOccurred) {
            FloatingActionButton(
                onClick = {
                    webViewRef?.evaluateJavascript(
                        "if (window.centerOnUser) { window.centerOnUser(); }",
                        null
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(40.dp),
                shape = CircleShape,
                containerColor = Color(0xFF0F172A).copy(alpha = 0.9f),
                contentColor = Color(0xFF38BDF8),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Center on GNSS Position",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun generateOpenSourceMapHtml(
    userLat: Double,
    userLng: Double,
    userAddress: String,
    stores: List<EnrichedStoreLocation>,
    selectedStoreId: String?,
    tileProvider: MapTileProvider
): String {
    val storeMarkersJs = stores.joinToString("\n") { enriched ->
        val store = enriched.store
        val isSelected = store.id == selectedStoreId
        val badge = enriched.stockBadgeText.replace("'", "\\'")
        val name = store.name.replace("'", "\\'")

        """
        var markerClass = ${if (isSelected) "'store-pin-marker store-pin-selected'" else "'store-pin-marker'"};
        var storeIcon = L.divIcon({
            className: markerClass,
            html: "<div class='store-title'>$name (${enriched.computedDistanceKm} km)</div><div class='stock-badge'>$badge</div>",
            iconSize: null,
            iconAnchor: [60, 24]
        });
        var marker_${store.id.replace("-", "_")} = L.marker([${store.latitude}, ${store.longitude}], {icon: storeIcon}).addTo(map);
        marker_${store.id.replace("-", "_")}.on('click', function() {
            map.panTo([${store.latitude}, ${store.longitude}], { animate: true });
            if (window.AndroidBridge && window.AndroidBridge.onStoreClick) {
                window.AndroidBridge.onStoreClick('${store.id}');
            }
        });
        """.trimIndent()
    }

    val sanitizedAddress = userAddress.replace("'", "\\'")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <style>
                ${LeafletBundle.LEAFLET_CSS}
                html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background-color: #0F172A; }
                .leaflet-container { background-color: #0F172A !important; }
                .user-dot {
                    width: 18px;
                    height: 18px;
                    background-color: #38BDF8;
                    border: 3px solid #FFFFFF;
                    border-radius: 50%;
                    box-shadow: 0 0 12px #38BDF8;
                    animation: pulse-ring 1.8s infinite;
                }
                @keyframes pulse-ring {
                    0% { box-shadow: 0 0 0 0 rgba(56, 189, 248, 0.7); }
                    70% { box-shadow: 0 0 0 16px rgba(56, 189, 248, 0); }
                    100% { box-shadow: 0 0 0 0 rgba(56, 189, 248, 0); }
                }
                .store-pin-marker {
                    background-color: #1E293B;
                    color: #FFFFFF;
                    border: 1.5px solid #38BDF8;
                    border-radius: 10px;
                    padding: 5px 10px;
                    font-family: system-ui, -apple-system, sans-serif;
                    font-size: 11px;
                    font-weight: bold;
                    box-shadow: 0 4px 14px rgba(0,0,0,0.5);
                    text-align: center;
                    white-space: nowrap;
                    cursor: pointer;
                    transition: all 0.2s ease-in-out;
                }
                .store-pin-selected {
                    border: 2.5px solid #4ADE80 !important;
                    background-color: #064E3B !important;
                    box-shadow: 0 6px 18px rgba(74, 222, 128, 0.4) !important;
                    transform: scale(1.1);
                    z-index: 1000 !important;
                }
                .stock-badge {
                    background-color: rgba(56, 189, 248, 0.2);
                    color: #38BDF8;
                    font-size: 9.5px;
                    border-radius: 4px;
                    padding: 2px 6px;
                    margin-top: 3px;
                    display: inline-block;
                    font-weight: 600;
                }
                .store-pin-selected .stock-badge {
                    background-color: rgba(74, 222, 128, 0.25);
                    color: #86EFAC;
                }
            </style>
            <script>
                ${LeafletBundle.LEAFLET_JS}
            </script>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var userLat = $userLat;
                var userLng = $userLng;
                var map = L.map('map', { zoomControl: false }).setView([userLat, userLng], 13);

                L.tileLayer('${tileProvider.tileUrlPattern}', {
                    subdomains: '${tileProvider.subdomains}',
                    maxZoom: 19,
                    attribution: '${tileProvider.attribution}'
                }).addTo(map);

                var userIcon = L.divIcon({
                    className: 'user-dot',
                    iconSize: [18, 18],
                    iconAnchor: [9, 9]
                });
                var userMarker = L.marker([userLat, userLng], {icon: userIcon}).addTo(map);
                userMarker.bindPopup("<b>My GNSS Position</b><br/>$sanitizedAddress");

                window.centerOnUser = function() {
                    map.flyTo([userLat, userLng], 14, { animate: true, duration: 1.2 });
                    userMarker.openPopup();
                };

                $storeMarkersJs
            </script>
        </body>
        </html>
    """.trimIndent()
}
