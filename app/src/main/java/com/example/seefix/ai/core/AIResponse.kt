package com.example.seefix.ai.core

data class AIResponse(
    val text: String,
    val modelName: String,
    val providerName: String,
    val finishReason: String = "completed",
    val usageTokens: Int = 0,
    val error: AIError? = null
)
