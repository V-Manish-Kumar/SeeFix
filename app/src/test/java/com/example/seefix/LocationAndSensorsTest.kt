package com.example.seefix

import com.example.seefix.domain.model.ToolItem
import com.example.seefix.location.LocationManager
import com.example.seefix.location.LocationResult
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.location.StoreSearchResult
import com.example.seefix.sensors.SensorRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndSensorsTest {

    @Test
    fun locationResult_returnsUnavailableWhenPermissionMissing() = runTest {
        // Without mocked android context/permissions, default LocationManager returns Unavailable
        val storeRepo = StoreFinderRepository()
        val result = storeRepo.findNearbyStores(0.0, 0.0)
        assertTrue(result is StoreSearchResult.Error)
    }

    @Test
    fun storeFinderRepository_computesRealDistanceAndCardinalDirection() {
        val storeRepo = StoreFinderRepository()
        val userLat = 37.7749
        val userLng = -122.4194

        val stores = storeRepo.findStores(userLat, userLng)

        assertTrue(stores.isNotEmpty())
        val nearest = stores.first()

        assertNotNull(nearest.store.name)
        assertTrue(nearest.computedDistanceKm > 0.0)
        assertTrue(nearest.bearingDegrees >= 0f && nearest.bearingDegrees < 360f)
        assertTrue(nearest.cardinalDirection.isNotEmpty())
        assertTrue(nearest.stockItems.isNotEmpty())
    }

    @Test
    fun storeFinderRepository_filtersMissingTools() {
        val storeRepo = StoreFinderRepository()
        val missingTools = listOf(
            ToolItem("t1", "45uF Run Capacitor", isAvailable = false, isRequired = true)
        )

        val matchingStores = storeRepo.findStores(37.7749, -122.4194, missingTools = missingTools)

        assertTrue(matchingStores.isNotEmpty())
        assertTrue(matchingStores.all { store ->
            store.stockItems.any { it.partName.contains("Capacitor", ignoreCase = true) }
        })
    }

    @Test
    fun storeFinderRepository_calculatesHaversineDistanceCorrectly() {
        val distance = StoreFinderRepository.calculateDistanceKm(37.7749, -122.4194, 37.7650, -122.4280)

        assertTrue("Distance should be around 1.3 km but was $distance", distance in 0.8..2.5)
    }

    @Test
    fun sensorRepository_convertsAzimuthToCardinalDirection() {
        assertEquals("N", SensorRepository.getCardinalFromAzimuth(0f))
        assertEquals("NE", SensorRepository.getCardinalFromAzimuth(45f))
        assertEquals("E", SensorRepository.getCardinalFromAzimuth(90f))
        assertEquals("SE", SensorRepository.getCardinalFromAzimuth(135f))
        assertEquals("S", SensorRepository.getCardinalFromAzimuth(180f))
        assertEquals("SW", SensorRepository.getCardinalFromAzimuth(225f))
        assertEquals("W", SensorRepository.getCardinalFromAzimuth(270f))
        assertEquals("NW", SensorRepository.getCardinalFromAzimuth(315f))
    }
}
