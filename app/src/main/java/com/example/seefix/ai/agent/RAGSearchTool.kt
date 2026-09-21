package com.example.seefix.ai.agent

import com.example.seefix.ai.rag.DocumentType
import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.rag.RagQueryContext
import com.example.seefix.domain.model.WorkDomain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RAGSearchTool(
    private val ragKnowledgeEngine: RagKnowledgeEngine = RagKnowledgeEngine()
) : SeeFixTool {
    override val name: String = "rag_search_tool"
    override val description: String = "Retrieves technical manual sections and troubleshooting documentation."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = listOf("query"),
        optionalParameters = listOf("domain", "equipmentType", "component", "documentType", "maxChunks"),
        description = "Search RAG knowledge base"
    )

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        val query = arguments["query"]
        if (query.isNullOrBlank()) {
            return ToolValidationResult(
                isValid = false,
                errorMessage = "Missing required parameter 'query'"
            )
        }
        return ToolValidationResult(isValid = true)
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val validation = validate(arguments)
        if (!validation.isValid) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = validation.errorMessage
            )
        }

        val query = arguments["query"] ?: ""
        val domainArg = arguments["domain"]?.let { d ->
            WorkDomain.entries.find { it.name.equals(d, ignoreCase = true) }
        }
        val eqType = arguments["equipmentType"]
        val component = arguments["component"]
        val docType = arguments["documentType"]?.let { dt ->
            DocumentType.entries.find { it.name.equals(dt, ignoreCase = true) }
        }
        val maxChunks = arguments["maxChunks"]?.toIntOrNull() ?: 3

        val queryContext = RagQueryContext(
            query = query,
            domain = domainArg,
            equipmentType = eqType,
            component = component,
            documentType = docType,
            maxChunks = maxChunks
        )

        val chunks = ragKnowledgeEngine.retrieve(queryContext)
        val topChunk = chunks.firstOrNull()

        val chunksJsonList = chunks.map { chunk ->
            mapOf(
                "chunkId" to chunk.chunkId,
                "documentTitle" to chunk.documentTitle,
                "sectionTitle" to chunk.sectionTitle,
                "content" to chunk.content,
                "relevanceScore" to chunk.relevanceScore.toString()
            )
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "chunkCount" to chunks.size.toString(),
                "chunksJson" to json.encodeToString(chunksJsonList),
                "topTitle" to (topChunk?.documentTitle ?: "No match"),
                "topContent" to (topChunk?.content ?: "")
            )
        )
    }
}
