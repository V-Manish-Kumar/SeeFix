package com.example.seefix.ai.core

enum class AIErrorCode {
    MODEL_NOT_FOUND,
    MODEL_LOAD_FAILED,
    MODEL_UNSUPPORTED,
    INSUFFICIENT_MEMORY,
    INSUFFICIENT_STORAGE,
    RUNTIME_UNAVAILABLE,
    INFERENCE_FAILED,
    INFERENCE_CANCELLED,
    PROVIDER_UNAVAILABLE,
    NETWORK_ERROR,
    TIMEOUT,
    INVALID_API_KEY,
    RATE_LIMITED,
    INVALID_RESPONSE,
    UNSUPPORTED_CAPABILITY
}

data class AIError(
    val code: AIErrorCode,
    val message: String,
    val cause: Throwable? = null
) {
    fun toUserFacingMessage(): String {
        val baseMessage = when (code) {
            AIErrorCode.MODEL_NOT_FOUND -> "The requested AI model could not be found."
            AIErrorCode.MODEL_LOAD_FAILED -> "Failed to load the AI model into memory."
            AIErrorCode.MODEL_UNSUPPORTED -> "The specified AI model is not supported on this device."
            AIErrorCode.INSUFFICIENT_MEMORY -> "Insufficient device memory available to run the model."
            AIErrorCode.INSUFFICIENT_STORAGE -> "Insufficient storage space available on device."
            AIErrorCode.RUNTIME_UNAVAILABLE -> "The required AI execution runtime is unavailable."
            AIErrorCode.INFERENCE_FAILED -> "AI processing failed while generating a response."
            AIErrorCode.INFERENCE_CANCELLED -> "AI operation was cancelled."
            AIErrorCode.PROVIDER_UNAVAILABLE -> "The selected AI provider is currently unavailable."
            AIErrorCode.NETWORK_ERROR -> "Network connection error. Please check your internet connection."
            AIErrorCode.TIMEOUT -> "The request timed out while waiting for AI response."
            AIErrorCode.INVALID_API_KEY -> "Invalid or missing API key for AI provider."
            AIErrorCode.RATE_LIMITED -> "Rate limit exceeded. Please try again in a moment."
            AIErrorCode.INVALID_RESPONSE -> "Received an invalid response from the AI provider."
            AIErrorCode.UNSUPPORTED_CAPABILITY -> "This AI model does not support the requested capability."
        }
        return if (message.isNotBlank() && message != code.name) {
            "$baseMessage ($message)"
        } else {
            baseMessage
        }
    }
}
