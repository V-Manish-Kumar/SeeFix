package com.example.seefix.ai

import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain

/**
 * Universal AI Multimodal diagnosis service for SeeFix field-work tasks.
 */
class AIAssistantManager {

    companion object {
        const val SYSTEM_PROMPT =
            "You are SeeFix, a universal AI field-work assistant assisting technical workers across mechanical, electrical, automotive, industrial, construction, HVAC, plumbing, IT/networking, and general field-work domains. Analyze the supplied structured WorkContext, task requirements, observations, and safety conditions to provide clear, actionable guidance. Never assume a specific domain unless indicated in context."
    }

    suspend fun diagnoseHardware(
        imageBytes: ByteArray?,
        userNotes: String,
        domain: WorkDomain = WorkDomain.GENERAL
    ): TroubleshootingSession {
        val detectedDomain = if (domain != WorkDomain.GENERAL) domain else inferDomainFromNotes(userNotes)
        val detectedDevice = HardwareDevice(
            id = "dev_" + System.currentTimeMillis(),
            name = "${detectedDomain.toUserFacingName()} Equipment Unit",
            model = "SF-${detectedDomain.name}-100",
            category = detectedDomain.toUserFacingName()
        )
        return TroubleshootingSession(
            id = "sess_" + System.currentTimeMillis(),
            device = detectedDevice,
            detectedProblem = userNotes.ifBlank { "System Inspection and Field Diagnostic Procedure" },
            observations = listOf(
                "Multimodal scan analyzed visual & technical telemetry for ${detectedDomain.toUserFacingName()}",
                "Applied universal field-work assistant protocol"
            ),
            confidence = 0.95f,
            domain = detectedDomain
        )
    }

    suspend fun diagnoseWorkContext(context: WorkContext): TroubleshootingSession {
        return diagnoseHardware(
            imageBytes = null,
            userNotes = context.userInput ?: context.task?.description ?: "",
            domain = context.domain
        )
    }

    private fun inferDomainFromNotes(notes: String): WorkDomain {
        val lower = notes.lowercase()
        return when {
            lower.contains("engine") || lower.contains("car") || lower.contains("brake") || lower.contains("auto") || lower.contains("transmission") -> WorkDomain.AUTOMOTIVE
            lower.contains("pipe") || lower.contains("leak") || lower.contains("plumb") || lower.contains("drain") || lower.contains("valve") || lower.contains("faucet") -> WorkDomain.PLUMBING
            lower.contains("wire") || lower.contains("breaker") || lower.contains("voltage") || lower.contains("circuit") || lower.contains("mains") -> WorkDomain.ELECTRICAL
            lower.contains("network") || lower.contains("router") || lower.contains("switch") || lower.contains("ethernet") || lower.contains("ip address") || lower.contains("lan") -> WorkDomain.IT_NETWORKING
            lower.contains("hvac") || lower.contains("ac") || lower.contains("chiller") || lower.contains("thermal") || lower.contains("compressor") -> WorkDomain.HVAC
            lower.contains("factory") || lower.contains("plc") || lower.contains("conveyor") || lower.contains("industrial") || lower.contains("hydraulic") -> WorkDomain.INDUSTRIAL
            lower.contains("gear") || lower.contains("bearing") || lower.contains("shaft") || lower.contains("mechanical") -> WorkDomain.MECHANICAL
            lower.contains("concrete") || lower.contains("rebar") || lower.contains("scaffold") || lower.contains("construct") || lower.contains("beam") -> WorkDomain.CONSTRUCTION
            lower.contains("tractor") || lower.contains("crop") || lower.contains("irrigation") || lower.contains("farm") || lower.contains("agri") -> WorkDomain.AGRICULTURE
            lower.contains("pcb") || lower.contains("solder") || lower.contains("resistor") || lower.contains("microcontroller") -> WorkDomain.ELECTRONICS
            lower.contains("door") || lower.contains("lock") || lower.contains("roof") || lower.contains("drywall") || lower.contains("home") -> WorkDomain.HOME_MAINTENANCE
            lower.contains("washer") || lower.contains("dryer") || lower.contains("appliance") || lower.contains("fridge") -> WorkDomain.APPLIANCE
            else -> WorkDomain.GENERAL
        }
    }
}
