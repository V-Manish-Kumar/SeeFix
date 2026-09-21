package com.example.seefix.ai.core

import kotlinx.coroutines.flow.Flow

interface AIService {
    val providerConfig: AIProviderConfig
    suspend fun generate(request: AIRequest): AIResponse
    fun stream(request: AIRequest): Flow<AIStreamEvent>
    suspend fun isAvailable(): Boolean
    fun getCapabilities(): AICapabilities
    suspend fun loadModel(): Boolean
    suspend fun unloadModel()
}
