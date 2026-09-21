package com.example.seefix.ai.context

import com.example.seefix.ai.AIAssistantManager
import com.example.seefix.ai.core.AIImage
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.WorkContext

interface WorkContextBuilder {
    suspend fun buildAIRequest(
        userPrompt: String,
        workContext: WorkContext,
        config: WorkContextConfig = WorkContextConfig()
    ): AIRequest
}

class WorkContextBuilderImpl : WorkContextBuilder {

    override suspend fun buildAIRequest(
        userPrompt: String,
        workContext: WorkContext,
        config: WorkContextConfig
    ): AIRequest {
        // 1. Bounded Image Assembly
        val userPhotos = workContext.images.map { AIImage(uri = it) }
        val vContext = workContext.videoContext as? VideoContext
        val videoFrames = vContext?.selectedFrames ?: emptyList()

        val deduplicatedImages = mutableListOf<AIImage>()
        val seenUris = mutableSetOf<String>()

        for (img in userPhotos + videoFrames) {
            val key = img.uri?.takeIf { it.isNotBlank() }
            if (key != null) {
                if (seenUris.add(key)) {
                    deduplicatedImages.add(img)
                }
            } else {
                deduplicatedImages.add(img)
            }
        }

        val boundedImages = deduplicatedImages.take(config.maxTotalImages)

        // 2. Structured System & Context Prompt Assembly
        val systemPromptText = formatSystemPrompt(workContext, config)

        // 3. Constructs AIRequest
        val boundedVideoContext = vContext?.copy(selectedFrames = emptyList())
        val transcript = workContext.audioTranscript ?: vContext?.transcript

        val request = AIRequest(
            prompt = userPrompt,
            systemPrompt = systemPromptText,
            conversationHistory = workContext.conversationHistory.takeLast(config.maxConversationTurns),
            images = boundedImages,
            videoContext = boundedVideoContext,
            audioTranscript = transcript,
            sensorData = workContext.sensorData,
            location = workContext.location,
            machineInfo = workContext.machineInfo,
            repairHistory = workContext.workHistory.take(config.maxHistoryEntries),
            availableTools = workContext.availableTools,
            availableParts = workContext.availableParts,
            retrievedKnowledge = workContext.retrievedKnowledge.take(config.maxRagChunks),
            workContext = workContext
        )

        return request
    }

