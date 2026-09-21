package com.example.seefix.ai.agent

import com.example.seefix.domain.model.WorkContext
import kotlinx.coroutines.withTimeoutOrNull

interface ToolExecutor {
    suspend fun execute(
        request: ToolRequest,
        workContext: WorkContext,
        userConfirmedByUI: Boolean = false
    ): ToolResult
}

class ToolExecutorImpl(
    private val toolRegistry: ToolRegistry,
    private val permissionPolicy: PermissionPolicy = PermissionPolicy()
) : ToolExecutor {

    override suspend fun execute(
        request: ToolRequest,
        workContext: WorkContext,
        userConfirmedByUI: Boolean
    ): ToolResult {
        val tool = toolRegistry.getTool(request.toolName)
            ?: return ToolResult(
                toolName = request.toolName,
                isSuccess = false,
                errorMessage = "Unknown tool: ${request.toolName}"
            )

        // Ignore any AI-supplied userConfirmed or userConfirmedByUI parameter in request.arguments
        val sanitizedArguments = request.arguments.filterKeys { key ->
            !key.equals("userConfirmed", ignoreCase = true) && !key.equals("userConfirmedByUI", ignoreCase = true)
        }

        val validation = tool.validate(sanitizedArguments)
        if (!validation.isValid) {
            return ToolResult(
                toolName = request.toolName,
                isSuccess = false,
                errorMessage = validation.errorMessage ?: "Invalid arguments for tool ${request.toolName}"
            )
        }

        if (permissionPolicy.requiresUserConfirmation(tool, sanitizedArguments) && !userConfirmedByUI) {
            return ToolResult(
                toolName = request.toolName,
                isSuccess = false,
                errorMessage = "CONFIRMATION_REQUIRED: Action requires explicit user confirmation."
            )
        }

        val result = withTimeoutOrNull(5000L) {
            tool.execute(sanitizedArguments)
        }

        return result ?: ToolResult(
            toolName = request.toolName,
            isSuccess = false,
            errorMessage = "Tool execution timed out after 5000ms"
        )
    }
}
