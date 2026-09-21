package com.example.seefix.location

import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.ToolItem
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class StoreSearchResult {
    data class Success(val stores: List<EnrichedStoreLocation>) : StoreSearchResult()
    data class Error(val message: String) : StoreSearchResult()
}

data class StoreStockItem(
    val partName: String,
    val isAvailable: Boolean,
    val stockCountText: String
)

data class EnrichedStoreLocation(
    val store: StoreLocation,
    val category: String,
    val computedDistanceKm: Double,
    val bearingDegrees: Float,
    val cardinalDirection: String,
    val stockItems: List<StoreStockItem>
) {
    val stockBadgeText: String
        get() {
            val availableItem = stockItems.firstOrNull { it.isAvailable }
            return if (availableItem != null) {
                "${availableItem.partName} IN STOCK"
            } else if (stockItems.isNotEmpty()) {
                "${stockItems.first().partName} OUT OF STOCK"
            } else {
                "PARTS IN STOCK"
            }
        }
}

/**
 * Repository for discovering hardware/electrical stores near GNSS coordinates,
 * computing real geodesic distances and compass bearings, and filtering stock for missing tools.
 */
class StoreFinderRepository {

    private val storeDatabase = listOf(
        EnrichedStoreLocation(
            store = StoreLocation(
                id = "store_depot_1",
                name = "Central Electrical & Hardware Depot",
                address = "Plot 12, Mindspace Junction, Tech Park Sector",
                distanceKm = 0.8,
                isOpen = true,
                phone = "+1 555-0122",
                latitude = 37.7833,
                longitude = -122.4167,
                inventorySource = InventoryDataSource.SIMULATED_DEMO
            ),
            category = "Electrical",
            computedDistanceKm = 0.8,
            bearingDegrees = 45f,
            cardinalDirection = "NE",
            stockItems = listOf(
                StoreStockItem("45uF Run Capacitor", true, "8 in stock (SIMULATED)"),
                StoreStockItem("Digital Multimeter CAT III", true, "15 in stock (SIMULATED)"),
                StoreStockItem("Replacement Harness Clip", true, "20 in stock (SIMULATED)")
            )
        ),
        EnrichedStoreLocation(
            store = StoreLocation(
                id = "store_depot_2",
                name = "Metro Tools & Appliance Parts",
                address = "450 Main Rd, near Bio-Diversity Park",
                distanceKm = 2.4,
                isOpen = true,
                phone = "+1 555-0188",
                latitude = 37.7650,
                longitude = -122.4280,
                inventorySource = InventoryDataSource.SIMULATED_DEMO
            ),
            category = "Tools & Hardware",
            computedDistanceKm = 2.4,
            bearingDegrees = 210f,
            cardinalDirection = "SW",
            stockItems = listOf(
                StoreStockItem("45uF Run Capacitor", true, "4 in stock (SIMULATED)"),
                StoreStockItem("Washing Machine Drain Filter Cap", true, "6 in stock (SIMULATED)"),
                StoreStockItem("Insulated Screwdriver Set 1000V", true, "10 in stock (SIMULATED)")
            )
        ),
        EnrichedStoreLocation(
            store = StoreLocation(
                id = "store_depot_3",
                name = "North Sector HVAC & Electrical Supplies",
                address = "102 Kothaguda X Roads, North Sector",
                distanceKm = 3.1,
                isOpen = false,
                phone = "+1 555-0191",
                latitude = 37.7910,
                longitude = -122.4350,
                inventorySource = InventoryDataSource.SIMULATED_DEMO
            ),
            category = "HVAC",
            computedDistanceKm = 3.1,
            bearingDegrees = 330f,
            cardinalDirection = "NW",
            stockItems = listOf(
                StoreStockItem("Inverter Compressor Relay", true, "2 in stock (SIMULATED)"),
                StoreStockItem("HVAC Multi-meter Probe Set", true, "5 in stock (SIMULATED)")
            )
        )
    )

    fun findNearbyStores(
        userLat: Double?,
        userLng: Double?,
        query: String = "",
        missingTools: List<ToolItem> = emptyList()
    ): StoreSearchResult {
        if (userLat == null || userLng == null || (userLat == 0.0 && userLng == 0.0)) {
            return StoreSearchResult.Error("LOCATION_UNAVAILABLE: Valid GNSS location required to find nearby stores.")
        }

        val stores = storeDatabase.map { enriched ->
            val dist = calculateDistanceKm(userLat, userLng, enriched.store.latitude, enriched.store.longitude)
            val bearing = calculateBearingDegrees(userLat, userLng, enriched.store.latitude, enriched.store.longitude)
            val cardinal = getCardinalDirection(bearing)

            val updatedStore = enriched.store.copy(
                distanceKm = Math.round(dist * 10.0) / 10.0,
                inventorySource = InventoryDataSource.SIMULATED_DEMO
            )

            enriched.copy(
                store = updatedStore,
                computedDistanceKm = dist,
                bearingDegrees = bearing,
                cardinalDirection = cardinal
            )
        }.filter { enriched ->
            val matchesQuery = query.isEmpty() ||
                    enriched.store.name.contains(query, ignoreCase = true) ||
                    enriched.store.address.contains(query, ignoreCase = true) ||
                    enriched.stockItems.any { it.partName.contains(query, ignoreCase = true) }

            val matchesMissingTools = missingTools.isEmpty() || missingTools.any { tool ->
                enriched.stockItems.any { stock -> stock.partName.contains(tool.name, ignoreCase = true) }
            }

            matchesQuery && matchesMissingTools
        }.sortedBy { it.computedDistanceKm }

        return StoreSearchResult.Success(stores)
    }

    fun findStores(
        userLat: Double,
        userLng: Double,
        query: String = "",
        missingTools: List<ToolItem> = emptyList()
    ): List<EnrichedStoreLocation> {
        return when (val res = findNearbyStores(userLat, userLng, query, missingTools)) {
            is StoreSearchResult.Success -> res.stores
            is StoreSearchResult.Error -> emptyList()
        }
    }

    companion object {
        fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0 // Radius of Earth in kilometers
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun calculateBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lon2 - lon1)

            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            var bearing = Math.toDegrees(atan2(y, x)).toFloat()
            bearing = (bearing + 360f) % 360f
            return bearing
        }

        fun getCardinalDirection(bearingDegrees: Float): String {
            return when {
                bearingDegrees >= 337.5 || bearingDegrees < 22.5 -> "N"
                bearingDegrees >= 22.5 && bearingDegrees < 67.5 -> "NE"
                bearingDegrees >= 67.5 && bearingDegrees < 112.5 -> "E"
                bearingDegrees >= 112.5 && bearingDegrees < 157.5 -> "SE"
                bearingDegrees >= 157.5 && bearingDegrees < 202.5 -> "S"
                bearingDegrees >= 202.5 && bearingDegrees < 247.5 -> "SW"
                bearingDegrees >= 247.5 && bearingDegrees < 292.5 -> "W"
                bearingDegrees >= 292.5 && bearingDegrees < 337.5 -> "NW"
                else -> "N"
            }
        }
    }
}
