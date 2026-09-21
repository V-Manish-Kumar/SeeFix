package com.example.seefix.domain

import com.example.seefix.domain.model.ActionType
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.SafetyStatus
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkAnalysisResult
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import com.example.seefix.domain.model.WorkTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkDomainTest {

    // ----------------------------------------------------------------------------------
    // 1. WorkDomain Enum Names and toUserFacingName() Outputs
    // ----------------------------------------------------------------------------------

    @Test
    fun testWorkDomainEnumNamesAndUserFacingNames() {
        val expectedMappings = mapOf(
            WorkDomain.MECHANICAL to "Mechanical",
            WorkDomain.ELECTRICAL to "Electrical",
            WorkDomain.ELECTRONICS to "Electronics",
            WorkDomain.AUTOMOTIVE to "Automotive",
            WorkDomain.INDUSTRIAL to "Industrial",
            WorkDomain.CONSTRUCTION to "Construction",
            WorkDomain.PLUMBING to "Plumbing",
            WorkDomain.HVAC to "HVAC",
            WorkDomain.AGRICULTURE to "Agriculture",
            WorkDomain.APPLIANCE to "Appliance",
            WorkDomain.IT_NETWORKING to "IT & Networking",
            WorkDomain.HOME_MAINTENANCE to "Home Maintenance",
            WorkDomain.GENERAL to "General",
        )

        assertEquals(13, WorkDomain.entries.size)

        for ((domain, expectedName) in expectedMappings) {
            assertEquals(expectedName, domain.toUserFacingName())
            assertEquals(domain, WorkDomain.valueOf(domain.name))
        }
    }

    @Test
    fun testWorkDomainSpecificNames() {
        assertEquals("MECHANICAL", WorkDomain.MECHANICAL.name)
        assertEquals("Mechanical", WorkDomain.MECHANICAL.toUserFacingName())

        assertEquals("ELECTRICAL", WorkDomain.ELECTRICAL.name)
        assertEquals("Electrical", WorkDomain.ELECTRICAL.toUserFacingName())

        assertEquals("ELECTRONICS", WorkDomain.ELECTRONICS.name)
        assertEquals("Electronics", WorkDomain.ELECTRONICS.toUserFacingName())

        assertEquals("AUTOMOTIVE", WorkDomain.AUTOMOTIVE.name)
        assertEquals("Automotive", WorkDomain.AUTOMOTIVE.toUserFacingName())

        assertEquals("INDUSTRIAL", WorkDomain.INDUSTRIAL.name)
        assertEquals("Industrial", WorkDomain.INDUSTRIAL.toUserFacingName())

        assertEquals("CONSTRUCTION", WorkDomain.CONSTRUCTION.name)
        assertEquals("Construction", WorkDomain.CONSTRUCTION.toUserFacingName())

        assertEquals("PLUMBING", WorkDomain.PLUMBING.name)
        assertEquals("Plumbing", WorkDomain.PLUMBING.toUserFacingName())

        assertEquals("HVAC", WorkDomain.HVAC.name)
        assertEquals("HVAC", WorkDomain.HVAC.toUserFacingName())

        assertEquals("AGRICULTURE", WorkDomain.AGRICULTURE.name)
        assertEquals("Agriculture", WorkDomain.AGRICULTURE.toUserFacingName())

        assertEquals("APPLIANCE", WorkDomain.APPLIANCE.name)
        assertEquals("Appliance", WorkDomain.APPLIANCE.toUserFacingName())

        assertEquals("IT_NETWORKING", WorkDomain.IT_NETWORKING.name)
        assertEquals("IT & Networking", WorkDomain.IT_NETWORKING.toUserFacingName())

        assertEquals("HOME_MAINTENANCE", WorkDomain.HOME_MAINTENANCE.name)
        assertEquals("Home Maintenance", WorkDomain.HOME_MAINTENANCE.toUserFacingName())

        assertEquals("GENERAL", WorkDomain.GENERAL.name)
        assertEquals("General", WorkDomain.GENERAL.toUserFacingName())
    }

    // ----------------------------------------------------------------------------------
    // 2. Mapping between TroubleshootingSession / TroubleshootingStep and WorkTask / WorkAction / WorkAnalysisResult
    // ----------------------------------------------------------------------------------

    @Test
    fun testTroubleshootingSessionToWorkTaskMapping() {
        val device = HardwareDevice(
            id = "dev_101",
            name = "Commercial HVAC Compressor",
            model = "HVAC_2000",
            category = "HVAC"
        )
        val timestamp = 1700000000000L
        val session = TroubleshootingSession(
            id = "sess_001",
            device = device,
            detectedProblem = "Low refrigerant pressure",
            domain = WorkDomain.HVAC,
            statusText = "IN PROGRESS",
            timestamp = timestamp
        )

        val task = session.toWorkTask()

        assertEquals("sess_001", task.id)
        assertEquals("sess_001", task.sessionId)
        assertEquals("Commercial HVAC Compressor", task.title)
        assertEquals("Low refrigerant pressure", task.description)
        assertEquals(WorkDomain.HVAC, task.domain)
        assertEquals("IN PROGRESS", task.status)
        assertEquals(timestamp, task.createdAt)
        assertEquals(timestamp, task.updatedAt)
    }

    @Test
    fun testTroubleshootingSessionToWorkTaskFallbackTitle() {
        val sessionNoDevice = TroubleshootingSession(
            id = "sess_002",
            device = null,
            detectedProblem = "Engine knocking noise",
            domain = WorkDomain.AUTOMOTIVE
        )
        val task = sessionNoDevice.toWorkTask()
        assertEquals("Engine knocking noise", task.title)

        val emptySession = TroubleshootingSession(
            id = "sess_003",
            device = null,
            detectedProblem = "",
            domain = WorkDomain.GENERAL
        )
        val defaultTask = emptySession.toWorkTask()
        assertEquals("Troubleshooting Task", defaultTask.title)
    }

    @Test
    fun testTroubleshootingSessionFromWorkTaskMapping() {
        val task = WorkTask(
            id = "task_50",
            title = "Repair Hydraulic Cylinder",
            description = "Hydraulic fluid leak around seal",
            domain = WorkDomain.INDUSTRIAL,
            status = "PENDING",
            createdAt = 1690000000000L,
            sessionId = "sess_custom_99"
        )

        val session = TroubleshootingSession.fromWorkTask(task)

        assertEquals("sess_custom_99", session.id)
        assertNotNull(session.device)
        assertEquals("dev_sess_custom_99", session.device?.id)
        assertEquals("Repair Hydraulic Cylinder", session.device?.name)
        assertEquals("INDUSTRIAL", session.device?.model)
        assertEquals("Industrial", session.device?.category)
        assertEquals("Hydraulic fluid leak around seal", session.detectedProblem)
        assertEquals(WorkDomain.INDUSTRIAL, session.domain)
        assertEquals("PENDING", session.statusText)
        assertEquals(1690000000000L, session.timestamp)
    }

    @Test
    fun testTroubleshootingSessionFromWorkTaskWithDefaultSessionId() {
        val task = WorkTask(
            id = "task_51",
            title = "Replace Router Gateway",
            description = "No internet signal",
            domain = WorkDomain.IT_NETWORKING,
            sessionId = null
        )

        val session = TroubleshootingSession.fromWorkTask(task, sessionId = "override_sess")
        assertEquals("override_sess", session.id)
        assertEquals("Replace Router Gateway", session.device?.name)
    }

    @Test
    fun testTroubleshootingSessionToWorkAnalysisResultMapping() {
        val tool1 = ToolItem(id = "t1", name = "Multimeter", isAvailable = true)
        val part1 = ToolItem(id = "p1", name = "10A Fuse", isAvailable = false)

        val step1 = TroubleshootingStep(
            stepNumber = 1,
            title = "Isolate Power",
            instructionText = "Turn off breaker B12 before proceeding",
            visualVerificationPrompt = "Verify breaker switch is in OFF position",
            requiredTools = listOf(tool1),
            requiresSafetyConfirmation = true,
            safetyConfirmationPrompt = "Confirm power is off using multimeter"
        )

        val session = TroubleshootingSession(
            id = "sess_analysis_1",
            device = HardwareDevice(
                id = "dev_1",
                name = "Dishwasher Pump Motor",
                model = "DW-500",
                category = "Appliance"
            ),
            detectedProblem = "Motor does not spin",
            observations = listOf("Humming sound on power-on", "No water movement"),
            steps = listOf(step1),
            currentStepIndex = 0,
            availableTools = listOf(tool1),
            missingTools = listOf(part1),
            safetyStatus = SafetyStatus(
                level = SafetyLevel.CAUTION,
                message = "High voltage components exposed."
            ),
            confidence = 0.88f,
            domain = WorkDomain.APPLIANCE
        )

        val result = session.toWorkAnalysisResult()

        assertEquals("Dishwasher Pump Motor", result.identifiedObject)
        assertEquals(WorkDomain.APPLIANCE, result.domain)
        assertEquals("Motor does not spin", result.task)
        assertEquals(2, result.observations.size)
        assertEquals("Humming sound on power-on", result.observations[0].description)
        assertEquals(ObservationSource.AI_INFERENCE, result.observations[0].source)
        assertEquals(listOf("Motor does not spin"), result.detectedIssues)

        assertEquals(1, result.recommendedActions.size)
        val action = result.recommendedActions[0]
        assertEquals("action_1", action.id)
        assertEquals("Isolate Power -- Turn off breaker B12 before proceeding", action.description)
        assertEquals(ActionType.REPAIR, action.type)
        assertEquals("Verify breaker switch is in OFF position", action.verificationMethod)

        assertEquals(listOf(tool1), result.requiredTools)
        assertEquals(listOf(part1), result.requiredParts)
        assertEquals(1, result.safetyRequirements.size)
        assertEquals(SafetySeverity.MEDIUM, result.safetyRequirements[0].severity)
        assertEquals("High voltage components exposed.", result.safetyRequirements[0].hazard)
        assertEquals(0.88f, result.confidence, 0.001f)

        assertNotNull(result.nextAction)
        assertEquals("action_1", result.nextAction?.id)
    }

    @Test
    fun testTroubleshootingSessionToWorkAnalysisResultSafetyLevels() {
        val dangerousSession = TroubleshootingSession(
            id = "sess_danger",
            safetyStatus = SafetyStatus(
                level = SafetyLevel.DANGEROUS_STOP,
                message = "Gas Leak Detected!"
            )
        )
        val dangerousResult = dangerousSession.toWorkAnalysisResult()
        assertEquals(1, dangerousResult.safetyRequirements.size)
        assertEquals(SafetySeverity.CRITICAL_STOP, dangerousResult.safetyRequirements[0].severity)

        val safeSession = TroubleshootingSession(
            id = "sess_safe",
            safetyStatus = SafetyStatus.SAFE_DEFAULT
        )
        val safeResult = safeSession.toWorkAnalysisResult()
        assertTrue(safeResult.safetyRequirements.isEmpty())
    }

    @Test
    fun testTroubleshootingSessionFromWorkAnalysisResultMapping() {
        val action1 = WorkAction(
            id = "act_1",
            description = "Check Valve: Ensure main inlet valve is open",
            type = ActionType.INSPECT,
            status = "COMPLETED",
            verificationMethod = "Check valve lever angle"
        )
        val action2 = WorkAction(
            id = "act_2",
            description = "Flush Line -- Run water for 30 seconds",
            type = ActionType.CLEAN,
            status = "PENDING"
        )

        val safetyReq = SafetyRequirement(
            hazard = "Pressurized Water Line",
            severity = SafetySeverity.CRITICAL_STOP,
            precaution = "Shut off pressure valve prior to disconnecting"
        )

        val analysisResult = WorkAnalysisResult(
            identifiedObject = "Main Water Supply Pipe",
            domain = WorkDomain.PLUMBING,
            task = "Water Pipe Pressure Inspection",
            observations = listOf(
                Observation("obs_1", "Low pressure at kitchen tap", ObservationSource.USER)
            ),
            recommendedActions = listOf(action1, action2),
            requiredTools = listOf(ToolItem(id = "t_wrench", name = "Pipe Wrench")),
            requiredParts = emptyList(),
            safetyRequirements = listOf(safetyReq),
            confidence = 0.92f
        )

        val session = TroubleshootingSession.fromWorkAnalysisResult(analysisResult, sessionId = "sess_plumbing_101")

        assertEquals("sess_plumbing_101", session.id)
        assertEquals("Main Water Supply Pipe", session.device?.name)
        assertEquals("PLUMBING", session.device?.model)
        assertEquals("Plumbing", session.device?.category)
        assertEquals("Water Pipe Pressure Inspection", session.detectedProblem)
        assertEquals(WorkDomain.PLUMBING, session.domain)
        assertEquals(listOf("Low pressure at kitchen tap"), session.observations)

        assertEquals(2, session.steps.size)
        val step1 = session.steps[0]
        assertEquals(1, step1.stepNumber)
        assertEquals("Check Valve", step1.title)
        assertEquals("Ensure main inlet valve is open", step1.instructionText)
        assertTrue(step1.isCompleted)

        val step2 = session.steps[1]
        assertEquals(2, step2.stepNumber)
        assertEquals("Flush Line", step2.title)
        assertEquals("Run water for 30 seconds", step2.instructionText)
        assertFalse(step2.isCompleted)

        assertEquals(SafetyLevel.DANGEROUS_STOP, session.safetyStatus.level)
        assertEquals("Shut off pressure valve prior to disconnecting", session.safetyStatus.message)
        assertEquals(0.92f, session.confidence, 0.001f)
    }

    @Test
    fun testTroubleshootingStepToWorkActionAndBack() {
        val originalStep = TroubleshootingStep(
            stepNumber = 3,
            title = "Calibrate Sensor",
            instructionText = "Set zero offset on optical sensor",
            spokenInstruction = "Set zero offset on optical sensor",
            visualVerificationPrompt = "Sensor LED should glow green",
            isCompleted = true,
            requiredTools = listOf(ToolItem(id = "tool_cal", name = "Calibration Tool")),
            requiresSafetyConfirmation = true,
            safetyConfirmationPrompt = "Wear anti-static wrist strap"
        )

        val workAction = originalStep.toWorkAction()

        assertEquals("action_3", workAction.id)
        assertEquals("Calibrate Sensor -- Set zero offset on optical sensor", workAction.description)
        assertEquals(ActionType.REPAIR, workAction.type)
        assertEquals("COMPLETED", workAction.status)
        assertEquals("Sensor LED should glow green", workAction.verificationMethod)
        assertEquals(1, workAction.safetyRequirements.size)
        assertEquals("Wear anti-static wrist strap", workAction.safetyRequirements[0].precaution)

        val roundTripStep = TroubleshootingStep.fromWorkAction(3, workAction)

        assertEquals(3, roundTripStep.stepNumber)
        assertEquals("Calibrate Sensor", roundTripStep.title)
        assertEquals("Set zero offset on optical sensor", roundTripStep.instructionText)
        assertEquals("Sensor LED should glow green", roundTripStep.visualVerificationPrompt)
        assertTrue(roundTripStep.isCompleted)
        assertTrue(roundTripStep.requiresSafetyConfirmation)
        assertEquals("Wear anti-static wrist strap", roundTripStep.safetyConfirmationPrompt)
    }

    @Test
    fun testTroubleshootingStepFromWorkActionFormattingVariants() {
        val dashAction = WorkAction(
            id = "act_dash",
            description = "Safety First -- Verify ground connection",
            type = ActionType.VERIFY
        )
        val dashStep = TroubleshootingStep.fromWorkAction(1, dashAction)
        assertEquals("Safety First", dashStep.title)
        assertEquals("Verify ground connection", dashStep.instructionText)

        val colonAction = WorkAction(
            id = "act_colon",
            description = "Step 1: Unplug power cable",
            type = ActionType.DISASSEMBLE
        )
        val colonStep = TroubleshootingStep.fromWorkAction(2, colonAction)
        assertEquals("Step 1", colonStep.title)
        assertEquals("Unplug power cable", colonStep.instructionText)

        val plainAction = WorkAction(
            id = "act_plain",
            description = "Tighten mounting screws securely",
            type = ActionType.REPAIR
        )
        val plainStep = TroubleshootingStep.fromWorkAction(3, plainAction)
        assertEquals("REPAIR", plainStep.title)
        assertEquals("Tighten mounting screws securely", plainStep.instructionText)
    }

    // ----------------------------------------------------------------------------------
    // 3. WorkContext Construction
    // ----------------------------------------------------------------------------------

    @Test
    fun testWorkContextDefaultConstruction() {
        val defaultContext = WorkContext()

        assertNull(defaultContext.task)
        assertEquals(WorkDomain.GENERAL, defaultContext.domain)
        assertNull(defaultContext.userInput)
        assertTrue(defaultContext.conversationHistory.isEmpty())
        assertNull(defaultContext.machineInfo)
        assertTrue(defaultContext.images.isEmpty())
        assertNull(defaultContext.videoContext)
        assertNull(defaultContext.audioTranscript)
        assertNull(defaultContext.sensorData)
        assertNull(defaultContext.location)
        assertTrue(defaultContext.workHistory.isEmpty())
        assertTrue(defaultContext.availableTools.isEmpty())
        assertTrue(defaultContext.availableParts.isEmpty())
        assertTrue(defaultContext.retrievedKnowledge.isEmpty())
        assertTrue(defaultContext.completedSteps.isEmpty())
        assertTrue(defaultContext.observations.isEmpty())
        assertTrue(defaultContext.safetyContext.isEmpty())
    }

    @Test
    fun testWorkContextFullConstruction() {
        val task = WorkTask(
            id = "task_ctx_01",
            title = "Solar Inverter Maintenance",
            description = "Error code E-404 grid disconnect",
            domain = WorkDomain.ELECTRICAL,
            status = "IN_PROGRESS",
            priority = "HIGH"
        )

        val obs1 = Observation(
            id = "obs_101",
            description = "Inverter status LED flashing red",
            source = ObservationSource.CAMERA,
            confidence = 0.95f
        )
        val obs2 = Observation(
            id = "obs_102",
            description = "Audible clicking noise every 5 seconds",
            source = ObservationSource.USER,
            confidence = 1.0f
        )

        val safetyReq1 = SafetyRequirement(
            hazard = "High Voltage DC Bus",
            severity = SafetySeverity.CRITICAL_STOP,
            precaution = "Disconnect DC isolator switch before touching terminals",
            ppe = listOf("Insulated Gloves 1000V", "Safety Glasses")
        )

        val tool1 = ToolItem(id = "t_multi", name = "Digital Multimeter CAT III")
        val part1 = ToolItem(id = "p_fuse", name = "DC Surge Fuse 1000V")

        val completedAction = WorkAction(
            id = "act_done_1",
            description = "Isolate DC input switch",
            type = ActionType.DISASSEMBLE,
            status = "COMPLETED"
        )

        val context = WorkContext(
            task = task,
            domain = WorkDomain.ELECTRICAL,
            userInput = "Solar array inverter is throwing error E-404",
            images = listOf("file:///sdcard/inverter_status.jpg"),
            availableTools = listOf(tool1),
            availableParts = listOf(part1),
            completedSteps = listOf(completedAction),
            observations = listOf(obs1, obs2),
            safetyContext = listOf(safetyReq1)
        )

        assertNotNull(context.task)
        assertEquals("task_ctx_01", context.task?.id)
        assertEquals("Solar Inverter Maintenance", context.task?.title)
        assertEquals(WorkDomain.ELECTRICAL, context.domain)
        assertEquals("Solar array inverter is throwing error E-404", context.userInput)
        assertEquals(1, context.images.size)
        assertEquals("file:///sdcard/inverter_status.jpg", context.images[0])

        assertEquals(1, context.availableTools.size)
        assertEquals("Digital Multimeter CAT III", context.availableTools[0].name)

        assertEquals(1, context.availableParts.size)
        assertEquals("DC Surge Fuse 1000V", context.availableParts[0].name)

        assertEquals(1, context.completedSteps.size)
        assertEquals("COMPLETED", context.completedSteps[0].status)

        assertEquals(2, context.observations.size)
        assertEquals("Inverter status LED flashing red", context.observations[0].description)
        assertEquals(ObservationSource.CAMERA, context.observations[0].source)
        assertEquals("Audible clicking noise every 5 seconds", context.observations[1].description)

        assertEquals(1, context.safetyContext.size)
        assertEquals(SafetySeverity.CRITICAL_STOP, context.safetyContext[0].severity)
        assertEquals(listOf("Insulated Gloves 1000V", "Safety Glasses"), context.safetyContext[0].ppe)
    }
}
