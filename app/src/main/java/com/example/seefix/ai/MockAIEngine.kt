package com.example.seefix.ai

import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.domain.model.ActionType
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.StoreLocation
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkAnalysisResult
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import com.example.seefix.ai.core.AICapabilities
import kotlinx.coroutines.delay

/**
 * Offline AI Provider delivering domain-generalized field-work multimodal responses
 * powered by local RAG manual knowledge and DemoScenarios.
 */
class MockAIEngine(
    private val ragEngine: RagKnowledgeEngine = RagKnowledgeEngine()
) : MultimodalAIEngine {

    fun getCapabilities(): AICapabilities {
        return AICapabilities(
            supportsText = true,
            supportsImages = true,
            supportsMultipleImages = true,
            supportsAudio = true,
            supportsVideo = false,
            supportsVideoFrames = true,
            supportsToolCalling = true,
            supportsStructuredOutput = true,
            maxContextTokens = 8192,
            maxImages = 10
        )
    }

    companion object {
        const val SYSTEM_PROMPT =
            "You are SeeFix, a universal AI field-work assistant assisting technical workers across mechanical, electrical, automotive, industrial, construction, HVAC, plumbing, IT/networking, and general field-work domains. Analyze the supplied structured WorkContext, task requirements, observations, and safety conditions to provide clear, actionable guidance. Never assume a specific domain unless indicated in context."
    }

    override suspend fun analyzeEquipment(
        imageBytes: ByteArray?,
        userVoicePrompt: String
    ): AIAnalysisResult {
        delay(500)

        val inferredDomain = inferDomainFromPrompt(userVoicePrompt)

        // If query matches preloaded demo scenarios (Washing machine, Breaker, HVAC), use them
        val lowerPrompt = userVoicePrompt.lowercase()
        val isPreloadedScenario = lowerPrompt.contains("washer") || lowerPrompt.contains("washing") ||
                lowerPrompt.contains("breaker") || lowerPrompt.contains("panel") ||
                lowerPrompt.contains("hvac") || lowerPrompt.contains("split")

        if (isPreloadedScenario) {
            val scenario = DemoScenarios.getScenarioForAppliance(userVoicePrompt)
            val ragResult = ragEngine.queryKnowledge(userVoicePrompt, scenario.device?.name)
            val steps = if (scenario.steps.isNotEmpty()) scenario.steps else ragResult.recommendedSteps
            val requiredTools = steps.flatMap { it.requiredTools }.distinctBy { it.id }

            val observations = listOf(
                "Detected visual match for ${scenario.device?.name} (${scenario.device?.model})",
                "Domain classified as ${scenario.domain.toUserFacingName()}",
                "RAG manual confidence score: ${(scenario.confidence * 100).toInt()}%",
                "Matched problem: ${scenario.detectedProblem}"
            )

            return AIAnalysisResult(
                device = scenario.device,
                problem = scenario.detectedProblem,
                observations = observations,
                steps = steps,
                requiredTools = requiredTools,
                confidence = scenario.confidence,
                safetyStatus = scenario.safetyStatus
            )
        }

        // Domain-generalized dynamic generation
        val analysisResult = generateDomainAnalysis(inferredDomain, userVoicePrompt)
        val device = HardwareDevice(
            id = "dev_${inferredDomain.name.lowercase()}",
            name = analysisResult.identifiedObject,
            model = "SF-${inferredDomain.name}-PRO",
            category = inferredDomain.toUserFacingName()
        )

        return AIAnalysisResult(
            device = device,
            problem = analysisResult.task,
            observations = analysisResult.observations.map { it.description },
            steps = analysisResult.recommendedActions.mapIndexed { idx, action ->
                TroubleshootingStep.fromWorkAction(idx + 1, action)
            },
            requiredTools = analysisResult.requiredTools,
            confidence = analysisResult.confidence,
            safetyStatus = if (analysisResult.safetyRequirements.isNotEmpty()) {
                val primary = analysisResult.safetyRequirements.first()
                SafetyStatus(
                    level = if (primary.severity == SafetySeverity.CRITICAL_STOP || primary.severity == SafetySeverity.HIGH)
                        SafetyLevel.DANGEROUS_STOP else SafetyLevel.CAUTION,
                    message = primary.precaution
                )
            } else SafetyStatus.SAFE_DEFAULT
        )
    }

    suspend fun analyzeWorkContext(context: WorkContext): WorkAnalysisResult {
        delay(400)
        val domain = if (context.domain != WorkDomain.GENERAL) context.domain
        else inferDomainFromPrompt(context.userInput ?: context.task?.description ?: "")
        val prompt = context.userInput ?: context.task?.description ?: "Field inspection"
        return generateDomainAnalysis(domain, prompt)
    }

    fun inferDomainFromPrompt(prompt: String): WorkDomain {
        val lower = prompt.lowercase()
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

    fun generateDomainAnalysis(domain: WorkDomain, prompt: String): WorkAnalysisResult {
        val domainName = domain.toUserFacingName()
        val taskText = prompt.ifBlank { "$domainName Equipment Field Inspection" }

        val observations = listOf(
            Observation(
                id = "obs_1",
                description = "Visual reticle target identified for $domainName system",
                source = ObservationSource.CAMERA,
                confidence = 0.95f
            ),
            Observation(
                id = "obs_2",
                description = "System telemetry and safety interlock parameters evaluated for $domainName domain",
                source = ObservationSource.AI_INFERENCE,
                confidence = 0.92f
            )
        )

        val (safetyList, actions, tools, parts) = when (domain) {
            WorkDomain.AUTOMOTIVE -> Tuple4(
                listOf(SafetyRequirement("Hot Engine Bay & Battery Hazard", SafetySeverity.HIGH, "Engage parking brake, block wheels, and wear mechanics gloves.", listOf("Mechanics Gloves", "Eye Protection"))),
                listOf(
                    WorkAction("a1", "Connect OBD-II Diagnostic Scanner to vehicle DLC port", ActionType.INSPECT, verificationMethod = "Verify DLC connector lock and active telemetry link."),
                    WorkAction("a2", "Check fluid levels, drive belt tension, and wire harness integrity", ActionType.INSPECT, verificationMethod = "Align camera reticle on drive belt tensioner indicator.")
                ),
                listOf(ToolItem("tool_obd", "OBD-II Diagnostic Scanner", isAvailable = true), ToolItem("tool_wrenches", "Metric Socket & Wrench Set", isAvailable = true)),
                listOf(ToolItem("part_filter", "Engine Air Filter", isAvailable = false))
            )

            WorkDomain.ELECTRICAL -> Tuple4(
                listOf(SafetyRequirement("High Voltage Shock & Arc Flash Hazard", SafetySeverity.CRITICAL_STOP, "Isolate main electrical supply and wear 1000V rated insulated gloves.", listOf("1000V Insulated Gloves", "Arc Flash Visor"))),
                listOf(
                    WorkAction("a1", "Verify Zero Energy State using Digital Multimeter", ActionType.MEASURE, verificationMethod = "Align multimeter probes across phase lugs and ground bar."),
                    WorkAction("a2", "Inspect wiring lugs, terminal block torque, and breaker contact clips", ActionType.INSPECT, verificationMethod = "Point camera at terminal screws.")
                ),
                listOf(ToolItem("tool_gloves_1000v", "1000V Insulated Gloves", isAvailable = true), ToolItem("tool_multimeter", "CAT III Digital Multimeter", isAvailable = true)),
                emptyList()
            )

            WorkDomain.IT_NETWORKING -> Tuple4(
                listOf(SafetyRequirement("Electrostatic Discharge (ESD) & Laser Radiation Hazard", SafetySeverity.MEDIUM, "Wear anti-static ESD wrist strap and avoid direct optical fiber laser eye contact.", listOf("ESD Wrist Strap"))),
                listOf(
                    WorkAction("a1", "Perform physical link audit and check RJ-45 / Fiber SFP transceivers", ActionType.INSPECT, verificationMethod = "Point camera at active switch port status LEDs."),
                    WorkAction("a2", "Test ethernet cable continuity and signal attenuation using Cable Analyzer", ActionType.TEST, verificationMethod = "Verify green wiremap indicator on tester display.")
                ),
                listOf(ToolItem("tool_esd", "ESD Wrist Strap", isAvailable = true), ToolItem("tool_cable_tester", "Ethernet Network Cable Tester", isAvailable = true)),
                listOf(ToolItem("part_sfp", "Cat6 Patch Cable / SFP Transceiver", isAvailable = false))
            )

            WorkDomain.PLUMBING -> Tuple4(
                listOf(SafetyRequirement("Pressurized Fluid & Slippery Surface Hazard", SafetySeverity.HIGH, "Shut off main supply valve and wear heavy-duty waterproof boots and safety glasses.", listOf("Safety Glasses", "Waterproof Boots"))),
                listOf(
                    WorkAction("a1", "Isolate main water supply shutoff valve and relieve residual pipe pressure", ActionType.INSPECT, verificationMethod = "Point camera at supply valve handle in OFF position."),
                    WorkAction("a2", "Inspect pipe joints, compression fittings, and thread seals for active leakage", ActionType.INSPECT, verificationMethod = "Target pipe coupling with camera reticle.")
                ),
                listOf(ToolItem("tool_pipe_wrench", "Adjustable Pipe Wrench Set", isAvailable = true), ToolItem("tool_ptfe", "PTFE Thread Seal Tape", isAvailable = true)),
                listOf(ToolItem("part_gasket", "Compression Fitting O-Ring Seal", isAvailable = false))
            )

            WorkDomain.CONSTRUCTION -> Tuple4(
                listOf(SafetyRequirement("Falling Objects & Structural Overhead Hazard", SafetySeverity.HIGH, "Equip Hard Hat, High-Visibility Vest, and Steel-Toed Safety Boots.", listOf("Hard Hat", "Hi-Vis Vest", "Steel-Toed Boots"))),
                listOf(
                    WorkAction("a1", "Conduct structural fastener and anchor torque audit", ActionType.INSPECT, verificationMethod = "Align laser measure reticle with anchor bolt assembly."),
                    WorkAction("a2", "Verify beam alignment and plumb line using Digital Laser Level", ActionType.MEASURE, verificationMethod = "Check laser level projection line alignment.")
                ),
                listOf(ToolItem("tool_laser_level", "Self-Leveling Digital Laser Level", isAvailable = true), ToolItem("tool_torque_wrench", "Heavy Duty Torque Wrench", isAvailable = true)),
                emptyList()
            )

            WorkDomain.INDUSTRIAL -> Tuple4(
                listOf(SafetyRequirement("Hazardous Energy & Rotating Machinery Interlock Hazard", SafetySeverity.CRITICAL_STOP, "Apply Lockout/Tagout (LOTO) padlocks to energy isolation points before opening guards.", listOf("Hard Hat", "LOTO Lock Set", "Safety Glasses"))),
                listOf(
                    WorkAction("a1", "Apply Lockout/Tagout (LOTO) padlocks on main power disconnect breaker", ActionType.CONFIGURE, verificationMethod = "Verify LOTO padlock engaged on breaker handle."),
                    WorkAction("a2", "Inspect conveyor drive roller bearings, motor coupling, and PLC I/O module status", ActionType.INSPECT, verificationMethod = "Center reticle on PLC status diagnostic LEDs.")
                ),
                listOf(ToolItem("tool_loto", "Lockout/Tagout (LOTO) Kit", isAvailable = true), ToolItem("tool_multimeter", "Industrial Digital Multimeter", isAvailable = true)),
                emptyList()
            )

            WorkDomain.MECHANICAL -> Tuple4(
                listOf(SafetyRequirement("Pinch Point & Entanglement Hazard", SafetySeverity.HIGH, "Ensure drive shaft is stationary and wear snug safety gloves and protective eyewear.", listOf("Safety Gloves", "Protective Eyewear"))),
                listOf(
                    WorkAction("a1", "Check shaft alignment and bearing play using Dial Indicator gauge", ActionType.MEASURE, verificationMethod = "Target dial indicator probe tip on shaft surface."),
                    WorkAction("a2", "Inspect drive belt tension and pulley alignment for wear patterns", ActionType.INSPECT, verificationMethod = "Align reticle with drive belt groove profile.")
                ),
                listOf(ToolItem("tool_dial_indicator", "Dial Indicator Alignment Set", isAvailable = true), ToolItem("tool_calipers", "Digital Vernier Caliper", isAvailable = true)),
                emptyList()
            )

            else -> Tuple4(
                listOf(SafetyRequirement("General Field Work Hazard", SafetySeverity.MEDIUM, "Inspect work area, ensure adequate lighting and wear standard PPE.", listOf("Safety Glasses", "Work Gloves"))),
                listOf(
                    WorkAction("a1", "Perform visual inspection of equipment housing and electrical/mechanical connections", ActionType.INSPECT, verificationMethod = "Align reticle with main component label."),
                    WorkAction("a2", "Verify operational parameters, controls, and indicator LED statuses", ActionType.VERIFY, verificationMethod = "Check status display readout.")
                ),
                listOf(ToolItem("tool_basic_kit", "Universal Field Technician Tool Kit", isAvailable = true), ToolItem("tool_flashlight", "LED Work Flashlight", isAvailable = true)),
                emptyList()
            )
        }

        return WorkAnalysisResult(
            identifiedObject = "$domainName Hardware Unit",
            domain = domain,
            task = taskText,
            observations = observations,
            detectedIssues = listOf("Field inspection required for $taskText"),
            recommendedActions = actions,
            requiredTools = tools,
            requiredParts = parts,
            measurements = emptyList(),
            safetyRequirements = safetyList,
            confidence = 0.94f,
            nextAction = actions.firstOrNull()
        )
    }

    private data class Tuple4<A, B, C, D>(
        val val1: A,
        val val2: B,
        val val3: C,
        val val4: D
    )

    override suspend fun verifyStepCompletion(
        imageBytes: ByteArray?,
        currentStep: TroubleshootingStep
    ): StepVerificationResult {
        delay(600)

        val detectedBoxes = listOf(
            DetectedBox(
                xMin = currentStep.targetBoundingBox?.xMin ?: 0.25f,
                yMin = currentStep.targetBoundingBox?.yMin ?: 0.30f,
                xMax = currentStep.targetBoundingBox?.xMax ?: 0.65f,
                yMax = currentStep.targetBoundingBox?.yMax ?: 0.65f,
                label = "${currentStep.title} (Verified)"
            )
        )

        val feedback = "Step '${currentStep.title}' verified successfully using universal field reticle scan."

        return StepVerificationResult(
            isVerified = true,
            confidence = 0.96f,
            feedbackMessage = feedback,
            detectedObjects = listOf(currentStep.title, "Inspection Area", "Field Tool"),
            nextRecommendedAction = "Proceed to Next Action",
            detectedBoxes = detectedBoxes
        )
    }

    override suspend fun recommendStoresForMissingTools(
        missingTools: List<ToolItem>,
        userLocation: LocationInfo
    ): List<StoreLocation> {
        delay(300)
        val lat = userLocation.latitude
        val lng = userLocation.longitude

        return listOf(
            StoreLocation(
                id = "store_m1",
                name = "Hardware Express Supply",
                address = "101 Industrial Pkwy, Sector 4",
                distanceKm = 1.2,
                isOpen = true,
                phone = "(555) 019-2831",
                latitude = lat + 0.005,
                longitude = lng + 0.005
            ),
            StoreLocation(
                id = "store_m2",
                name = "Pro Parts & Electrical Depot",
                address = "450 Metro Tech Blvd",
                distanceKm = 3.5,
                isOpen = true,
                phone = "(555) 014-9922",
                latitude = lat - 0.012,
                longitude = lng + 0.008
            ),
            StoreLocation(
                id = "store_m3",
                name = "City Electronics & Industrial Spares",
                address = "88 West Commercial St",
                distanceKm = 5.1,
                isOpen = true,
                phone = "(555) 018-3344",
                latitude = lat + 0.020,
                longitude = lng - 0.015
            )
        )
    }
}
