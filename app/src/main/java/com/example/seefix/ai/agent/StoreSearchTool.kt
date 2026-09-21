package com.example.seefix.ai.agent

import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.location.LocationManager
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.location.StoreSearchResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StoreSearchTool(
    private val storeFinderRepository: StoreFinderRepository = StoreFinderRepository(),
    private val locationManager: LocationManager? = null
) : SeeFixTool {
    override val name: String = "store_search_tool"
    override val description: String = "Searches nearby stores stocking required equipment tools or parts."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = listOf("query"),
        optionalParameters = listOf("latitude", "longitude", "item", "category", "radiusKm"),
        description = "Search store locator"
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        val query = arguments["query"] ?: arguments["item"] ?: arguments["category"]
        if (query.isNullOrBlank()) {
            return ToolValidationResult(
                isValid = false,
                errorMessage = "Missing required parameter 'query'"
            )
        }
        return ToolValidationResult(isValid = true)
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val validation = validate(arguments)
        if (!validation.isValid) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = validation.errorMessage
            )
        }

        val itemQuery = arguments["query"] ?: arguments["item"] ?: arguments["category"] ?: ""
        val categoryArg = arguments["category"] ?: ""
        val radiusKm = arguments["radiusKm"]?.toDoubleOrNull() ?: 10.0

        val argLat = arguments["latitude"]?.toDoubleOrNull() ?: arguments["userLat"]?.toDoubleOrNull()
        val argLng = arguments["longitude"]?.toDoubleOrNull() ?: arguments["userLng"]?.toDoubleOrNull()

        val userLoc = locationManager?.userLocation?.value
        val userLat = argLat ?: userLoc?.latitude
        val userLng = argLng ?: userLoc?.longitude

        if (userLat == null || userLng == null || (userLat == 0.0 && userLng == 0.0)) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = "LOCATION_UNAVAILABLE: Current device location is required to perform nearby store search."
            )
        }

        val result = storeFinderRepository.findNearbyStores(
            userLat = userLat,
            userLng = userLng,
            query = itemQuery
        )

        val stores = when (result) {
            is StoreSearchResult.Success -> result.stores.filter { it.computedDistanceKm <= radiusKm }
            is StoreSearchResult.Error -> return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = result.message
            )
        }

        var finalStores = stores
        if (categoryArg.isNotBlank() && itemQuery.isNotBlank()) {
            val filteredByCategory = stores.filter { it.category.contains(categoryArg, ignoreCase = true) }
            if (filteredByCategory.isNotEmpty()) {
                finalStores = filteredByCategory
            }
        }

        val nearest = finalStores.firstOrNull()
        val nearestStockStatus = nearest?.stockItems?.firstOrNull { stock ->
            stock.partName.contains(itemQuery, ignoreCase = true)
        }?.stockCountText ?: if (nearest != null && nearest.store.isOpen) "In Stock (SIMULATED)" else "Out of Stock"

        val storesJsonList = finalStores.map { enriched ->
            mapOf(
                "id" to enriched.store.id,
                "name" to enriched.store.name,
                "address" to enriched.store.address,
                "category" to enriched.category,
                "distanceKm" to enriched.computedDistanceKm.toString(),
                "isOpen" to enriched.store.isOpen.toString(),
                "phone" to enriched.store.phone,
                "inventorySource" to enriched.store.inventorySource.name
            )
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "storeCount" to finalStores.size.toString(),
                "storesJson" to json.encodeToString(storesJsonList),
                "nearestStoreName" to (nearest?.store?.name ?: "None"),
                "nearestDistanceKm" to (nearest?.computedDistanceKm?.toString() ?: "0.0"),
                "nearestStockStatus" to nearestStockStatus,
                "inventorySource" to (nearest?.store?.inventorySource?.name ?: InventoryDataSource.SIMULATED_DEMO.name)
            )
        )
    }
}
