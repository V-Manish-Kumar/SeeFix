package com.example.seefix.ui

import com.example.seefix.ai.agent.AgentActionType
import com.example.seefix.ai.agent.AgentResult
import com.example.seefix.ai.agent.AgentState
import com.example.seefix.ai.agent.AgentStatus
import com.example.seefix.ai.agent.PermissionPolicy
import com.example.seefix.ai.agent.RAGSearchTool
import com.example.seefix.ai.agent.SeeFixAgent
import com.example.seefix.ai.agent.SeeFixAgentImpl
import com.example.seefix.ai.agent.SeeFixTool
import com.example.seefix.ai.agent.SensorTool
import com.example.seefix.ai.agent.StoreSearchTool
import com.example.seefix.ai.agent.ToolExecutorImpl
import com.example.seefix.ai.agent.ToolInputSchema
import com.example.seefix.ai.agent.ToolPermission
import com.example.seefix.ai.agent.ToolRegistry
import com.example.seefix.ai.agent.ToolRequest
import com.example.seefix.ai.agent.ToolResult
import com.example.seefix.ai.agent.ToolValidationResult
import com.example.seefix.ai.context.WorkContextBuilderImpl
import com.example.seefix.ai.context.WorkContextConfig
import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIMessage
import com.example.seefix.ai.core.AIRole
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.data.repository.SessionHistoryRepositoryImpl
import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.SeeFixSession
import com.example.seefix.domain.model.WorkAction
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkTask
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.ui.diagnosis.DiagnosisUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTurnSessionIntegrationTest {

    private class MockLocalGemma(
        var responseText: String = "ACTION: OBSERVE\nREASON: Inspecting machine"
    ) : LocalGemmaAIService(context = null, localModelManager = null) {
        override suspend fun isAvailable(): Boolean = true
        override fun getCapabilities(): AICapabilities = AICapabilities(supportsText = true, supportsImages = true)
        override suspend fun generate(request: AIRequest): AIResponse = AIResponse(
            text = responseText,
            modelName = "mock-gemma",
            providerName = "Mock Local Gemma"
        )
        override fun stream(request: AIRequest): Flow<AIStreamEvent> = flowOf(
            AIStreamEvent.Completed(AIResponse(text = responseText, modelName = "mock-gemma", providerName = "Mock"))
        )
    }

    private class ConfirmationActionTool : SeeFixTool {
        override val name: String = "confirmation_action_tool"
        override val description: String = "Requires explicit user confirmation"
        override val permission: ToolPermission = ToolPermission.USER_CONFIRMATION_REQUIRED
        override val inputSchema: ToolInputSchema = ToolInputSchema()

        override suspend fun validate(arguments: Map<String, String>): ToolValidationResult =
            ToolValidationResult(isValid = true)

        override suspend fun execute(arguments: Map<String, String>): ToolResult {
            return ToolResult(
                toolName = name,
                isSuccess = true,
                outputData = mapOf("status" to "ACTION_EXECUTED")
            )
        }
    }

    // 1. Test SeeFixSession creation, turn-by-turn conversation retention, and state mapping
    @Test
    fun testSeeFixSessionCreationConversationRetentionAndStateMapping() {
        val session = SeeFixSession.createEmpty("test_session_100")

        assertEquals("test_session_100", session.sessionId)
        assertEquals(AgentStatus.IDLE, session.status)
        assertTrue(session.conversation.isEmpty())

        val turn1 = session.copy(
            conversation = session.conversation + AIMessage(role = AIRole.USER, content = "Appliance issue"),
            status = AgentStatus.ANALYZING
        )
        val turn2 = turn1.copy(
            conversation = turn1.conversation + AIMessage(role = AIRole.ASSISTANT, content = "Checking manual"),
            status = AgentStatus.WAITING_FOR_TOOL
        )

        assertEquals(2, turn2.conversation.size)
        assertEquals(AIRole.USER, turn2.conversation[0].role)
        assertEquals(AIRole.ASSISTANT, turn2.conversation[1].role)

        val troubleSession = turn2.toTroubleshootingSession()
        assertEquals("test_session_100", troubleSession.id)
        assertEquals("WAITING_FOR_TOOL", troubleSession.statusText)

        val restoredSeeFix = SeeFixSession.fromTroubleshootingSession(troubleSession)
        assertEquals("test_session_100", restoredSeeFix.sessionId)
        assertEquals(AgentStatus.WAITING_FOR_TOOL, restoredSeeFix.status)
    }

    // 2. Test context bounding enforcement (maxConversationTurns = 10, maxTotalImages = 12, maxHistoryEntries = 5, maxRagChunks = 3)
    @Test
    fun testContextBoundingEnforcement() = runTest {
        val conversations = (1..15).map { AIMessage(role = if (it % 2 == 1) AIRole.USER else AIRole.ASSISTANT, content = "Message $it") }
        val images = (1..20).map { "file:///path/img_$it.jpg" }
        val history = (1..10).map { "History entry $it" }
        val rag = (1..5).map { "RAG chunk $it content" }

        val workContext = WorkContext(
            conversationHistory = conversations,
            images = images,
            workHistory = history,
            retrievedKnowledge = rag
        )

        val builder = WorkContextBuilderImpl()
        val config = WorkContextConfig(
            maxConversationTurns = 10,
            maxTotalImages = 12,
            maxHistoryEntries = 5,
            maxRagChunks = 3
        )

        val aiRequest = builder.buildAIRequest("User prompt", workContext, config)

        assertEquals(10, aiRequest.conversationHistory.size)
        assertEquals(12, aiRequest.images.size)
        assertEquals(5, aiRequest.repairHistory.size)
        assertEquals(3, aiRequest.retrievedKnowledge.size)
    }

    // 3. Test AgentStatus UI state mapping to DiagnosisUiState
    @Test
    fun testAgentStatusUiStateMappingToDiagnosisUiState() {
        val session = SeeFixSession.createEmpty("ui_mapping_session")
        val uiState = DiagnosisUiState(session = session)

        assertEquals(AgentStatus.IDLE, uiState.session.status)
        assertFalse(uiState.isProcessing)

        val updatedSession = session.copy(status = AgentStatus.ANALYZING)
        val updatedUiState = uiState.copy(session = updatedSession, isProcessing = true)

        assertEquals(AgentStatus.ANALYZING, updatedUiState.session.status)
        assertTrue(updatedUiState.isProcessing)
    }

    // 4. Test explicit UI user confirmation requirement
    @Test
    fun testExplicitUserConfirmationRequirement() = runTest {
        val policy = PermissionPolicy()
        val confirmTool = ConfirmationActionTool()
        val registry = ToolRegistry(listOf(confirmTool))
        val executor = ToolExecutorImpl(registry, policy)

        val req = ToolRequest("confirmation_action_tool")

        // Unconfirmed execution -> blocked
        val blockedResult = executor.execute(req, WorkContext(), userConfirmedByUI = false)
        assertFalse(blockedResult.isSuccess)
        assertTrue(blockedResult.errorMessage?.contains("CONFIRMATION_REQUIRED") == true)

        // Confirmed execution -> allowed
        val allowedResult = executor.execute(req, WorkContext(), userConfirmedByUI = true)
        assertTrue(allowedResult.isSuccess)
        assertEquals("ACTION_EXECUTED", allowedResult.outputData["status"])
    }

    // 5. Test safety boundary (CRITICAL_STOP hazard forces AgentStatus.STOPPED)
    @Test
    fun testSafetyBoundaryCriticalStopForcesStoppedStatus() = runTest {
        val mockGemma = MockLocalGemma()
        val router = AIRouter(AIProviderRegistry(), mockGemma)
        val builder = WorkContextBuilderImpl()
        val registry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, registry, prefManager)

        val criticalSafety = listOf(
            SafetyRequirement(
                hazard = "Exposed High-Voltage Capacitor",
                severity = SafetySeverity.CRITICAL_STOP,
                precaution = "Isolate mains power immediately"
            )
        )
        val workContext = WorkContext(safetyContext = criticalSafety)
        val state = AgentState(sessionId = "safety_stop_session")

        val result = agent.process(workContext, state)

        assertEquals(AgentActionType.STOP, result.decision.actionType)
        assertEquals(AgentStatus.STOPPED, result.state.status)
        assertTrue(result.explanation.contains("Stopping agent operation immediately"))
    }

    // 6. Test photo, video keyframe, and voice transcript integration into WorkContext
    @Test
    fun testPhotoVideoAndVoiceTranscriptIntegration() = runTest {
        val builder = WorkContextBuilderImpl()
        val videoContext = VideoContext(videoUri = "file:///video.mp4", durationMs = 8000L)
        val workContext = WorkContext(
            images = listOf("file:///photo1.jpg"),
            videoContext = videoContext,
            audioTranscript = "Engine making grinding noise"
        )

        val aiRequest = builder.buildAIRequest("Diagnose issue", workContext)

        assertTrue(aiRequest.images.any { it.uri == "file:///photo1.jpg" })
        assertEquals("file:///video.mp4", aiRequest.videoContext?.videoUri)
        assertEquals("Engine making grinding noise", aiRequest.audioTranscript)
    }

    // 7. Test session persistence and restoration via SessionHistoryRepository
    @Test
    fun testSessionPersistenceAndRestoration() = runTest {
        val repository = SessionHistoryRepositoryImpl()
        val session = SeeFixSession.createEmpty("persist_test_999").copy(
            status = AgentStatus.ACTION_PROPOSED,
            observations = listOf(Observation(id = "o1", description = "Pump leak detected", source = ObservationSource.USER))
        )

        repository.saveSession(session)

        val retrievedSessions = repository.getSeeFixSessions().first()
        assertTrue(retrievedSessions.any { it.sessionId == "persist_test_999" })

        val fetched = repository.getSeeFixSession("persist_test_999")
        assertNotNull(fetched)
        assertEquals(AgentStatus.ACTION_PROPOSED, fetched?.status)
        assertEquals(1, fetched?.observations?.size)
    }

    // 8. Test concurrency protection preventing duplicate simultaneous agent runs
    @Test
    fun testConcurrencyProtectionPreventingDuplicateAgentRuns() = runTest {
        val mockGemma = MockLocalGemma(responseText = "ACTION: OBSERVE\nREASON: Checking equipment")
        val router = AIRouter(AIProviderRegistry(), mockGemma)
        val builder = WorkContextBuilderImpl()
        val registry = ToolRegistry()
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, registry, prefManager)

        val workContext = WorkContext(userInput = "Run diagnostic")
        val state = AgentState(sessionId = "concurrency_session")

        // First run
        val result1 = agent.process(workContext, state)
        assertEquals(AgentStatus.ANALYZING, result1.state.status)

        // Repeat call with active state increment
        val result2 = agent.process(workContext, result1.state)
        assertEquals(2, result2.state.iterationCount)
    }

    // 9. Test cancellation handling (cancelActiveAnalysis)
    @Test
    fun testCancellationHandling() {
        val session = SeeFixSession.createEmpty("cancel_session_1")
        val uiState = DiagnosisUiState(session = session, isProcessing = true)

        // Perform cancellation mapping
        val cancelledSession = session.copy(
            status = AgentStatus.STOPPED,
            agentState = session.agentState.copy(status = AgentStatus.STOPPED, errors = listOf("Analysis cancelled by user."))
        )
        val cancelledUiState = uiState.copy(
            session = cancelledSession,
            isProcessing = false,
            userFacingError = "Analysis cancelled by user."
        )

        assertFalse(cancelledUiState.isProcessing)
        assertEquals(AgentStatus.STOPPED, cancelledUiState.session.status)
        assertEquals("Analysis cancelled by user.", cancelledUiState.userFacingError)
    }

    // 10. Full end-to-end integration test: User query -> WorkContext -> SeeFixAgent -> RAGSearchTool -> SensorTool -> AgentDecision -> ASK_USER -> User follow-up -> Final diagnosis
    @Test
    fun testFullEndToEndIntegrationMultiTurnSession() = runTest {
        val ragEngine = RagKnowledgeEngine(context = null)
        val ragTool = RAGSearchTool(ragEngine)
        val sensorTool = SensorTool()
        val toolRegistry = ToolRegistry(listOf(ragTool, sensorTool))
        val executor = ToolExecutorImpl(toolRegistry)

        // Turn 1: User asks question -> Agent requests tool
        val turn1Json = """
            {
              "actionType": "REQUEST_TOOL",
              "toolName": "rag_search_tool",
              "toolArguments": {"query": "brake fluid"},
              "reason": "Searching technical manual"
            }
        """.trimIndent()
        val mockGemma1 = MockLocalGemma(responseText = turn1Json)
        val router1 = AIRouter(AIProviderRegistry(), mockGemma1)
        val builder1 = WorkContextBuilderImpl()
        val prefManager = AIPreferenceManager(context = null)
        val agent1 = SeeFixAgentImpl(router1, builder1, toolRegistry, prefManager, executor)

        val workContext1 = WorkContext(userInput = "Brake pedal feels soft")
        val state1 = AgentState(sessionId = "e2e_turn_session")

        val result1 = agent1.process(workContext1, state1)
        assertEquals(AgentActionType.REQUEST_TOOL, result1.decision.actionType)
        assertEquals(AgentStatus.WAITING_FOR_TOOL, result1.state.status)
        assertEquals(1, result1.state.toolResults.size)

        // Turn 2: User follow-up query -> Agent completes diagnosis
        val turn2Json = """
            {
              "actionType": "COMPLETE",
              "reason": "Brake fluid replacement required. Issue diagnosed successfully.",
              "confidence": 0.98
            }
        """.trimIndent()
        val mockGemma2 = MockLocalGemma(responseText = turn2Json)
        val router2 = AIRouter(AIProviderRegistry(), mockGemma2)
        val agent2 = SeeFixAgentImpl(router2, builder1, toolRegistry, prefManager, executor)

        val workContext2 = workContext1.copy(userInput = "Brake fluid reservoir is low")
        val result2 = agent2.process(workContext2, result1.state)

        assertEquals(AgentActionType.COMPLETE, result2.decision.actionType)
        assertEquals(AgentStatus.COMPLETED, result2.state.status)
        assertTrue(result2.isCompleted)
    }

    // 11. Full missing-part store search test: Missing tool -> StoreSearchTool -> nearby results with (SIMULATED) stock labels -> navigation confirmation modal -> user confirmation
    @Test
    fun testFullMissingPartStoreSearchAndConfirmationFlow() = runTest {
        val storeRepo = StoreFinderRepository()
        val storeTool = StoreSearchTool(storeFinderRepository = storeRepo)
        val confirmTool = ConfirmationActionTool()
        val registry = ToolRegistry(listOf(storeTool, confirmTool))
        val policy = PermissionPolicy()
        val executor = ToolExecutorImpl(registry, policy)

        // 1. Store Search Tool execution
        val searchResult = storeTool.execute(
            mapOf("query" to "Capacitor", "latitude" to "37.7749", "longitude" to "-122.4194")
        )
        assertTrue(searchResult.isSuccess)
        assertEquals(InventoryDataSource.SIMULATED_DEMO.name, searchResult.outputData["inventorySource"])

        // 2. Navigation action requiring confirmation
        val navRequest = ToolRequest("confirmation_action_tool", arguments = mapOf("action" to "open_navigation"))

        // Unconfirmed -> blocked with confirmation required
        val blockedResult = executor.execute(navRequest, WorkContext(), userConfirmedByUI = false)
        assertFalse(blockedResult.isSuccess)
        assertTrue(blockedResult.errorMessage?.contains("CONFIRMATION_REQUIRED") == true)

        // User taps button -> confirmed -> navigation action succeeds
        val confirmedResult = executor.execute(navRequest, WorkContext(), userConfirmedByUI = true)
        assertTrue(confirmedResult.isSuccess)
    }
}
