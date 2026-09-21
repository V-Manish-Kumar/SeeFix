package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolItem(
    val id: String,
    val name: String,
    val isAvailable: Boolean = true,
    val isRequired: Boolean = false,
    val storeQueryKey: String = ""
)
