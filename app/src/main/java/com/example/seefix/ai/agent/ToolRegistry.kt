package com.example.seefix.ai.agent

import java.util.concurrent.ConcurrentHashMap

class ToolRegistry(
    initialTools: List<SeeFixTool> = listOf(
        LocationTool(),
        StoreSearchTool(),
        SensorTool(),
        RAGSearchTool(),
        HistorySearchTool(),
        DocumentTool()
    )
) {
    private val toolsMap = ConcurrentHashMap<String, SeeFixTool>()

    init {
        initialTools.forEach { registerTool(it) }
    }

    fun registerTool(tool: SeeFixTool) {
        toolsMap[tool.name] = tool
    }

    fun unregisterTool(name: String) {
        toolsMap.remove(name)
    }

    fun getTool(name: String): SeeFixTool? {
        return toolsMap[name]
    }

    fun hasTool(name: String): Boolean {
        return toolsMap.containsKey(name)
    }

    fun getAllTools(): List<SeeFixTool> {
        return toolsMap.values.toList()
    }

    suspend fun validateAndExecute(name: String, arguments: Map<String, String>): ToolResult {
        val tool = toolsMap[name]
            ?: return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = "Unknown tool: $name"
            )

        val validation = tool.validate(arguments)
        if (!validation.isValid) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = validation.errorMessage ?: "Invalid arguments for tool $name"
            )
        }

        return tool.execute(arguments)
    }
}
