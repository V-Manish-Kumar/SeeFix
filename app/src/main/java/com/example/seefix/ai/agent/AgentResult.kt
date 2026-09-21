package com.example.seefix.ai.agent

import com.example.seefix.ai.core.AIError

data class AgentResult(
    val state: AgentState,
    val decision: AgentDecision,
    val explanation: String,
    val isCompleted: Boolean = false,
    val error: AIError? = null
)
