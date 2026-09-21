package com.example.seefix.ai.agent

import kotlinx.serialization.Serializable

@Serializable
data class ToolInputSchema(
    val requiredParameters: List<String> = emptyList(),
    val optionalParameters: List<String> = emptyList(),
    val description: String = ""
)
