package com.example.seefix.ai.agent

import com.example.seefix.ai.context.WorkContextBuilder
import com.example.seefix.ai.core.AIError
import com.example.seefix.ai.core.AIErrorCode
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.domain.model.ActionType
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface SeeFixAgent {
    suspend fun process(
        workContext: WorkContext,
        currentState: AgentState,
        userConfirmed: Boolean = false
    ): AgentResult
}

class SeeFixAgentImpl(
    private val aiRouter: AIRouter,
    private val workContextBuilder: WorkContextBuilder,
    private val toolRegistry: ToolRegistry,
    private val preferenceManager: AIPreferenceManager,
    private val toolExecutor: ToolExecutor = ToolExecutorImpl(toolRegistry)
) : SeeFixAgent {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun process(
        workContext: WorkContext,
        currentState: AgentState,
        userConfirmed: Boolean
    ): AgentResult {
        // 1. Safety Boundary Check
        val criticalSafetyRequirement = workContext.safetyContext.firstOrNull {
            it.severity == SafetySeverity.CRITICAL_STOP
        }
        if (criticalSafetyRequirement != null) {
            val stopReason = "Critical safety hazard detected: ${criticalSafetyRequirement.hazard}. Precaution: ${criticalSafetyRequirement.precaution}"
            val decision = AgentDecision(
                actionType = AgentActionType.STOP,
                reason = stopReason,
                confidence = 1.0f
            )
            val updatedState = currentState.copy(
                status = AgentStatus.STOPPED,
                errors = currentState.errors + stopReason
            )
            return AgentResult(
                state = updatedState,
                decision = decision,
                explanation = "Critical safety hazard detected in safety context. Stopping agent operation immediately.",
                isCompleted = false
            )
        }

        // 2. Iteration Bound Check
        if (currentState.iterationCount >= currentState.maxIterations) {
            val stopReason = "Maximum iteration limit reached (${currentState.maxIterations})"
            val decision = AgentDecision(
                actionType = AgentActionType.STOP,
                reason = stopReason,
                confidence = 1.0f
            )
            val updatedState = currentState.copy(
                status = AgentStatus.STOPPED,
                errors = currentState.errors + stopReason
            )
            return AgentResult(
                state = updatedState,
                decision = decision,
                explanation = "Maximum iteration limit reached",
                isCompleted = false
            )
        }

        // 3. Assemble AIRequest via WorkContextBuilder
        val promptText = buildAgentPrompt(workContext, currentState)
        val aiRequest = workContextBuilder.buildAIRequest(
            userPrompt = promptText,
            workContext = workContext
        )

        // 4. Invoke AIRouter
        val aiResponse = try {
            aiRouter.generate(aiRequest, preferenceManager.getAIMode())
        } catch (e: Exception) {
            val aiError = AIError(AIErrorCode.INFERENCE_FAILED, e.message ?: "AI generation failed", e)
            val decision = AgentDecision(
                actionType = AgentActionType.STOP,
                reason = "AI inference exception: ${e.message}",
                confidence = 0.0f
            )
            val updatedState = currentState.copy(
                status = AgentStatus.ERROR,
                iterationCount = currentState.iterationCount + 1,
                errors = currentState.errors + (e.message ?: "AI generation exception")
            )
            return AgentResult(
                state = updatedState,
                decision = decision,
                explanation = "AI Router invocation failed.",
                isCompleted = false,
                error = aiError
            )
        }

        if (aiResponse.error != null) {
            val decision = AgentDecision(
                actionType = AgentActionType.STOP,
                reason = aiResponse.error.message,
                confidence = 0.0f
            )
            val updatedState = currentState.copy(
                status = AgentStatus.ERROR,
                iterationCount = currentState.iterationCount + 1,
                errors = currentState.errors + aiResponse.error.message
            )
            return AgentResult(
                state = updatedState,
                decision = decision,
                explanation = "AI provider returned error: ${aiResponse.error.message}",
                isCompleted = false,
                error = aiResponse.error
            )
        }

        // 5. Parse AIResponse into AgentDecision
        val parsedDecision = parseDecision(aiResponse.text)

        // Handle tool request decision
        if (parsedDecision.actionType == AgentActionType.REQUEST_TOOL) {
            val toolReq = parsedDecision.toolRequest ?: ToolRequest(toolName = "", reason = parsedDecision.reason)
            val toolResult = toolExecutor.execute(toolReq, workContext, userConfirmedByUI = userConfirmed)

            if (toolResult.errorMessage?.startsWith("Unknown tool") == true) {
                val errorMsg = "Unknown tool requested: ${toolReq.toolName}"
                val fallbackDecision = AgentDecision(
                    actionType = AgentActionType.ASK_USER,
                    userQuestion = "Tool '${toolReq.toolName}' is not recognized. How would you like to proceed?",
                    reason = errorMsg,
                    confidence = 0.5f
                )
                val updatedState = currentState.copy(
                    status = AgentStatus.ERROR,
                    toolResults = currentState.toolResults + toolResult,
                    iterationCount = currentState.iterationCount + 1,
                    errors = currentState.errors + errorMsg
                )
                return AgentResult(
                    state = updatedState,
                    decision = fallbackDecision,
                    explanation = errorMsg,
                    isCompleted = false
                )
            }

            val isConfirmationRequired = toolResult.errorMessage?.contains("CONFIRMATION_REQUIRED") == true
            val nextStatus = if (isConfirmationRequired) {
                AgentStatus.WAITING_FOR_USER
            } else {
                AgentStatus.WAITING_FOR_TOOL
            }

            val nextIteration = currentState.iterationCount + 1
            val updatedState = currentState.copy(
                status = nextStatus,
                toolResults = currentState.toolResults + toolResult,
                iterationCount = nextIteration
            )

            return AgentResult(
                state = updatedState,
                decision = parsedDecision,
                explanation = parsedDecision.reason.ifBlank { "Tool ${toolReq.toolName} requested and executed." },
                isCompleted = false
            )
        }

        // Map AgentActionType to AgentStatus for non-tool decisions
        val nextStatus = when (parsedDecision.actionType) {
            AgentActionType.OBSERVE -> AgentStatus.ANALYZING
            AgentActionType.RETRIEVE_KNOWLEDGE -> AgentStatus.ANALYZING
            AgentActionType.ASK_USER -> AgentStatus.WAITING_FOR_USER
            AgentActionType.PROPOSE_ACTION -> AgentStatus.ACTION_PROPOSED
            AgentActionType.COMPLETE -> AgentStatus.COMPLETED
            AgentActionType.STOP -> AgentStatus.STOPPED
            else -> AgentStatus.ANALYZING
        }

        val nextIteration = currentState.iterationCount + 1
        val isCompleted = (parsedDecision.actionType == AgentActionType.COMPLETE)

        val updatedState = currentState.copy(
            status = nextStatus,
            pendingAction = parsedDecision.proposedAction ?: currentState.pendingAction,
            iterationCount = nextIteration
        )

        return AgentResult(
            state = updatedState,
            decision = parsedDecision,
            explanation = parsedDecision.reason.ifBlank { "Agent determined next action: ${parsedDecision.actionType}" },
            isCompleted = isCompleted
        )
    }

    private fun buildAgentPrompt(workContext: WorkContext, currentState: AgentState): String {
        val userQuery = workContext.userInput ?: currentState.currentTask?.title ?: "Troubleshooting"
        return """
            Current iteration: ${currentState.iterationCount + 1} of ${currentState.maxIterations}
            Current agent status: ${currentState.status}
            User query/context: $userQuery
            
            Determine the next agent action type (OBSERVE, RETRIEVE_KNOWLEDGE, REQUEST_TOOL, ASK_USER, PROPOSE_ACTION, COMPLETE, STOP).
            Output JSON format:
            {
              "actionType": "PROPOSE_ACTION",
              "reason": "Detailed explanation",
              "confidence": 0.9,
              "toolName": "optional_tool_name",
              "toolArguments": {},
              "userQuestion": "optional user question",
              "proposedActionDescription": "optional proposed action description"
            }
        """.trimIndent()
    }

    internal fun parseDecision(text: String): AgentDecision {
        if (text.isBlank()) {
            return AgentDecision(
                actionType = AgentActionType.OBSERVE,
                reason = "Received empty response from AI engine."
            )
        }

        // 1. Try JSON parsing
        try {
            val jsonStart = text.indexOf('{')
            val jsonEnd = text.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonSubstring = text.substring(jsonStart, jsonEnd + 1)
                val dto = json.decodeFromString<AgentDecisionDto>(jsonSubstring)

                val actionType = try {
                    AgentActionType.valueOf(dto.actionType.uppercase())
                } catch (_: Exception) {
                    null
                }

                if (actionType != null) {
                    val toolReq = if (!dto.toolName.isNullOrBlank()) {
                        ToolRequest(
                            toolName = dto.toolName,
                            arguments = dto.toolArguments ?: emptyMap(),
                            reason = dto.reason ?: ""
                        )
                    } else null

                    val proposedAction = if (!dto.proposedActionDescription.isNullOrBlank()) {
                        WorkAction(
                            id = "act_" + System.currentTimeMillis(),
                            description = dto.proposedActionDescription,
                            type = ActionType.INSPECT
                        )
                    } else null

                    return AgentDecision(
                        actionType = actionType,
                        toolRequest = toolReq,
                        userQuestion = dto.userQuestion,
                        proposedAction = proposedAction,
                        reason = dto.reason ?: "",
                        confidence = dto.confidence ?: 1.0f
                    )
                }
            }
        } catch (_: Exception) {
            // Fallthrough to text parsing
        }

        // 2. Text parsing fallbacks
        val upperText = text.uppercase()

        return when {
            upperText.contains("CRITICAL_STOP") || (upperText.contains("STOP") && !upperText.contains("REQUEST_TOOL")) -> {
                AgentDecision(
                    actionType = AgentActionType.STOP,
                    reason = text,
                    confidence = 1.0f
                )
            }
            upperText.contains("REQUEST_TOOL") || upperText.contains("TOOL_REQUEST") -> {
                val toolName = extractToolNameFromText(text)
                AgentDecision(
                    actionType = AgentActionType.REQUEST_TOOL,
                    toolRequest = ToolRequest(toolName = toolName, reason = text),
                    reason = text,
                    confidence = 0.9f
                )
            }
            upperText.contains("ASK_USER") || upperText.contains("USER_QUESTION") -> {
                AgentDecision(
                    actionType = AgentActionType.ASK_USER,
                    userQuestion = text,
                    reason = text,
                    confidence = 0.9f
                )
            }
            upperText.contains("RETRIEVE_KNOWLEDGE") || upperText.contains("RAG_SEARCH") -> {
                AgentDecision(
                    actionType = AgentActionType.RETRIEVE_KNOWLEDGE,
                    reason = text,
                    confidence = 0.9f
                )
            }
            upperText.contains("PROPOSE_ACTION") || upperText.contains("WORK_ACTION") -> {
                AgentDecision(
                    actionType = AgentActionType.PROPOSE_ACTION,
                    proposedAction = WorkAction(
                        id = "act_proposed",
                        description = text.take(100),
                        type = ActionType.INSPECT
                    ),
                    reason = text,
                    confidence = 0.9f
                )
            }
            upperText.contains("COMPLETE") || upperText.contains("RESOLVED") -> {
                AgentDecision(
                    actionType = AgentActionType.COMPLETE,
                    reason = text,
                    confidence = 1.0f
                )
            }
            upperText.contains("OBSERVE") -> {
                AgentDecision(
                    actionType = AgentActionType.OBSERVE,
                    reason = text,
                    confidence = 0.8f
                )
            }
            else -> {
                AgentDecision(
                    actionType = AgentActionType.PROPOSE_ACTION,
                    proposedAction = WorkAction(
                        id = "act_default",
                        description = text.take(100),
                        type = ActionType.INSPECT
                    ),
                    reason = text,
                    confidence = 0.7f
                )
            }
        }
    }

    private fun extractToolNameFromText(text: String): String {
        val knownTools = listOf(
            "location_tool",
            "store_search_tool",
            "sensor_tool",
            "rag_search_tool",
            "history_search_tool",
            "document_tool",
            "execute_shell_command"
        )
        for (tool in knownTools) {
            if (text.contains(tool, ignoreCase = true)) {
                return tool
            }
        }
        return "unknown_tool"
    }
}

@Serializable
private data class AgentDecisionDto(
    val actionType: String,
    val toolName: String? = null,
    val toolArguments: Map<String, String>? = null,
    val userQuestion: String? = null,
    val proposedActionDescription: String? = null,
    val reason: String? = null,
    val confidence: Float? = null
)
