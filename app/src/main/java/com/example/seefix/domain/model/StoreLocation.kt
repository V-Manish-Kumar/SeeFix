package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class InventoryDataSource {
    CONFIRMED_API,
    SIMULATED_DEMO,
    UNAVAILABLE
}

@Serializable
data class StoreLocation(
    val id: String,
    val name: String,
    val address: String,
    val distanceKm: Double = 0.0,
    val isOpen: Boolean = true,
    val phone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val inventorySource: InventoryDataSource = InventoryDataSource.SIMULATED_DEMO
)
