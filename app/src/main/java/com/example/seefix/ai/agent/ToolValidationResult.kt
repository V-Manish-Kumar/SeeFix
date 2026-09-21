package com.example.seefix.ai.agent

data class ToolValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
