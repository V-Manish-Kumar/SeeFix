package com.example.seefix.ai.agent

import com.example.seefix.domain.model.WorkAction
import kotlinx.serialization.Serializable

@Serializable
data class AgentDecision(
    val actionType: AgentActionType,
    val toolRequest: ToolRequest? = null,
    val userQuestion: String? = null,
    val proposedAction: WorkAction? = null,
    val reason: String = "",
    val confidence: Float = 1.0f
)
