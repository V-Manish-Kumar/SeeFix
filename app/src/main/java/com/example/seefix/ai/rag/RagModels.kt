package com.example.seefix.ai.rag

import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkDomain
import kotlinx.serialization.Serializable

@Serializable
enum class DocumentType {
    SERVICE_MANUAL,
    USER_MANUAL,
    INSTALLATION_GUIDE,
    TECHNICAL_MANUAL,
    TROUBLESHOOTING_GUIDE,
    REPAIR_GUIDE,
    SAFETY_DOCUMENT,
    SPECIFICATION,
    DATASHEET,
    WIRING_DIAGRAM,
    SCHEMATIC,
    CONFIGURATION_GUIDE,
    INSPECTION_GUIDE,
    TRAINING_DOCUMENT,
    OTHER
}

@Serializable
data class DocumentSection(
    val sectionId: String,
    val sectionTitle: String,
    val content: String,
    val pageNumber: Int? = null,
    val problemKeywords: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val specifications: Map<String, String> = emptyMap(),
    val steps: List<String> = emptyList()
)

@Serializable
data class TechnicalDocument(
    val documentId: String,
    val title: String,
    val source: String,
    val domain: WorkDomain,
    val documentType: DocumentType,
    val manufacturer: String? = null,
    val model: String? = null,
    val equipmentType: String? = null,
    val component: String? = null,
    val language: String = "en",
    val version: String = "1.0",
    val safetyLevel: SafetySeverity = SafetySeverity.LOW,
    val tags: List<String> = emptyList(),
    val sections: List<DocumentSection> = emptyList()
)

@Serializable
data class RagChunk(
    val chunkId: String,
    val documentId: String,
    val documentTitle: String,
    val source: String,
    val sectionTitle: String,
    val pageNumber: Int? = null,
    val content: String,
    val domain: WorkDomain,
    val documentType: DocumentType,
    val equipmentType: String? = null,
    val component: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val relevanceScore: Float = 0f,
    val safetyLevel: SafetySeverity = SafetySeverity.LOW,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class RagQueryContext(
    val query: String,
    val domain: WorkDomain? = null,
    val equipmentType: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val component: String? = null,
    val documentType: DocumentType? = null,
    val maxChunks: Int = 3,
    val safetyFilterOnly: Boolean = false
)

// Legacy compatibility models
@Serializable
data class ManualGuideStep(
    val stepNumber: Int,
    val title: String,
    val instructionText: String,
    val spokenInstruction: String = "",
    val visualVerificationPrompt: String = "",
    val requiredTools: List<ToolItem> = emptyList()
)

@Serializable
data class TroubleshootingGuide(
    val id: String,
    val problemKeywords: List<String>,
    val symptomDescription: String,
    val rootCause: String,
    val safetyLevel: String = "SAFE",
    val steps: List<ManualGuideStep> = emptyList()
)

@Serializable
data class ManualDocument(
    val manualId: String,
    val deviceName: String,
    val category: String,
    val models: List<String> = emptyList(),
    val safetyWarnings: List<String> = emptyList(),
    val specifications: Map<String, String> = emptyMap(),
    val troubleshootingGuides: List<TroubleshootingGuide> = emptyList()
)

@Serializable
data class RagQueryResult(
    val matchingManualTitle: String,
    val manualId: String,
    val relevantSafetyWarnings: List<String>,
    val specifications: Map<String, String>,
    val matchedGuide: TroubleshootingGuide?,
    val recommendedSteps: List<TroubleshootingStep>,
    val confidenceScore: Float
)