    private fun formatSystemPrompt(workContext: WorkContext, config: WorkContextConfig): String {
        val sb = StringBuilder()
        sb.appendLine(AIAssistantManager.SYSTEM_PROMPT)
        sb.appendLine()
        sb.appendLine("--- WORK CONTEXT PROVENANCE ---")

        // [TASK & DOMAIN]
        sb.appendLine("[TASK & DOMAIN]")
        val taskTitle = workContext.task?.title ?: "N/A"
        val domainName = workContext.domain.toUserFacingName()
        sb.appendLine("Task: $taskTitle")
        sb.appendLine("Domain: $domainName")
        workContext.task?.description?.let { desc ->
            if (desc.isNotBlank()) {
                sb.appendLine("Task Description: $desc")
            }
        }
        sb.appendLine()

        // [USER OBSERVATIONS]
        if (workContext.observations.isNotEmpty()) {
            sb.appendLine("[USER OBSERVATIONS]")
            workContext.observations.forEach { obs ->
                var text = "- ${obs.description} (Source: ${obs.source})"
                obs.supportingEvidence?.let { ev ->
                    if (ev.isNotBlank()) text += " [Evidence: $ev]"
                }
                sb.appendLine(text)
            }
            sb.appendLine()
        }

        // [AUDIO TRANSCRIPT]
        val transcript = workContext.audioTranscript
            ?: (workContext.videoContext as? VideoContext)?.transcript
        if (!transcript.isNullOrBlank()) {
            sb.appendLine("[AUDIO TRANSCRIPT]")
            sb.appendLine(transcript)
            sb.appendLine()
        }

        // [SENSOR MEASUREMENTS]
        val sensorData = workContext.sensorData
        if (sensorData != null) {
            sb.appendLine("[SENSOR MEASUREMENTS]")
            sensorData.accelerometer?.let { acc ->
                sb.appendLine("Accelerometer: x=${acc.getOrNull(0) ?: 0f}, y=${acc.getOrNull(1) ?: 0f}, z=${acc.getOrNull(2) ?: 0f}")
            }
            sensorData.gyroscope?.let { gyro ->
                sb.appendLine("Gyroscope: x=${gyro.getOrNull(0) ?: 0f}, y=${gyro.getOrNull(1) ?: 0f}, z=${gyro.getOrNull(2) ?: 0f}")
            }
            sensorData.compassHeading?.let { heading ->
                sb.appendLine("Compass Heading: ${heading}°")
            }
            sensorData.metadata.forEach { (key, value) ->
                sb.appendLine("$key: $value")
            }
            sb.appendLine()
        }

        // [LOCATION CONTEXT]
        val location = workContext.location
        if (location != null) {
            sb.appendLine("[LOCATION CONTEXT]")
            if (!location.address.isNullOrBlank()) {
                sb.appendLine("City/Address: ${location.address}")
            }
            sb.appendLine("Coordinates: Lat ${location.latitude}, Lng ${location.longitude}")
            sb.appendLine()
        }

        // [MACHINE METADATA]
        val machineInfo = workContext.machineInfo
        if (machineInfo != null) {
            sb.appendLine("[MACHINE METADATA]")
            sb.appendLine("Device: ${machineInfo.deviceName}")
            sb.appendLine("Model: ${machineInfo.modelNumber}")
            sb.appendLine("Category: ${machineInfo.category}")
            sb.appendLine()
        }

        // [SERVICE HISTORY]
        val boundedHistory = workContext.workHistory.take(config.maxHistoryEntries)
        if (boundedHistory.isNotEmpty()) {
            sb.appendLine("[SERVICE HISTORY]")
            boundedHistory.forEachIndexed { idx, entry ->
                sb.appendLine("${idx + 1}. $entry")
            }
            sb.appendLine()
        }

        // [AVAILABLE TOOLS & PARTS]
        if (workContext.availableTools.isNotEmpty() || workContext.availableParts.isNotEmpty()) {
            sb.appendLine("[AVAILABLE TOOLS & PARTS]")
            if (workContext.availableTools.isNotEmpty()) {
                val tools = workContext.availableTools.joinToString(", ") { "${it.name}${if (!it.isAvailable) " (Unavailable)" else ""}" }
                sb.appendLine("Tools: $tools")
            }
            if (workContext.availableParts.isNotEmpty()) {
                val parts = workContext.availableParts.joinToString(", ") { "${it.name}${if (!it.isAvailable) " (Unavailable)" else ""}" }
                sb.appendLine("Parts: $parts")
            }
            sb.appendLine()
        }

        // [RETRIEVED MANUAL KNOWLEDGE]
        val boundedRag = workContext.retrievedKnowledge.take(config.maxRagChunks)
        if (boundedRag.isNotEmpty()) {
            sb.appendLine("[RETRIEVED MANUAL KNOWLEDGE]")
            boundedRag.forEachIndexed { idx, chunk ->
                sb.appendLine("Chunk ${idx + 1}: $chunk")
            }
            sb.appendLine()
        }

        // [SAFETY CONSTRAINTS]
        val highSeveritySafety = workContext.safetyContext.filter {
            it.severity == SafetySeverity.HIGH || it.severity == SafetySeverity.CRITICAL_STOP
        }
        if (highSeveritySafety.isNotEmpty()) {
            sb.appendLine("[SAFETY CONSTRAINTS]")
            highSeveritySafety.forEach { safety ->
                var line = "HAZARD: ${safety.hazard} [Severity: ${safety.severity}] - Precaution: ${safety.precaution}"
                if (safety.ppe.isNotEmpty()) {
                    line += " (PPE: ${safety.ppe.joinToString(", ")})"
                }
                if (!safety.stopCondition.isNullOrBlank()) {
                    line += " [Stop Condition: ${safety.stopCondition}]"
                }
                sb.appendLine(line)
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }
}
