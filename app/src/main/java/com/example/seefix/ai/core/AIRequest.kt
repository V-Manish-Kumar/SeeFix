package com.example.seefix.ai.core

import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.WorkContext

data class SensorDataPayload(
    val accelerometer: FloatArray? = null,
    val gyroscope: FloatArray? = null,
    val compassHeading: Float? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorDataPayload

        if (accelerometer != null) {
            if (other.accelerometer == null) return false
            if (!accelerometer.contentEquals(other.accelerometer)) return false
        } else if (other.accelerometer != null) return false

        if (gyroscope != null) {
            if (other.gyroscope == null) return false
            if (!gyroscope.contentEquals(other.gyroscope)) return false
        } else if (other.gyroscope != null) return false

        if (compassHeading != other.compassHeading) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = accelerometer?.contentHashCode() ?: 0
        result = 31 * result + (gyroscope?.contentHashCode() ?: 0)
        result = 31 * result + (compassHeading?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }
}

data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

data class MachineInfoPayload(
    val deviceName: String,
    val modelNumber: String,
    val category: String
)

data class AIRequest(
    val prompt: String,
    val systemPrompt: String? = null,
    val conversationHistory: List<AIMessage> = emptyList(),
    val imageBytes: ByteArray? = null,
    val sensorData: SensorDataPayload? = null,
    val location: LocationPayload? = null,
    val machineInfo: MachineInfoPayload? = null,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val images: List<AIImage> = emptyList(),
    val videoContext: VideoContext? = null,
    val audioTranscript: String? = null,
    val repairHistory: List<String> = emptyList(),
    val availableTools: List<ToolItem> = emptyList(),
    val availableParts: List<ToolItem> = emptyList(),
    val retrievedKnowledge: List<String> = emptyList(),
    val workContext: WorkContext? = null
) {
    fun computeRequirements(): AIRequirements {
        val videoFrames = videoContext?.selectedFrames ?: emptyList()
        val videoFrameCount = videoFrames.size

        val legacyImageCount = if (imageBytes != null && images.isEmpty()) 1 else 0
        val totalImages = images.size + legacyImageCount + videoFrameCount

        val requiresImg = totalImages > 0
        val requiresMultiImg = totalImages > 1
        val requiresVidFrames = videoFrameCount > 0
        val requiresVid = videoContext?.videoUri != null
        val requiresTools = availableTools.isNotEmpty() || availableParts.isNotEmpty()
        val requiresStruct = workContext != null

        return AIRequirements(
            requiresText = prompt.isNotBlank() || systemPrompt != null || conversationHistory.isNotEmpty(),
            requiresImages = requiresImg,
            requiresMultipleImages = requiresMultiImg,
            requiresAudio = false,
            requiresVideo = requiresVid,
            requiresVideoFrames = requiresVidFrames,
            requiresToolCalling = requiresTools,
            requiresStructuredOutput = requiresStruct,
            requiredImageCount = totalImages
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AIRequest

        if (prompt != other.prompt) return false
        if (systemPrompt != other.systemPrompt) return false
        if (conversationHistory != other.conversationHistory) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (sensorData != other.sensorData) return false
        if (location != other.location) return false
        if (machineInfo != other.machineInfo) return false
        if (maxTokens != other.maxTokens) return false
        if (temperature != other.temperature) return false
        if (topK != other.topK) return false
        if (images != other.images) return false
        if (videoContext != other.videoContext) return false
        if (audioTranscript != other.audioTranscript) return false
        if (repairHistory != other.repairHistory) return false
        if (availableTools != other.availableTools) return false
        if (availableParts != other.availableParts) return false
        if (retrievedKnowledge != other.retrievedKnowledge) return false
        if (workContext != other.workContext) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prompt.hashCode()
        result = 31 * result + (systemPrompt?.hashCode() ?: 0)
        result = 31 * result + conversationHistory.hashCode()
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (sensorData?.hashCode() ?: 0)
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + (machineInfo?.hashCode() ?: 0)
        result = 31 * result + maxTokens
        result = 31 * result + temperature.hashCode()
        result = 31 * result + topK
        result = 31 * result + images.hashCode()
        result = 31 * result + (videoContext?.hashCode() ?: 0)
        result = 31 * result + (audioTranscript?.hashCode() ?: 0)
        result = 31 * result + repairHistory.hashCode()
        result = 31 * result + availableTools.hashCode()
        result = 31 * result + availableParts.hashCode()
        result = 31 * result + retrievedKnowledge.hashCode()
        result = 31 * result + (workContext?.hashCode() ?: 0)
        return result
    }
}
