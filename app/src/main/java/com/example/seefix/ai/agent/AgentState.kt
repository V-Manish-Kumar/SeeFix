package com.example.seefix.ai.agent

import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkTask
import kotlinx.serialization.Serializable

@Serializable
data class AgentState(
    val sessionId: String,
    val currentTask: WorkTask? = null,
    val currentStepIndex: Int = 0,
    val status: AgentStatus = AgentStatus.IDLE,
    val completedSteps: List<WorkAction> = emptyList(),
    val pendingAction: WorkAction? = null,
    val observations: List<Observation> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val iterationCount: Int = 0,
    val maxIterations: Int = 5,
    val errors: List<String> = emptyList()
)
