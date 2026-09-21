package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TroubleshootingStep(
    val stepNumber: Int,
    val title: String,
    val instructionText: String,
    val spokenInstruction: String = "",
    val visualVerificationPrompt: String = "",
    val targetBoundingBox: BoundingBox? = null,
    val isCompleted: Boolean = false,
    val isVerified: Boolean = false,
    val requiredTools: List<ToolItem> = emptyList(),
    val requiresSafetyConfirmation: Boolean = false,
    val isSafetyConfirmed: Boolean = false,
    val safetyConfirmationPrompt: String? = null
) {
    fun toWorkAction(): WorkAction = WorkAction(
        id = "action_$stepNumber",
        description = if (title.isNotBlank()) "$title -- $instructionText" else instructionText,
        type = ActionType.REPAIR,
        status = if (isCompleted) "COMPLETED" else "PENDING",
        requiredTools = requiredTools,
        requiredParts = emptyList(),
        safetyRequirements = if (requiresSafetyConfirmation) {
            listOf(
                SafetyRequirement(
                    hazard = safetyConfirmationPrompt ?: "Safety Confirmation Required",
                    severity = SafetySeverity.HIGH,
                    precaution = safetyConfirmationPrompt ?: "Confirm safety conditions before procedure."
                )
            )
        } else emptyList(),
        verificationMethod = visualVerificationPrompt.ifBlank { null }
    )

    companion object {
        fun fromWorkAction(stepNumber: Int, action: WorkAction): TroubleshootingStep {
            val parts = if (action.description.contains(" -- ")) {
                action.description.split(" -- ", limit = 2)
            } else {
                action.description.split(":", limit = 2)
            }
            val title = if (parts.size > 1) parts[0].trim() else action.type.name
            val instruction = if (parts.size > 1) parts[1].trim() else action.description
            val safetyReq = action.safetyRequirements.firstOrNull()

            return TroubleshootingStep(
                stepNumber = stepNumber,
                title = title,
                instructionText = instruction,
                spokenInstruction = instruction,
                visualVerificationPrompt = action.verificationMethod ?: "",
                isCompleted = action.status == "COMPLETED",
                requiredTools = action.requiredTools,
                requiresSafetyConfirmation = action.safetyRequirements.isNotEmpty(),
                safetyConfirmationPrompt = safetyReq?.precaution ?: safetyReq?.hazard
            )
        }
    }
}
