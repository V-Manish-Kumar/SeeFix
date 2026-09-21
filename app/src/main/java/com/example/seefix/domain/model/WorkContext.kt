package com.example.seefix.domain.model

import com.example.seefix.ai.core.AIMessage
import com.example.seefix.ai.core.LocationPayload
import com.example.seefix.ai.core.MachineInfoPayload
import com.example.seefix.ai.core.SensorDataPayload

data class WorkContext(
    val task: WorkTask? = null,
    val domain: WorkDomain = WorkDomain.GENERAL,
    val userInput: String? = null,
    val conversationHistory: List<AIMessage> = emptyList(),
    val machineInfo: MachineInfoPayload? = null,
    val images: List<String> = emptyList(),
    val videoContext: Any? = null,
    val audioTranscript: String? = null,
    val sensorData: SensorDataPayload? = null,
    val location: LocationPayload? = null,
    val workHistory: List<String> = emptyList(),
    val availableTools: List<ToolItem> = emptyList(),
    val availableParts: List<ToolItem> = emptyList(),
    val retrievedKnowledge: List<String> = emptyList(),
    val completedSteps: List<WorkAction> = emptyList(),
    val observations: List<Observation> = emptyList(),
    val safetyContext: List<SafetyRequirement> = emptyList()
)
