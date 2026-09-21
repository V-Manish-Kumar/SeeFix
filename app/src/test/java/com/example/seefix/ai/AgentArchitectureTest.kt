package com.example.seefix.ai

import com.example.seefix.ai.agent.AgentActionType
import com.example.seefix.ai.agent.AgentState
import com.example.seefix.ai.agent.AgentStatus
import com.example.seefix.ai.agent.DocumentTool
import com.example.seefix.ai.agent.SeeFixAgentImpl
import com.example.seefix.ai.agent.StoreSearchTool
import com.example.seefix.ai.agent.ToolRegistry
import com.example.seefix.ai.context.WorkContextBuilderImpl
import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.WorkContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentArchitectureTest {

    private class TestMockLocalGemma(
        var mockResponseText: String = "ACTION: OBSERVE\nREASON: Inspecting machine"
    ) : LocalGemmaAIService(context = null, localModelManager = null) {
        override suspend fun isAvailable(): Boolean = true
        override fun getCapabilities(): AICapabilities = AICapabilities(
            supportsText = true,
            supportsImages = true
        )

        override suspend fun generate(request: AIRequest): AIResponse {
            return AIResponse(
                text = mockResponseText,
                modelName = "mock-gemma",
                providerName = "Mock Local Gemma"
            )
        }

        override fun stream(request: AIRequest): Flow<AIStreamEvent> {
            return flowOf(
                AIStreamEvent.Chunk(mockResponseText),
                AIStreamEvent.Completed(
                    AIResponse(
                        text = mockResponseText,
                        modelName = "mock-gemma",
                        providerName = "Mock Local Gemma"
                    )
                )
            )
        }
    }

    // 1. Test AgentState initial values and state transitions
    @Test
    fun testAgentStateInitialValuesAndTransitions() {
        val state = AgentState(sessionId = "test_session_1")

        assertEquals("test_session_1", state.sessionId)
        assertNull(state.currentTask)
        assertEquals(0, state.currentStepIndex)
        assertEquals(AgentStatus.IDLE, state.status)
        assertTrue(state.completedSteps.isEmpty())
        assertNull(state.pendingAction)
        assertTrue(state.observations.isEmpty())
        assertTrue(state.toolResults.isEmpty())
        assertEquals(0, state.iterationCount)
        assertEquals(5, state.maxIterations)
        assertTrue(state.errors.isEmpty())

        val updatedState = state.copy(
            status = AgentStatus.ANALYZING,
            iterationCount = 1,
            errors = listOf("Sample minor warning")
        )

        assertEquals(AgentStatus.ANALYZING, updatedState.status)
        assertEquals(1, updatedState.iterationCount)
        assertEquals(1, updatedState.errors.size)
    }

    // 2. Test maxIterations limit enforcement
    @Test
    fun testMaxIterationsLimitEnforcement() = runTest {
        val mockLocal = TestMockLocalGemma()
        val registry = AIProviderRegistry()
        val router = AIRouter(registry, mockLocal)
        val builder = WorkContextBuilderImpl()
        val toolRegistry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager)

        val workContext = WorkContext(userInput = "Inspect washing machine drive belt")
        val stateMaxReached = AgentState(
            sessionId = "session_max_iter",
            iterationCount = 5,
            maxIterations = 5
        )

        val result = agent.process(workContext, stateMaxReached)

        assertEquals(AgentActionType.STOP, result.decision.actionType)
        assertEquals(AgentStatus.STOPPED, result.state.status)
        assertTrue(result.decision.reason.contains("Maximum iteration limit reached"))
        assertEquals("Maximum iteration limit reached", result.explanation)
    }

    // 3. Test AgentDecision parsing and action types
    @Test
    fun testAgentDecisionParsingAndActionTypes() {
        val mockLocal = TestMockLocalGemma()
        val registry = AIProviderRegistry()
        val router = AIRouter(registry, mockLocal)
        val builder = WorkContextBuilderImpl()
        val toolRegistry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager)

        // OBSERVE
        val obsDecision = agent.parseDecision("ACTION: OBSERVE\nREASON: Visually verify motor")
        assertEquals(AgentActionType.OBSERVE, obsDecision.actionType)

        // RETRIEVE_KNOWLEDGE
        val ragDecision = agent.parseDecision("RETRIEVE_KNOWLEDGE: Check service manual section 4.2")
        assertEquals(AgentActionType.RETRIEVE_KNOWLEDGE, ragDecision.actionType)

        // REQUEST_TOOL
        val toolDecision = agent.parseDecision("REQUEST_TOOL: location_tool")
        assertEquals(AgentActionType.REQUEST_TOOL, toolDecision.actionType)
        assertEquals("location_tool", toolDecision.toolRequest?.toolName)

        // ASK_USER
        val askDecision = agent.parseDecision("ASK_USER: What is the equipment error code?")
        assertEquals(AgentActionType.ASK_USER, askDecision.actionType)

        // PROPOSE_ACTION
        val proposeDecision = agent.parseDecision("PROPOSE_ACTION: Replace worn drive belt")
        assertEquals(AgentActionType.PROPOSE_ACTION, proposeDecision.actionType)
        assertNotNull(proposeDecision.proposedAction)

        // COMPLETE
        val completeDecision = agent.parseDecision("COMPLETE: Problem resolved and verified safe")
        assertEquals(AgentActionType.COMPLETE, completeDecision.actionType)

        // STOP
        val stopDecision = agent.parseDecision("STOP: Hazard detected, halting diagnosis")
        assertEquals(AgentActionType.STOP, stopDecision.actionType)

        // JSON parsing test
        val jsonText = """
            {
              "actionType": "REQUEST_TOOL",
              "toolName": "store_search_tool",
              "toolArguments": {"query": "drive belt"},
              "reason": "Need nearby hardware store",
              "confidence": 0.95
            }
        """.trimIndent()
        val jsonDecision = agent.parseDecision(jsonText)
        assertEquals(AgentActionType.REQUEST_TOOL, jsonDecision.actionType)
        assertEquals("store_search_tool", jsonDecision.toolRequest?.toolName)
        assertEquals("drive belt", jsonDecision.toolRequest?.arguments?.get("query"))
    }

    // 4. Test ToolRegistry tool registration, lookup, and rejection of unknown tools
    @Test
    fun testToolRegistryAndUnknownToolRejection() = runTest {
        val registry = ToolRegistry()

        // Registered tools lookup
        assertNotNull(registry.getTool("location_tool"))
        assertNotNull(registry.getTool("store_search_tool"))
        assertNotNull(registry.getTool("sensor_tool"))
        assertNotNull(registry.getTool("rag_search_tool"))
        assertNotNull(registry.getTool("history_search_tool"))
        assertNotNull(registry.getTool("document_tool"))

        assertTrue(registry.hasTool("location_tool"))

        // Rejection of unknown tool
        assertFalse(registry.hasTool("execute_shell_command"))
        assertNull(registry.getTool("execute_shell_command"))

        val unknownResult = registry.validateAndExecute("execute_shell_command", emptyMap())
        assertFalse(unknownResult.isSuccess)
        assertTrue(unknownResult.errorMessage?.contains("Unknown tool: execute_shell_command") == true)

        // Unknown tool handling in agent process
        val mockLocal = TestMockLocalGemma(mockResponseText = "REQUEST_TOOL: execute_shell_command")
        val aiRegistry = AIProviderRegistry()
        val router = AIRouter(aiRegistry, mockLocal)
        val builder = WorkContextBuilderImpl()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, registry, prefManager)

        val workContext = WorkContext(userInput = "Run terminal command")
        val state = AgentState(sessionId = "test_unknown_tool")

        val result = agent.process(workContext, state)

        assertEquals(AgentStatus.ERROR, result.state.status)
        assertEquals(AgentActionType.ASK_USER, result.decision.actionType)
        assertTrue(result.state.errors.any { it.contains("Unknown tool requested: execute_shell_command") })
    }

    // 5. Test tool argument validation
    @Test
    fun testToolArgumentValidation() = runTest {
        val storeTool = StoreSearchTool()

        // Valid args
        val validResult = storeTool.validate(mapOf("query" to "capacitor"))
        assertTrue(validResult.isValid)
        assertNull(validResult.errorMessage)

        // Missing required arg
        val invalidResult = storeTool.validate(emptyMap())
        assertFalse(invalidResult.isValid)
        assertNotNull(invalidResult.errorMessage)
        assertTrue(invalidResult.errorMessage?.contains("Missing required parameter 'query'") == true)

        // DocumentTool valid & invalid
        val docTool = DocumentTool()
        assertTrue(docTool.validate(mapOf("docId" to "DOC_99")).isValid)
        assertFalse(docTool.validate(emptyMap()).isValid)
    }

    // 6. Test safety policy boundary (CRITICAL_STOP causes safe stop)
    @Test
    fun testSafetyPolicyBoundaryCriticalStop() = runTest {
        val mockLocal = TestMockLocalGemma()
        val registry = AIProviderRegistry()
        val router = AIRouter(registry, mockLocal)
        val builder = WorkContextBuilderImpl()
        val toolRegistry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager)

        val criticalSafetyContext = listOf(
            SafetyRequirement(
                hazard = "Exposed 240V Mains Cable",
                severity = SafetySeverity.CRITICAL_STOP,
                precaution = "Isolate circuit breaker immediately"
            )
        )
        val workContext = WorkContext(
            userInput = "Inspect wiring",
            safetyContext = criticalSafetyContext
        )
        val state = AgentState(sessionId = "test_critical_safety")

        val result = agent.process(workContext, state)

        assertEquals(AgentActionType.STOP, result.decision.actionType)
        assertEquals(AgentStatus.STOPPED, result.state.status)
        assertTrue(result.decision.reason.contains("Exposed 240V Mains Cable"))
        assertTrue(result.explanation.contains("Stopping agent operation immediately"))
    }

    // 7. Test provider-agnostic AIRouter integration with SeeFixAgent
    @Test
    fun testAIRouterIntegrationWithSeeFixAgent() = runTest {
        val mockLocal = TestMockLocalGemma(mockResponseText = "ACTION: RETRIEVE_KNOWLEDGE\nREASON: Search washing machine service manual")
        val registry = AIProviderRegistry()
        val router = AIRouter(registry, mockLocal)
        val builder = WorkContextBuilderImpl()
        val toolRegistry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager)

        val workContext = WorkContext(userInput = "Washing machine won't spin")
        val state = AgentState(sessionId = "test_router_integration")

        val result = agent.process(workContext, state)

        assertEquals(AgentActionType.RETRIEVE_KNOWLEDGE, result.decision.actionType)
        assertEquals(AgentStatus.ANALYZING, result.state.status)
        assertEquals(1, result.state.iterationCount)
        assertFalse(result.isCompleted)
    }
}
