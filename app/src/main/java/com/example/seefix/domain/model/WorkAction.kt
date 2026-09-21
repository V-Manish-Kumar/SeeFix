package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    INSPECT,
    MEASURE,
    DISASSEMBLE,
    REPAIR,
    REPLACE,
    INSTALL,
    CONFIGURE,
    CLEAN,
    TEST,
    VERIFY,
    DOCUMENT,
    SEARCH
}

@Serializable
data class WorkAction(
    val id: String,
    val description: String,
    val type: ActionType,
    val status: String = "PENDING",
    val requiredTools: List<ToolItem> = emptyList(),
    val requiredParts: List<ToolItem> = emptyList(),
    val safetyRequirements: List<SafetyRequirement> = emptyList(),
    val verificationMethod: String? = null,
    val dependencies: List<String> = emptyList()
)
