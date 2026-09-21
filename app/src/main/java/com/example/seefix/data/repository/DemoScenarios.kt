package com.example.seefix.data.repository

import com.example.seefix.domain.model.BoundingBox
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkDomain

/**
 * Preloaded Hackathon Demo Scenarios for SeeFix Field Engineer AI Assistant.
 */
object DemoScenarios {

    // 1. Smart Washing Machine (iQOO Demo Appliance)
    val WASHING_MACHINE_DEVICE = HardwareDevice(
        id = "demo_wm_iqoo",
        name = "Smart Washing Machine (iQOO Demo Appliance)",
        model = "WM-9000X-PRO",
        category = "Home Appliance",
        manualId = "MAN-WM-IQOO-2025"
    )

    val WASHING_MACHINE_SCENARIO = TroubleshootingSession(
        id = "sess_demo_wm_01",
        device = WASHING_MACHINE_DEVICE,
        detectedProblem = "Drain Pump Failure & Motor Capacitor Fault (Error E04)",
        observations = listOf(
            "Visual scan identified iQOO WM-9000X-PRO Inverter Drive Board",
            "Error code E04: Drain pump timeout & motor start capacitor voltage drift",
            "Safety check required: 230V AC mains connection"
        ),
        currentStepIndex = 0,
        steps = listOf(
            TroubleshootingStep(
                stepNumber = 1,
                title = "Disconnect Main AC Power Plug (Safety Check: HIGH VOLTAGE)",
                instructionText = "Unplug the main AC power cord from wall outlet before removing rear service panel to isolate 230V AC mains.",
                spokenInstruction = "Step 1: Disconnect the main AC power cord from wall outlet to isolate high voltage.",
                visualVerificationPrompt = "Align camera frame with main AC power cord and outlet.",
                targetBoundingBox = BoundingBox(0.20f, 0.25f, 0.70f, 0.75f),
                requiresSafetyConfirmation = true,
                isSafetyConfirmed = false,
                safetyConfirmationPrompt = "CONFIRM MAIN AC POWER CORD IS UNPLUGGED",
                requiredTools = listOf(
                    ToolItem("tool_gloves", "CAT IV Insulated Gloves", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 2,
                title = "Remove Rear Access Panel screws using Phillips Screwdriver",
                instructionText = "Unscrew the 4 retaining screws on the back service panel using a PH2 Phillips Screwdriver.",
                spokenInstruction = "Step 2: Use a PH2 Phillips Screwdriver to remove the 4 rear access panel screws.",
                visualVerificationPrompt = "Point camera at the rear access panel retaining screws.",
                targetBoundingBox = BoundingBox(0.15f, 0.20f, 0.85f, 0.80f),
                requiredTools = listOf(
                    ToolItem("tool_phillips", "Phillips Screwdriver (PH2)", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 3,
                title = "Point camera at Motor Capacitor (Verification Target: 45uF Capacitor)",
                instructionText = "Locate the silver cylindrical 45uF motor run capacitor C42 mounted on the metal bracket next to motor housing.",
                spokenInstruction = "Step 3: Point camera at the 45uF Motor Run Capacitor C42 for visual reticle inspection.",
                visualVerificationPrompt = "Target cylindrical 45uF Motor Capacitor C42 on bracket.",
                targetBoundingBox = BoundingBox(0.25f, 0.30f, 0.65f, 0.65f),
                requiredTools = listOf(
                    ToolItem("tool_flashlight", "LED Work Light", isAvailable = true, isRequired = false)
                )
            ),
            TroubleshootingStep(
                stepNumber = 4,
                title = "Disconnect 2-pin capacitor wire connector (Action: Disconnect wire)",
                instructionText = "Press plastic locking clip and disconnect the 2-pin spade wire connector from capacitor posts.",
                spokenInstruction = "Step 4: Press locking clip and disconnect the 2-pin wire connector from capacitor C42.",
                visualVerificationPrompt = "Verify 2-pin spade connector is detached from capacitor terminals.",
                targetBoundingBox = BoundingBox(0.30f, 0.35f, 0.70f, 0.70f),
                requiredTools = listOf(
                    ToolItem("tool_pliers", "Needle-Nose Pliers", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 5,
                title = "Check missing tool/part: 45uF Motor Run Capacitor",
                instructionText = "Inspect capacitor for bulging or electrical breakdown. Replacement part 45uF 450VAC required.",
                spokenInstruction = "Step 5: Check 45uF Motor Run Capacitor replacement part availability.",
                visualVerificationPrompt = "Point camera at replacement capacitor bracket.",
                targetBoundingBox = BoundingBox(0.20f, 0.25f, 0.75f, 0.75f),
                requiredTools = listOf(
                    ToolItem("part_cap_45uf", "45uF Motor Run Capacitor", isAvailable = false, isRequired = true, storeQueryKey = "Capacitor")
                )
            )
        ),
        availableTools = listOf(
            ToolItem("tool_gloves", "CAT IV Insulated Gloves", isAvailable = true),
            ToolItem("tool_phillips", "Phillips Screwdriver (PH2)", isAvailable = true),
            ToolItem("tool_pliers", "Needle-Nose Pliers", isAvailable = true),
            ToolItem("tool_flashlight", "LED Work Light", isAvailable = true)
        ),
        missingTools = listOf(
            ToolItem("part_cap_45uf", "45uF Motor Run Capacitor", isAvailable = false, isRequired = true, storeQueryKey = "Capacitor")
        ),
        safetyStatus = SafetyStatus(
            level = SafetyLevel.DANGEROUS_STOP,
            message = "HIGH VOLTAGE MAINS DETECTED! Unplug power cord before opening access panel."
        ),
        confidence = 0.96f,
        durationSeconds = 240,
        statusText = "IN PROGRESS",
        domain = WorkDomain.APPLIANCE
    )

    // 2. Main Circuit Breaker Panel
    val CIRCUIT_BREAKER_DEVICE = HardwareDevice(
        id = "demo_cb_panel",
        name = "Main Circuit Breaker Panel",
        model = "CB-200A-MAIN",
        category = "Electrical & Mains",
        manualId = "MAN-CB-2025"
    )

    val CIRCUIT_BREAKER_SCENARIO = TroubleshootingSession(
        id = "sess_demo_cb_02",
        device = CIRCUIT_BREAKER_DEVICE,
        detectedProblem = "AC Breaker Tripped & Neutral Bus Bar Loose",
        observations = listOf(
            "Identified 200A Main Service Panel with tripped 30A dual pole breaker",
            "Thermal arc trip on neutral bus bar connector",
            "High voltage hazard present on main bus bar"
        ),
        currentStepIndex = 0,
        steps = listOf(
            TroubleshootingStep(
                stepNumber = 1,
                title = "Turn Off Main Breaker (Safety Isolation)",
                instructionText = "Stand on rubber insulating mat and switch the Main 200A breaker toggle to OFF.",
                spokenInstruction = "Step 1: Switch Main Breaker to OFF position before touching internal panel components.",
                visualVerificationPrompt = "Point camera at Main 200A service breaker toggle switch.",
                targetBoundingBox = BoundingBox(0.20f, 0.20f, 0.80f, 0.60f),
                requiresSafetyConfirmation = true,
                isSafetyConfirmed = false,
                safetyConfirmationPrompt = "CONFIRM MAIN BREAKER SWITCH IS OFF",
                requiredTools = listOf(
                    ToolItem("tool_gloves_1000v", "1000V Insulated Safety Gloves", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 2,
                title = "Verify Voltage with Multimeter",
                instructionText = "Set multimeter to 750V AC range. Touch test probes to neutral bar and phase lugs to verify 0.0V.",
                spokenInstruction = "Step 2: Use Digital Multimeter to test zero voltage across neutral bus bar.",
                visualVerificationPrompt = "Align multimeter probes across neutral lug and ground bar.",
                targetBoundingBox = BoundingBox(0.30f, 0.35f, 0.70f, 0.75f),
                requiredTools = listOf(
                    ToolItem("tool_multimeter", "CAT III Digital Multimeter", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 3,
                title = "Tighten Neutral Screw",
                instructionText = "Using an insulated flathead screwdriver, torque loose neutral terminal screw lug to 45 in-lbs.",
                spokenInstruction = "Step 3: Tighten loose neutral bus bar terminal lug screw securely.",
                visualVerificationPrompt = "Target neutral bus bar screw lug with insulated screwdriver.",
                targetBoundingBox = BoundingBox(0.25f, 0.30f, 0.65f, 0.65f),
                requiredTools = listOf(
                    ToolItem("tool_flathead", "Insulated Flathead Screwdriver", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 4,
                title = "Reset Breaker",
                instructionText = "Push center-tripped breaker toggle firmly to OFF position to reset spring latch, then switch to ON.",
                spokenInstruction = "Step 4: Push tripped breaker lever to OFF to reset internal latch, then switch back to ON.",
                visualVerificationPrompt = "Point camera at dual pole breaker switch indicator.",
                targetBoundingBox = BoundingBox(0.35f, 0.40f, 0.75f, 0.75f),
                requiredTools = listOf(
                    ToolItem("tool_gloves_1000v", "1000V Insulated Safety Gloves", isAvailable = true, isRequired = true)
                )
            )
        ),
        availableTools = listOf(
            ToolItem("tool_gloves_1000v", "1000V Insulated Safety Gloves", isAvailable = true),
            ToolItem("tool_multimeter", "CAT III Digital Multimeter", isAvailable = true),
            ToolItem("tool_flathead", "Insulated Flathead Screwdriver", isAvailable = true)
        ),
        missingTools = emptyList(),
        safetyStatus = SafetyStatus(
            level = SafetyLevel.DANGEROUS_STOP,
            message = "HIGH VOLTAGE MAINS DETECTED! Wear 1000V insulated gloves and turn OFF Main Breaker!"
        ),
        confidence = 0.98f,
        durationSeconds = 310,
        statusText = "RESOLVED",
        domain = WorkDomain.ELECTRICAL
    )

    // 3. HVAC Split System
    val HVAC_DEVICE = HardwareDevice(
        id = "demo_hvac_system",
        name = "HVAC Split System",
        model = "HVAC-X9000-SPLIT",
        category = "HVAC & Thermal",
        manualId = "MAN-HVAC-9000"
    )

    val HVAC_SCENARIO = TroubleshootingSession(
        id = "sess_demo_hvac_03",
        device = HVAC_DEVICE,
        detectedProblem = "Thermal Fuse Open Circuit (Code H22)",
        observations = listOf(
            "Diagnostic error Code H22: Inverter compressor thermal fuse trip",
            "Compressor thermal cutoff sensor registered >105°C temperature trip",
            "High voltage DC link capacitors holding energy"
        ),
        currentStepIndex = 0,
        steps = listOf(
            TroubleshootingStep(
                stepNumber = 1,
                title = "Isolate Outdoor Unit Power Disconnect",
                instructionText = "Pull outdoor disconnect handle at wall box near condenser unit to disconnect 240V power line.",
                spokenInstruction = "Step 1: Pull outdoor power safety disconnect handle to isolate 240V condenser power.",
                visualVerificationPrompt = "Point camera at outdoor power safety disconnect wall box.",
                targetBoundingBox = BoundingBox(0.20f, 0.20f, 0.70f, 0.70f),
                requiresSafetyConfirmation = true,
                isSafetyConfirmed = false,
                safetyConfirmationPrompt = "CONFIRM OUTDOOR DISCONNECT HANDLE IS PULLED",
                requiredTools = listOf(
                    ToolItem("tool_gloves", "Safety Gloves", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 2,
                title = "Inspect Thermal Cutoff Assembly",
                instructionText = "Remove 2 hex bolts on electrical junction cover to access the thermal cutoff fuse harness.",
                spokenInstruction = "Step 2: Remove electrical cover bolts to inspect thermal cutoff assembly.",
                visualVerificationPrompt = "Center camera frame on compressor electrical junction box.",
                targetBoundingBox = BoundingBox(0.25f, 0.30f, 0.75f, 0.75f),
                requiredTools = listOf(
                    ToolItem("tool_hex_wrench", "8mm Hex Nut Driver", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 3,
                title = "Measure Resistance Across Thermal Fuse",
                instructionText = "Set multimeter to Ohms. Measure across thermal fuse leads. Infinite resistance (OL) indicates open fuse.",
                spokenInstruction = "Step 3: Use multimeter resistance setting to check for open thermal fuse.",
                visualVerificationPrompt = "Point camera at multimeter probes touching thermal fuse terminals.",
                targetBoundingBox = BoundingBox(0.30f, 0.35f, 0.70f, 0.70f),
                requiredTools = listOf(
                    ToolItem("tool_multimeter", "Digital Multimeter", isAvailable = true, isRequired = true)
                )
            ),
            TroubleshootingStep(
                stepNumber = 4,
                title = "Replace Blown Thermal Fuse",
                instructionText = "Unclip blown thermal fuse and replace with genuine 100°C 250V 10A thermal cutout fuse.",
                spokenInstruction = "Step 4: Replace open thermal fuse with 100°C 250V 10A replacement fuse.",
                visualVerificationPrompt = "Point camera at newly installed thermal fuse line clip.",
                targetBoundingBox = BoundingBox(0.20f, 0.25f, 0.75f, 0.75f),
                requiredTools = listOf(
                    ToolItem("part_thermal_fuse", "Thermal Fuse 100C 250V 10A", isAvailable = false, isRequired = true, storeQueryKey = "Fuse")
                )
            )
        ),
        availableTools = listOf(
            ToolItem("tool_gloves", "Safety Gloves", isAvailable = true),
            ToolItem("tool_hex_wrench", "8mm Hex Nut Driver", isAvailable = true),
            ToolItem("tool_multimeter", "Digital Multimeter", isAvailable = true)
        ),
        missingTools = listOf(
            ToolItem("part_thermal_fuse", "Thermal Fuse 100C 250V 10A", isAvailable = false, isRequired = true, storeQueryKey = "Fuse")
        ),
        safetyStatus = SafetyStatus(
            level = SafetyLevel.DANGEROUS_STOP,
            message = "HIGH VOLTAGE & HOT SURFACE HAZARD! Pull outdoor disconnect handle before opening cover!"
        ),
        confidence = 0.94f,
        durationSeconds = 180,
        statusText = "IN PROGRESS",
        domain = WorkDomain.HVAC
    )

    fun getAllScenarios(): List<TroubleshootingSession> {
        return listOf(
            WASHING_MACHINE_SCENARIO,
            CIRCUIT_BREAKER_SCENARIO,
            HVAC_SCENARIO
        )
    }

    fun getScenarioForAppliance(applianceQuery: String): TroubleshootingSession {
        val queryLower = applianceQuery.lowercase()
        return when {
            queryLower.contains("breaker") || queryLower.contains("panel") || queryLower.contains("circuit") -> CIRCUIT_BREAKER_SCENARIO
            queryLower.contains("hvac") || queryLower.contains("thermal") || queryLower.contains("fuse") -> HVAC_SCENARIO
            else -> WASHING_MACHINE_SCENARIO
        }
    }

    fun getPreloadedHistory(): List<TroubleshootingSession> {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - (60 * 60 * 1000)
        val yesterday = now - (24 * 60 * 60 * 1000)
        val threeDaysAgo = now - (3 * 24 * 60 * 60 * 1000)

        return listOf(
            WASHING_MACHINE_SCENARIO.copy(
                id = "sess_hist_01",
                timestamp = oneHourAgo,
                statusText = "IN PROGRESS",
                durationSeconds = 240
            ),
            CIRCUIT_BREAKER_SCENARIO.copy(
                id = "sess_hist_02",
                timestamp = yesterday,
                statusText = "RESOLVED",
                currentStepIndex = 3,
                steps = CIRCUIT_BREAKER_SCENARIO.steps.map { it.copy(isCompleted = true, isVerified = true) },
                durationSeconds = 310
            ),
            HVAC_SCENARIO.copy(
                id = "sess_hist_03",
                timestamp = threeDaysAgo,
                statusText = "RESOLVED",
                currentStepIndex = 3,
                steps = HVAC_SCENARIO.steps.map { it.copy(isCompleted = true, isVerified = true) },
                durationSeconds = 420
            )
        )
    }
}
