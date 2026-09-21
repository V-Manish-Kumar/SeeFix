package com.example.seefix.ai.context

data class WorkContextConfig(
    val maxTotalImages: Int = 12,
    val maxHistoryEntries: Int = 5,
    val maxRagChunks: Int = 3,
    val maxConversationTurns: Int = 10
)
