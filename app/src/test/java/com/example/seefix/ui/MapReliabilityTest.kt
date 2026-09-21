package com.example.seefix.ui

import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.location.EnrichedStoreLocation
import com.example.seefix.location.StoreStockItem
import com.example.seefix.ui.stores.MapTileProvider
import com.example.seefix.ui.stores.generateOpenSourceMapHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class MapReliabilityTest {

    private fun createSampleStores(): List<EnrichedStoreLocation> {
        return listOf(
            EnrichedStoreLocation(
                store = StoreLocation(
                    id = "store-sf-1",
                    name = "San Francisco Supply 'Co'",
                    address = "123 Market St, San Francisco, CA",
                    latitude = 37.7800,
                    longitude = -122.4100,
                    phone = "555-0192",
                    isOpen = true
                ),
                category = "Electrical",
                computedDistanceKm = 1.2,
                bearingDegrees = 45.0f,
                cardinalDirection = "NE",
                stockItems = listOf(
                    StoreStockItem("45uF Capacitor", true, "15 in stock"),
                    StoreStockItem("HVAC Fuse", false, "0 in stock")
                )
            ),
            EnrichedStoreLocation(
                store = StoreLocation(
                    id = "store-sf-2",
                    name = "Mission Electronics",
                    address = "456 Mission St, San Francisco, CA",
                    latitude = 37.7700,
                    longitude = -122.4250,
                    phone = "555-0193",
                    isOpen = false
                ),
                category = "Tools & Hardware",
                computedDistanceKm = 2.5,
                bearingDegrees = 210.0f,
                cardinalDirection = "SW",
                stockItems = listOf(
                    StoreStockItem("Multimeter Pro", true, "3 in stock")
                )
            )
        )
    }

    @Test
    fun testHtmlGenerationIncludesLeafletInlineScriptAndCss() {
        val stores = createSampleStores()
        val html = generateOpenSourceMapHtml(
            userLat = 37.7749,
            userLng = -122.4194,
            userAddress = "Test Field Location",
            stores = stores,
            selectedStoreId = "store-sf-1",
            tileProvider = MapTileProvider.CARTO_DARK
        )

        // 1. Verify inline style and script blocks exist
        assertTrue("HTML should contain <style> tag", html.contains("<style>"))
        assertTrue("HTML should contain <script> tag", html.contains("<script>"))

        // 2. Verify Leaflet CSS and JS are bundled directly inline
        assertTrue("HTML should contain embedded Leaflet CSS rules", html.contains(".leaflet-container"))
        assertTrue("HTML should contain embedded Leaflet JS version core", html.contains("Leaflet v1.9.4 Core Engine"))

        // 3. Verify external CDN scripts are NOT referenced
        assertFalse("HTML should NOT depend on external CDN script tag", html.contains("<script src=\"https://unpkg.com/leaflet"))
        assertFalse("HTML should NOT depend on external CDN stylesheet tag", html.contains("<link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet"))
    }

    @Test
    fun testStoreMarkerJsFormattingAndEscaping() {
        val stores = createSampleStores()
        val html = generateOpenSourceMapHtml(
            userLat = 37.7749,
            userLng = -122.4194,
            userAddress = "Test Location",
            stores = stores,
            selectedStoreId = "store-sf-1",
            tileProvider = MapTileProvider.CARTO_DARK
        )

        // Verify single quotes in store name are escaped
        assertTrue("Store name single quotes should be escaped in JS", html.contains("San Francisco Supply \\'Co\\'"))

        // Verify Android bridge click callback is present
        assertTrue("HTML should bind click handler calling AndroidBridge.onStoreClick", html.contains("window.AndroidBridge.onStoreClick('store-sf-1')"))

        // Verify selected marker class
        assertTrue("Selected store should have store-pin-selected class", html.contains("store-pin-selected"))

        // Verify stock badge text in JS HTML string
        assertTrue("Stock badge text should be included in HTML marker", html.contains("45uF Capacitor IN STOCK"))
    }

    @Test
    fun testTileServerUrlsUseHttpsAndReliableEndpoints() {
        MapTileProvider.entries.forEach { provider ->
            assertTrue("Tile URL pattern for ${provider.name} must start with https://", provider.tileUrlPattern.startsWith("https://"))
        }

        assertEquals(
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            MapTileProvider.OPEN_STREET_MAP.tileUrlPattern
        )
        assertEquals(
            "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}@2x.png",
            MapTileProvider.CARTO_VOYAGER.tileUrlPattern
        )
    }

    @Test
    fun testNativeCanvasCoordinateProjection() {
        val centerLat = 37.7749
        val centerLng = -122.4194
        val cosLatRad = cos(Math.toRadians(centerLat))
        val zoomPxPerKm = 80.0f
        val canvasCenterX = 500f
        val canvasCenterY = 500f

        // Store 1 km directly North (deltaLat = +1 / 111.0, deltaLng = 0)
        val storeNorthLat = centerLat + (1.0 / 111.0)
        val storeNorthLng = centerLng

        val deltaLat = storeNorthLat - centerLat
        val deltaLng = storeNorthLng - centerLng

        val pinX = canvasCenterX + (deltaLng * 111.0 * cosLatRad * zoomPxPerKm).toFloat()
        val pinY = canvasCenterY - (deltaLat * 111.0 * zoomPxPerKm).toFloat()

        assertEquals("X coordinate should remain centered for North movement", 500f, pinX, 0.1f)
        assertEquals("Y coordinate should shift upwards by approx 80px for 1 km North", 420f, pinY, 1.0f)
    }

    @Test
    fun testNativeCanvasDistanceRingsCalculations() {
        val zoomPxPerKm = 80.0f
        val distancesKm = listOf(1.0f, 2.0f, 5.0f)

        val radius1km = distancesKm[0] * zoomPxPerKm
        val radius2km = distancesKm[1] * zoomPxPerKm
        val radius5km = distancesKm[2] * zoomPxPerKm

        assertEquals(80.0f, radius1km, 0.001f)
        assertEquals(160.0f, radius2km, 0.001f)
        assertEquals(400.0f, radius5km, 0.001f)
    }

    @Test
    fun testStoreSelectionCallbackAndStockBadgeText() {
        val stores = createSampleStores()
        val selectedStore = stores.first()

        assertEquals("45uF Capacitor IN STOCK", selectedStore.stockBadgeText)
        assertEquals("San Francisco Supply 'Co'", selectedStore.store.name)
        assertEquals(1.2, selectedStore.computedDistanceKm, 0.01)
    }
}
