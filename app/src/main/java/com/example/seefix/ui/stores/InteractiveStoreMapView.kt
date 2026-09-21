package com.example.seefix.ui.stores

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.seefix.ai.LocationInfo
import com.example.seefix.location.EnrichedStoreLocation

/**
 * Primary interactive store map view component, backed by the 100% FREE open-source
 * OpenSourceMapView engine with Leaflet HTML and Native Jetpack Compose Canvas fallback.
 */
@Composable
fun InteractiveStoreMapView(
    userLocation: LocationInfo?,
    stores: List<EnrichedStoreLocation>,
    selectedStore: EnrichedStoreLocation?,
    onStoreSelected: (EnrichedStoreLocation) -> Unit,
    modifier: Modifier = Modifier,
    initialTileProvider: MapTileProvider = MapTileProvider.CARTO_DARK,
    initialEngineMode: MapEngineMode = MapEngineMode.WEB_VIEW
) {
    OpenSourceMapView(
        userLocation = userLocation,
        stores = stores,
        selectedStore = selectedStore,
        onStoreSelected = onStoreSelected,
        modifier = modifier,
        initialTileProvider = initialTileProvider,
        initialEngineMode = initialEngineMode
    )
}
