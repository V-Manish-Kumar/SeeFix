package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class HardwareDevice(
    val id: String,
    val name: String,
    val model: String,
    val category: String,
    val imageUrl: String? = null,
    val manualId: String? = null
)
