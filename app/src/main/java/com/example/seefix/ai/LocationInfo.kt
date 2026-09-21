package com.example.seefix.ai

import kotlinx.serialization.Serializable

@Serializable
data class LocationInfo(
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val address: String = "User Location"
)
