package com.example.seefix.ai.agent

import com.example.seefix.data.repository.SessionHistoryRepositoryImpl
import com.example.seefix.domain.repository.SessionHistoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class HistorySearchTool(
    private val sessionHistoryRepository: SessionHistoryRepository = SessionHistoryRepositoryImpl()
) : SeeFixTool {
    override val name: String = "history_search_tool"
    override val description: String = "Searches past troubleshooting session history."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = emptyList(),
        optionalParameters = listOf("query", "domain", "deviceName", "maxResults"),
        description = "Search past repairs"
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        return ToolValidationResult(isValid = true)
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val query = arguments["query"]
        val domainArg = arguments["domain"]
        val deviceNameArg = arguments["deviceName"]
        val maxResults = arguments["maxResults"]?.toIntOrNull() ?: 5

        val allSessions = try {
            sessionHistoryRepository.getHistory().first()
        } catch (_: Exception) {
            emptyList()
        }

        val filtered = allSessions.filter { session ->
            val matchesQuery = query.isNullOrBlank() ||
                    session.detectedProblem.contains(query, ignoreCase = true) ||
                    (session.device?.name?.contains(query, ignoreCase = true) == true) ||
                    session.observations.any { it.contains(query, ignoreCase = true) }

            val matchesDomain = domainArg.isNullOrBlank() ||
                    session.domain.name.equals(domainArg, ignoreCase = true)

            val matchesDevice = deviceNameArg.isNullOrBlank() ||
                    (session.device?.name?.contains(deviceNameArg, ignoreCase = true) == true)

            matchesQuery && matchesDomain && matchesDevice
        }.take(maxResults)

        val latest = filtered.firstOrNull()

        val historyJsonList = filtered.map { session ->
            mapOf(
                "id" to session.id,
                "deviceName" to (session.device?.name ?: "Unknown"),
                "problem" to session.detectedProblem,
                "statusText" to session.statusText,
                "timestamp" to session.timestamp.toString()
            )
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "historyCount" to filtered.size.toString(),
                "historyJson" to json.encodeToString(historyJsonList),
                "latestSessionDevice" to (latest?.device?.name ?: "None"),
                "latestSessionStatus" to (latest?.statusText ?: "UNKNOWN")
            )
        )
    }
}
