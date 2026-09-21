package com.example.seefix.ai.agent

interface SeeFixTool {
    val name: String
    val description: String
    val permission: ToolPermission
    val inputSchema: ToolInputSchema

    suspend fun validate(arguments: Map<String, String>): ToolValidationResult
    suspend fun execute(arguments: Map<String, String>): ToolResult
}
