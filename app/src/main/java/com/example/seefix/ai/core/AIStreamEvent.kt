package com.example.seefix.ai.core

sealed class AIStreamEvent {
    data class Chunk(val text: String) : AIStreamEvent()
    data class Completed(val response: AIResponse) : AIStreamEvent()
    data class Error(val error: AIError) : AIStreamEvent()
}
