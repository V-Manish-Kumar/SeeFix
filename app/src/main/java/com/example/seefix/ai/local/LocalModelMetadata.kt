package com.example.seefix.ai.local

import com.example.seefix.ai.core.AICapabilities

data class LocalModelInfo(
    val id: String,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val format: String,
    val isLoaded: Boolean = false,
    val capabilities: AICapabilities,
)

data class DeviceCompatibilityInfo(
    val isCompatible: Boolean,
    val totalRamMb: Long,
    val freeRamMb: Long,
    val freeStorageMb: Long,
    val hasGpuSupport: Boolean,
    val warningMessages: List<String>,
)
