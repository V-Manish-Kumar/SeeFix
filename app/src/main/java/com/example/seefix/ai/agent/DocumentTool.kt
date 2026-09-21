package com.example.seefix.ai.agent

import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.rag.RagQueryContext
import kotlinx.serialization.json.Json

class DocumentTool(
    private val ragKnowledgeEngine: RagKnowledgeEngine = RagKnowledgeEngine()
) : SeeFixTool {
    override val name: String = "document_tool"
    override val description: String = "Fetches specific manual page or schematic document."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = listOf("documentId"),
        optionalParameters = listOf("docId", "query", "pageNumber"),
        description = "Retrieve document page"
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun containsPathTraversal(value: String): Boolean {
        val lower = value.lowercase()
        return value.contains("..") ||
                value.contains("/") ||
                lower.contains("/sdcard") ||
                lower.contains("/data") ||
                lower.contains("/system") ||
                lower.contains("file://")
    }

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        for (value in arguments.values) {
            if (containsPathTraversal(value)) {
                return ToolValidationResult(
                    isValid = false,
                    errorMessage = "PATH_TRAVERSAL_REJECTED: Arbitrary file path access is strictly prohibited."
                )
            }
        }

        val docId = arguments["documentId"] ?: arguments["docId"]
        val query = arguments["query"]
        if (docId.isNullOrBlank() && query.isNullOrBlank()) {
            return ToolValidationResult(
                isValid = false,
                errorMessage = "Missing required parameter 'documentId' or 'query'"
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

        val docId = arguments["documentId"] ?: arguments["docId"]
        val query = arguments["query"]

        val allDocs = ragKnowledgeEngine.getIndexedDocuments()
        var matchedDoc = if (!docId.isNullOrBlank()) {
            allDocs.find { it.documentId.equals(docId, ignoreCase = true) }
        } else null

        if (matchedDoc == null && !query.isNullOrBlank()) {
            matchedDoc = allDocs.find {
                it.title.contains(query, ignoreCase = true) ||
                        it.documentId.contains(query, ignoreCase = true)
            } ?: run {
                val chunks = ragKnowledgeEngine.retrieve(RagQueryContext(query = query, maxChunks = 1))
                val topChunk = chunks.firstOrNull()
                topChunk?.let { chunk -> allDocs.find { it.documentId == chunk.documentId } }
            }
        }

        if (matchedDoc == null) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = "Document not found for parameters: $arguments"
            )
        }

        val sectionsJsonList = matchedDoc.sections.map { section ->
            mapOf(
                "sectionId" to section.sectionId,
                "sectionTitle" to section.sectionTitle,
                "content" to section.content,
                "pageNumber" to section.pageNumber.toString()
            )
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "documentId" to matchedDoc.documentId,
                "title" to matchedDoc.title,
                "source" to matchedDoc.source,
                "sectionCount" to matchedDoc.sections.size.toString(),
                "sectionsJson" to json.encodeToString(sectionsJsonList)
            )
        )
    }
}
