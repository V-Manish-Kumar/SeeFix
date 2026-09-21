package com.example.seefix.ai.agent

import kotlinx.serialization.Serializable

@Serializable
data class ToolRequest(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val reason: String = ""
)
