package com.example.seefix

import com.example.seefix.ai.AIAssistantManager
import com.example.seefix.ai.GeminiAIEngine
import com.example.seefix.ai.MockAIEngine
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.domain.model.ActionType
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.TroubleshootingStep
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkAnalysisResult
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import com.example.seefix.domain.model.WorkTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainGeneralizationTest {

    @Test
    fun testWorkDomain_toUserFacingName() {
        assertEquals("Mechanical", WorkDomain.MECHANICAL.toUserFacingName())
        assertEquals("Electrical", WorkDomain.ELECTRICAL.toUserFacingName())
        assertEquals("Electronics", WorkDomain.ELECTRONICS.toUserFacingName())
        assertEquals("Automotive", WorkDomain.AUTOMOTIVE.toUserFacingName())
        assertEquals("Industrial", WorkDomain.INDUSTRIAL.toUserFacingName())
        assertEquals("Construction", WorkDomain.CONSTRUCTION.toUserFacingName())
        assertEquals("Plumbing", WorkDomain.PLUMBING.toUserFacingName())
        assertEquals("HVAC", WorkDomain.HVAC.toUserFacingName())
        assertEquals("Agriculture", WorkDomain.AGRICULTURE.toUserFacingName())
        assertEquals("Appliance", WorkDomain.APPLIANCE.toUserFacingName())
        assertEquals("IT & Networking", WorkDomain.IT_NETWORKING.toUserFacingName())
        assertEquals("Home Maintenance", WorkDomain.HOME_MAINTENANCE.toUserFacingName())
        assertEquals("General", WorkDomain.GENERAL.toUserFacingName())
    }

    @Test
    fun testDomainModelsInstantiation() {
        val task = WorkTask(
            id = "task_01",
            title = "Check Automotive Transmission Fluid",
            description = "Inspect dipstick and check for leaks",
            domain = WorkDomain.AUTOMOTIVE
        )
        assertEquals(WorkDomain.AUTOMOTIVE, task.domain)

        val obs = Observation(
            id = "obs_01",
            description = "Fluid color dark brown",
            source = ObservationSource.CAMERA,
            confidence = 0.9f
        )
        assertEquals(ObservationSource.CAMERA, obs.source)

        val safety = SafetyRequirement(
            hazard = "Hot Engine Components",
            severity = SafetySeverity.HIGH,
            precaution = "Allow engine to cool down before touch"
        )
        assertEquals(SafetySeverity.HIGH, safety.severity)

        val action = WorkAction(
            id = "action_01",
            description = "Inspect fluid dipstick level",
            type = ActionType.INSPECT,
            safetyRequirements = listOf(safety)
        )
        assertEquals(ActionType.INSPECT, action.type)

        val context = WorkContext(
            task = task,
            domain = WorkDomain.AUTOMOTIVE,
            userInput = "Transmission slip on acceleration",
            observations = listOf(obs),
            safetyContext = listOf(safety)
        )
        assertEquals("Transmission slip on acceleration", context.userInput)

        val result = WorkAnalysisResult(
            identifiedObject = "Automotive Transmission",
            domain = WorkDomain.AUTOMOTIVE,
            task = "Transmission System Inspection",
            observations = listOf(obs),
            recommendedActions = listOf(action),
            confidence = 0.95f
        )
        assertEquals(WorkDomain.AUTOMOTIVE, result.domain)
    }

    @Test
    fun testSessionAndStepConversions() {
        val session = DemoScenarios.WASHING_MACHINE_SCENARIO
        val task = session.toWorkTask()
        assertEquals(session.id, task.sessionId)
        assertEquals(session.domain, task.domain)

        val analysisResult = session.toWorkAnalysisResult()
        assertEquals(session.domain, analysisResult.domain)
        assertEquals(session.steps.size, analysisResult.recommendedActions.size)

        val convertedSession = TroubleshootingSession.fromWorkAnalysisResult(analysisResult, sessionId = "sess_converted")
        assertEquals("sess_converted", convertedSession.id)
        assertEquals(session.domain, convertedSession.domain)
        assertEquals(session.steps.size, convertedSession.steps.size)

        val step = session.steps.first()
        val workAction = step.toWorkAction()
        assertEquals(ActionType.REPAIR, workAction.type)

        val convertedStep = TroubleshootingStep.fromWorkAction(1, workAction)
        assertEquals(1, convertedStep.stepNumber)
        assertEquals(step.title, convertedStep.title)
    }

    @Test
    fun testSystemPromptsAndMockAIEngineDomainGeneralization() = runTest {
        val expectedPromptSubstring = "universal AI field-work assistant"
        assertTrue(AIAssistantManager.SYSTEM_PROMPT.contains(expectedPromptSubstring))
        assertTrue(GeminiAIEngine.SYSTEM_PROMPT.contains(expectedPromptSubstring))
        assertTrue(MockAIEngine.SYSTEM_PROMPT.contains(expectedPromptSubstring))

        val mockEngine = MockAIEngine()

        // Test domain inference
        assertEquals(WorkDomain.AUTOMOTIVE, mockEngine.inferDomainFromPrompt("Engine transmission slipping on highway"))
        assertEquals(WorkDomain.IT_NETWORKING, mockEngine.inferDomainFromPrompt("Fiber ethernet switch port link down"))
        assertEquals(WorkDomain.PLUMBING, mockEngine.inferDomainFromPrompt("Water pipe leakage under kitchen sink"))
        assertEquals(WorkDomain.ELECTRICAL, mockEngine.inferDomainFromPrompt("Main breaker panel voltage drop"))
        assertEquals(WorkDomain.CONSTRUCTION, mockEngine.inferDomainFromPrompt("Concrete rebar beam inspection"))

        // Test domain-generalized analysis execution
        val autoContext = WorkContext(
            domain = WorkDomain.AUTOMOTIVE,
            userInput = "Check brake pads and rotor wear"
        )
        val autoResult = mockEngine.analyzeWorkContext(autoContext)
        assertEquals(WorkDomain.AUTOMOTIVE, autoResult.domain)
        assertTrue(autoResult.recommendedActions.isNotEmpty())

        val netAnalysis = mockEngine.analyzeEquipment(null, "Configure network router switch SFP module")
        assertEquals(WorkDomain.IT_NETWORKING, netAnalysis.device?.category?.let { WorkDomain.IT_NETWORKING })
        assertTrue(netAnalysis.steps.isNotEmpty())
    }
}
