package com.example.seefix.ai.core

enum class AIProviderType {
    LOCAL_GEMMA,
    GOOGLE_GEMINI,
    OPENAI,
    OPENROUTER,
    ANTHROPIC,
    OLLAMA,
    CUSTOM
}

data class AIProviderConfig(
    val providerType: AIProviderType,
    val name: String,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val organizationId: String? = null,
    val timeoutSeconds: Long = 30,
    val streamingEnabled: Boolean = true
)
