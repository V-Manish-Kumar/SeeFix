package com.example.seefix.ai.agent

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val toolName: String,
    val isSuccess: Boolean,
    val outputData: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
