package com.example.seefix.domain.model

import com.example.seefix.ai.agent.AgentState
import com.example.seefix.ai.agent.AgentStatus
import com.example.seefix.ai.agent.ToolResult
import com.example.seefix.ai.core.AIMessage

data class SeeFixSession(
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val currentTask: WorkTask? = null,
    val workContext: WorkContext,
    val agentState: AgentState,
    val conversation: List<AIMessage> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val toolResults: List<ToolResult> = emptyList(),
    val completedSteps: List<WorkAction> = emptyList(),
    val status: AgentStatus = AgentStatus.IDLE
) {
    val troubleSession: TroubleshootingSession
        get() = toTroubleshootingSession()

    val safetyStatus: SafetyStatus
        get() = troubleSession.safetyStatus

    val device: HardwareDevice?
        get() = troubleSession.device

    fun toTroubleshootingSession(): TroubleshootingSession {
        return TroubleshootingSession(
            id = sessionId,
            detectedProblem = currentTask?.description ?: workContext.userInput ?: "",
            observations = observations.map { it.description },
            currentStepIndex = agentState.currentStepIndex,
            steps = completedSteps.mapIndexed { idx, action ->
                TroubleshootingStep.fromWorkAction(idx + 1, action)
            },
            availableTools = workContext.availableTools,
            missingTools = workContext.availableParts,
            safetyStatus = if (workContext.safetyContext.any { it.severity == SafetySeverity.CRITICAL_STOP }) {
                SafetyStatus(SafetyLevel.DANGEROUS_STOP, workContext.safetyContext.firstOrNull()?.precaution ?: "Critical Safety Hazard")
            } else {
                SafetyStatus.SAFE_DEFAULT
            },
            timestamp = createdAt,
            statusText = status.name,
            domain = workContext.domain
        )
    }

    companion object {
        fun createEmpty(sessionId: String = "sess_${System.currentTimeMillis()}"): SeeFixSession {
            val emptyWorkContext = WorkContext()
            val emptyAgentState = AgentState(sessionId = sessionId)
            return SeeFixSession(
                sessionId = sessionId,
                workContext = emptyWorkContext,
                agentState = emptyAgentState,
                status = AgentStatus.IDLE
            )
        }

        fun fromTroubleshootingSession(troubleSession: TroubleshootingSession): SeeFixSession {
            val workTask = troubleSession.toWorkTask()
            val workContext = WorkContext(
                task = workTask,
                domain = troubleSession.domain,
                userInput = troubleSession.detectedProblem,
                availableTools = troubleSession.availableTools,
                availableParts = troubleSession.missingTools,
                observations = troubleSession.observations.mapIndexed { idx, obs ->
                    Observation(id = "obs_$idx", description = obs, source = ObservationSource.USER)
                }
            )
            val agentStatus = try {
                AgentStatus.valueOf(troubleSession.statusText)
            } catch (_: Exception) {
                AgentStatus.IDLE
            }
            val agentState = AgentState(
                sessionId = troubleSession.id,
                currentTask = workTask,
                currentStepIndex = troubleSession.currentStepIndex,
                status = agentStatus
            )
            return SeeFixSession(
                sessionId = troubleSession.id,
                createdAt = troubleSession.timestamp,
                updatedAt = troubleSession.timestamp,
                currentTask = workTask,
                workContext = workContext,
                agentState = agentState,
                observations = workContext.observations,
                status = agentStatus
            )
        }
    }
}
