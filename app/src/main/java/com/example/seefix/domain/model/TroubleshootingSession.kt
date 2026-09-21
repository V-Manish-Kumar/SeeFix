package com.example.seefix.domain.model

import com.example.seefix.ai.LocationInfo
import kotlinx.serialization.Serializable

@Serializable
data class TroubleshootingSession(
    val id: String,
    val device: HardwareDevice? = null,
    val detectedProblem: String = "",
    val observations: List<String> = emptyList(),
    val currentStepIndex: Int = 0,
    val steps: List<TroubleshootingStep> = emptyList(),
    val availableTools: List<ToolItem> = emptyList(),
    val missingTools: List<ToolItem> = emptyList(),
    val safetyStatus: SafetyStatus = SafetyStatus.SAFE_DEFAULT,
    val confidence: Float = 0f,
    val location: LocationInfo? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0L,
    val statusText: String = "IN PROGRESS",
    val domain: WorkDomain = WorkDomain.APPLIANCE
) {
    val currentStep: TroubleshootingStep?
        get() = steps.getOrNull(currentStepIndex)

    val completedSteps: List<TroubleshootingStep>
        get() = steps.filter { it.isCompleted }

    val isResolved: Boolean
        get() = statusText == "RESOLVED" || (steps.isNotEmpty() && steps.all { it.isCompleted })

    fun toWorkTask(): WorkTask = WorkTask(
        id = id,
        title = device?.name ?: detectedProblem.ifBlank { "Troubleshooting Task" },
        description = detectedProblem,
        domain = domain,
        status = statusText,
        createdAt = timestamp,
        updatedAt = timestamp,
        sessionId = id
    )

    fun toWorkAnalysisResult(): WorkAnalysisResult = WorkAnalysisResult(
        identifiedObject = device?.name ?: "Hardware Unit",
        domain = domain,
        task = detectedProblem,
        observations = observations.mapIndexed { index, obs ->
            Observation(
                id = "obs_$index",
                description = obs,
                source = ObservationSource.AI_INFERENCE,
                timestamp = timestamp,
                confidence = confidence
            )
        },
        detectedIssues = if (detectedProblem.isNotBlank()) listOf(detectedProblem) else emptyList(),
        recommendedActions = steps.map { it.toWorkAction() },
        requiredTools = availableTools,
        requiredParts = missingTools,
        safetyRequirements = if (safetyStatus.level != SafetyLevel.SAFE) {
            listOf(
                SafetyRequirement(
                    hazard = safetyStatus.message,
                    severity = if (safetyStatus.level == SafetyLevel.DANGEROUS_STOP) SafetySeverity.CRITICAL_STOP else SafetySeverity.MEDIUM,
                    precaution = safetyStatus.message
                )
            )
        } else emptyList(),
        confidence = confidence,
        nextAction = currentStep?.toWorkAction()
    )

    companion object {
        fun fromWorkAnalysisResult(
            result: WorkAnalysisResult,
            sessionId: String = "sess_" + System.currentTimeMillis()
        ): TroubleshootingSession {
            val steps = result.recommendedActions.mapIndexed { index, action ->
                TroubleshootingStep.fromWorkAction(index + 1, action)
            }
            val primarySafety = result.safetyRequirements.firstOrNull()
            val safetyStatus = when (primarySafety?.severity) {
                SafetySeverity.CRITICAL_STOP, SafetySeverity.HIGH -> SafetyStatus(
                    level = SafetyLevel.DANGEROUS_STOP,
                    message = primarySafety.precaution.ifBlank { primarySafety.hazard }
                )
                SafetySeverity.MEDIUM -> SafetyStatus(
                    level = SafetyLevel.CAUTION,
                    message = primarySafety.precaution.ifBlank { primarySafety.hazard }
                )
                else -> SafetyStatus.SAFE_DEFAULT
            }

            return TroubleshootingSession(
                id = sessionId,
                device = HardwareDevice(
                    id = "dev_$sessionId",
                    name = result.identifiedObject,
                    model = result.domain.name,
                    category = result.domain.toUserFacingName()
                ),
                detectedProblem = result.task.ifBlank { result.detectedIssues.firstOrNull() ?: "Diagnostic Result" },
                observations = result.observations.map { it.description },
                currentStepIndex = 0,
                steps = steps,
                availableTools = result.requiredTools,
                missingTools = result.requiredParts,
                safetyStatus = safetyStatus,
                confidence = result.confidence,
                domain = result.domain
            )
        }

        fun fromWorkTask(
            task: WorkTask,
            sessionId: String = task.sessionId ?: ("sess_" + System.currentTimeMillis())
        ): TroubleshootingSession = TroubleshootingSession(
            id = sessionId,
            device = HardwareDevice(
                id = "dev_$sessionId",
                name = task.title,
                model = task.domain.name,
                category = task.domain.toUserFacingName()
            ),
            detectedProblem = task.description,
            domain = task.domain,
            statusText = task.status,
            timestamp = task.createdAt
        )
    }
}
