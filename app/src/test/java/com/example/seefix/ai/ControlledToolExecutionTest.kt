package com.example.seefix.ai

import com.example.seefix.ai.agent.AgentActionType
import com.example.seefix.ai.agent.AgentState
import com.example.seefix.ai.agent.AgentStatus
import com.example.seefix.ai.agent.DocumentTool
import com.example.seefix.ai.agent.HistorySearchTool
import com.example.seefix.ai.agent.LocationTool
import com.example.seefix.ai.agent.PermissionPolicy
import com.example.seefix.ai.agent.RAGSearchTool
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
import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.core.AIRequest
import com.example.seefix.ai.core.AIResponse
import com.example.seefix.ai.core.AIStreamEvent
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.data.local.AIPreferenceManager
import com.example.seefix.data.repository.SessionHistoryRepositoryImpl
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.InventoryDataSource
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.location.StoreFinderRepository
import com.example.seefix.location.StoreSearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlledToolExecutionTest {

    private class MockGemma(
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

    private class SlowTimeoutTool : SeeFixTool {
        override val name: String = "slow_timeout_tool"
        override val description: String = "Simulates long running operation"
        override val permission: ToolPermission = ToolPermission.READ_ONLY
        override val inputSchema: ToolInputSchema = ToolInputSchema()

        override suspend fun validate(arguments: Map<String, String>): ToolValidationResult =
            ToolValidationResult(isValid = true)

        override suspend fun execute(arguments: Map<String, String>): ToolResult {
            delay(10000L)
            return ToolResult(toolName = name, isSuccess = true)
        }
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

    // 1. Test LocationTool execution with LocationManager
    @Test
    fun testLocationToolExecutionWithLocationManager() = runTest {
        val locationTool = LocationTool(locationManager = null)
        val result = locationTool.execute(mapOf("accuracy" to "5.0m"))

        assertFalse(result.isSuccess)
        assertEquals("location_tool", result.toolName)
        assertTrue(result.errorMessage?.contains("LOCATION_UNAVAILABLE") == true)
    }

    // 2. Test StoreSearchTool execution with StoreFinderRepository
    @Test
    fun testStoreSearchToolExecutionWithStoreFinderRepository() = runTest {
        val storeRepo = StoreFinderRepository()
        val storeTool = StoreSearchTool(storeFinderRepository = storeRepo)

        val result = storeTool.execute(
            mapOf(
                "query" to "Capacitor",
                "latitude" to "37.7749",
                "longitude" to "-122.4194",
                "radiusKm" to "10.0"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals("store_search_tool", result.toolName)
        assertNotNull(result.outputData["storeCount"])
        assertNotNull(result.outputData["storesJson"])
        assertNotNull(result.outputData["nearestStoreName"])
        assertNotNull(result.outputData["nearestDistanceKm"])
        assertNotNull(result.outputData["nearestStockStatus"])
        assertTrue((result.outputData["storeCount"]?.toInt() ?: 0) > 0)
    }

    // 3. Test SensorTool execution with SensorRepository
    @Test
    fun testSensorToolExecutionWithSensorRepository() = runTest {
        val sensorTool = SensorTool(sensorRepository = null)
        val result = sensorTool.execute(emptyMap())

        assertTrue(result.isSuccess)
        assertEquals("sensor_tool", result.toolName)
        assertNotNull(result.outputData["compassHeading"])
        assertNotNull(result.outputData["cardinalDirection"])
        assertNotNull(result.outputData["pitch"])
        assertNotNull(result.outputData["roll"])
        assertNotNull(result.outputData["motionState"])
        assertNotNull(result.outputData["isStable"])
    }

    // 4. Test RAGSearchTool execution with RagKnowledgeEngine
    @Test
    fun testRAGSearchToolExecutionWithRagKnowledgeEngine() = runTest {
        val ragEngine = RagKnowledgeEngine(context = null)
        val ragTool = RAGSearchTool(ragKnowledgeEngine = ragEngine)

        val result = ragTool.execute(mapOf("query" to "brake fluid", "maxChunks" to "2"))

        assertTrue(result.isSuccess)
        assertEquals("rag_search_tool", result.toolName)
        assertNotNull(result.outputData["chunkCount"])
        assertNotNull(result.outputData["chunksJson"])
        assertNotNull(result.outputData["topTitle"])
        assertNotNull(result.outputData["topContent"])
        assertTrue((result.outputData["chunkCount"]?.toInt() ?: 0) > 0)
    }

    // 5. Test HistorySearchTool execution with SessionHistoryRepository
    @Test
    fun testHistorySearchToolExecutionWithSessionHistoryRepository() = runTest {
        val historyRepo = SessionHistoryRepositoryImpl()
        val session = TroubleshootingSession(
            id = "test_hist_sess_1",
            device = HardwareDevice(id = "dev_1", name = "Washing Machine", model = "WM-9000", category = "Appliance"),
            detectedProblem = "Drain pump error"
        )
        historyRepo.saveSession(session)

        val historyTool = HistorySearchTool(sessionHistoryRepository = historyRepo)
        val result = historyTool.execute(mapOf("query" to "Washing Machine"))

        assertTrue(result.isSuccess)
        assertEquals("history_search_tool", result.toolName)
        assertNotNull(result.outputData["historyCount"])
        assertNotNull(result.outputData["historyJson"])
        assertNotNull(result.outputData["latestSessionDevice"])
        assertNotNull(result.outputData["latestSessionStatus"])
        assertTrue((result.outputData["historyCount"]?.toInt() ?: 0) > 0)
    }

    // 6. Test DocumentTool execution with RagKnowledgeEngine
    @Test
    fun testDocumentToolExecutionWithRagKnowledgeEngine() = runTest {
        val ragEngine = RagKnowledgeEngine(context = null)
        val docTool = DocumentTool(ragKnowledgeEngine = ragEngine)

        val result = docTool.execute(mapOf("documentId" to "DOC-AUTO-BRAKE-001"))

        assertTrue(result.isSuccess)
        assertEquals("document_tool", result.toolName)
        assertEquals("DOC-AUTO-BRAKE-001", result.outputData["documentId"])
        assertNotNull(result.outputData["title"])
        assertNotNull(result.outputData["source"])
        assertNotNull(result.outputData["sectionCount"])
        assertNotNull(result.outputData["sectionsJson"])
    }

    // 7. Test PermissionPolicy (READ_ONLY vs USER_CONFIRMATION_REQUIRED)
    @Test
    fun testPermissionPolicyAndConfirmationFlow() = runTest {
        val policy = PermissionPolicy()
        val readOnlyTool = LocationTool()
        val confirmationTool = ConfirmationActionTool()

        assertFalse(policy.requiresUserConfirmation(readOnlyTool, emptyMap()))
        assertTrue(policy.requiresUserConfirmation(readOnlyTool, mapOf("action" to "open_navigation")))
        assertTrue(policy.requiresUserConfirmation(confirmationTool, emptyMap()))

        val toolRegistry = ToolRegistry(listOf(readOnlyTool, confirmationTool))
        val executor = ToolExecutorImpl(toolRegistry, policy)
        val workContext = WorkContext()

        // Unconfirmed action requiring confirmation -> blocked
        val blockedResult = executor.execute(
            request = ToolRequest("confirmation_action_tool"),
            workContext = workContext,
            userConfirmedByUI = false
        )
        assertFalse(blockedResult.isSuccess)
        assertTrue(blockedResult.errorMessage?.contains("CONFIRMATION_REQUIRED") == true)

        // Confirmed action -> succeeds
        val confirmedResult = executor.execute(
            request = ToolRequest("confirmation_action_tool"),
            workContext = workContext,
            userConfirmedByUI = true
        )
        assertTrue(confirmedResult.isSuccess)
        assertEquals("ACTION_EXECUTED", confirmedResult.outputData["status"])
    }

    // 8. Test ToolExecutor argument validation, timeout handling, and unknown tool rejection
    @Test
    fun testToolExecutorValidationTimeoutAndUnknownToolRejection() = runTest {
        val slowTool = SlowTimeoutTool()
        val ragTool = RAGSearchTool()
        val toolRegistry = ToolRegistry(listOf(slowTool, ragTool))
        val executor = ToolExecutorImpl(toolRegistry)
        val workContext = WorkContext()

        // Argument validation failure
        val invalidArgResult = executor.execute(
            request = ToolRequest("rag_search_tool", arguments = emptyMap()),
            workContext = workContext
        )
        assertFalse(invalidArgResult.isSuccess)
        assertTrue(invalidArgResult.errorMessage?.contains("Missing required parameter 'query'") == true)

        // Unknown tool rejection
        val unknownResult = executor.execute(
            request = ToolRequest("unregistered_tool"),
            workContext = workContext
        )
        assertFalse(unknownResult.isSuccess)
        assertTrue(unknownResult.errorMessage?.contains("Unknown tool: unregistered_tool") == true)

        // Timeout handling
        val timeoutResult = executor.execute(
            request = ToolRequest("slow_timeout_tool"),
            workContext = workContext
        )
        assertFalse(timeoutResult.isSuccess)
        assertTrue(timeoutResult.errorMessage?.contains("timed out") == true)
    }

    // 9. Test safety policy boundary (CRITICAL_STOP blocks tool execution)
    @Test
    fun testSafetyPolicyBoundaryCriticalStopBlocksTool() = runTest {
        val jsonToolReq = """
            {
              "actionType": "REQUEST_TOOL",
              "toolName": "location_tool",
              "reason": "Requesting GPS location"
            }
        """.trimIndent()
        val mockGemma = MockGemma(responseText = jsonToolReq)
        val aiRegistry = AIProviderRegistry()
        val router = AIRouter(aiRegistry, mockGemma)
        val builder = WorkContextBuilderImpl()
        val toolRegistry = ToolRegistry(listOf(LocationTool()))
        val prefManager = AIPreferenceManager(context = null)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager)

        val criticalSafetyContext = listOf(
            SafetyRequirement(
                hazard = "Arc Flash Voltage Hazard",
                severity = SafetySeverity.CRITICAL_STOP,
                precaution = "Shut off main grid supply"
            )
        )
        val workContext = WorkContext(
            userInput = "Check electrical sub-station",
            safetyContext = criticalSafetyContext
        )
        val state = AgentState(sessionId = "test_critical_stop")

        val result = agent.process(workContext, state)

        assertEquals(AgentActionType.STOP, result.decision.actionType)
        assertEquals(AgentStatus.STOPPED, result.state.status)
        assertTrue(result.decision.reason.contains("Arc Flash Voltage Hazard"))
        assertTrue(result.state.toolResults.isEmpty())
    }

    // 10. Test full integration: WorkContext -> SeeFixAgent -> ToolRequest -> ToolExecutor -> Real Subsystem -> ToolResult -> AgentState
    @Test
    fun testFullIntegrationFlow() = runTest {
        val jsonToolReq = """
            {
              "actionType": "REQUEST_TOOL",
              "toolName": "rag_search_tool",
              "toolArguments": {
                "query": "brake fluid",
                "maxChunks": "1"
              },
              "reason": "Search technical manual"
            }
        """.trimIndent()

        val mockGemma = MockGemma(responseText = jsonToolReq)
        val aiRegistry = AIProviderRegistry()
        val router = AIRouter(aiRegistry, mockGemma)
        val builder = WorkContextBuilderImpl()

        val ragEngine = RagKnowledgeEngine(context = null)
        val ragTool = RAGSearchTool(ragEngine)
        val locationTool = LocationTool()
        val toolRegistry = ToolRegistry(listOf(ragTool, locationTool))

        val prefManager = AIPreferenceManager(context = null)
        val toolExecutor = ToolExecutorImpl(toolRegistry)
        val agent = SeeFixAgentImpl(router, builder, toolRegistry, prefManager, toolExecutor)

        val workContext = WorkContext(userInput = "How to service automotive brakes?")
        val initialState = AgentState(sessionId = "integration_test_session")

        val result = agent.process(workContext, initialState)

        assertEquals(AgentActionType.REQUEST_TOOL, result.decision.actionType)
        assertEquals(AgentStatus.WAITING_FOR_TOOL, result.state.status)
        assertEquals(1, result.state.toolResults.size)

        val executedResult = result.state.toolResults.first()
        assertEquals("rag_search_tool", executedResult.toolName)
        assertTrue(executedResult.isSuccess)
        assertNotNull(executedResult.outputData["topTitle"])
        assertNotNull(executedResult.outputData["topContent"])
        assertTrue((executedResult.outputData["chunkCount"]?.toInt() ?: 0) > 0)
    }

    // --- Phase 9.1 Controlled Tool Execution Correction & Audit Regression Tests ---

    @Test
    fun testStoreSearchToolWithMissingLocationReturnsLocationUnavailableAndNoHyderabadFallback() = runTest {
        val storeRepo = StoreFinderRepository()
        val storeTool = StoreSearchTool(storeFinderRepository = storeRepo, locationManager = null)

        val result = storeTool.execute(mapOf("query" to "Capacitor"))
        assertFalse(result.isSuccess)
        assertEquals("LOCATION_UNAVAILABLE: Current device location is required to perform nearby store search.", result.errorMessage)
        assertFalse(result.errorMessage?.contains("Hyderabad") == true)
    }

    @Test
    fun testStoreFinderRepositoryZeroCoordinatesReturnsError() = runTest {
        val storeRepo = StoreFinderRepository()
        val result = storeRepo.findNearbyStores(0.0, 0.0, "Capacitor")
        assertTrue(result is StoreSearchResult.Error)
        val error = result as StoreSearchResult.Error
        assertTrue(error.message.contains("LOCATION_UNAVAILABLE"))
    }

    @Test
    fun testAiSuppliedUserConfirmedIsIgnoredByPermissionPolicyAndToolExecutor() = runTest {
        val confirmationTool = ConfirmationActionTool()
        val toolRegistry = ToolRegistry(listOf(confirmationTool))
        val policy = PermissionPolicy()
        val executor = ToolExecutorImpl(toolRegistry, policy)
        val workContext = WorkContext()

        // AI JSON / prompt arguments attempt to pass userConfirmed: true
        val aiInjectedRequest = ToolRequest(
            toolName = "confirmation_action_tool",
            arguments = mapOf("userConfirmed" to "true", "userConfirmedByUI" to "true")
        )

        // Default call from UI layer without explicit user confirmation (userConfirmedByUI = false)
        val blockedResult = executor.execute(
            request = aiInjectedRequest,
            workContext = workContext,
            userConfirmedByUI = false
        )
        assertFalse(blockedResult.isSuccess)
        assertEquals("CONFIRMATION_REQUIRED: Action requires explicit user confirmation.", blockedResult.errorMessage)

        // Valid call from UI layer with explicit confirmation button press (userConfirmedByUI = true)
        val allowedResult = executor.execute(
            request = aiInjectedRequest,
            workContext = workContext,
            userConfirmedByUI = true
        )
        assertTrue(allowedResult.isSuccess)
    }

    @Test
    fun testDocumentToolRejectsArbitraryFilesystemPaths() = runTest {
        val docTool = DocumentTool()

        val invalidPaths = listOf(
            "/sdcard/passwords.txt",
            "/system/etc/hosts",
            "file:///etc/passwd",
            "../secret.txt",
            "/data/user/0/com.example/databases/db"
        )

        for (path in invalidPaths) {
            val valResult = docTool.validate(mapOf("documentId" to path))
            assertFalse("Validation should fail for: $path", valResult.isValid)
            assertEquals("PATH_TRAVERSAL_REJECTED: Arbitrary file path access is strictly prohibited.", valResult.errorMessage)

            val execResult = docTool.execute(mapOf("documentId" to path))
            assertFalse("Execution should fail for: $path", execResult.isSuccess)
            assertEquals("PATH_TRAVERSAL_REJECTED: Arbitrary file path access is strictly prohibited.", execResult.errorMessage)
        }
    }

    @Test
    fun testStoreLocationLabelsSimulatedInventorySources() = runTest {
        val storeRepo = StoreFinderRepository()
        val searchResult = storeRepo.findNearbyStores(37.7749, -122.4194)

        assertTrue(searchResult is StoreSearchResult.Success)
        val stores = (searchResult as StoreSearchResult.Success).stores
        assertTrue(stores.isNotEmpty())

        for (enriched in stores) {
            assertEquals(InventoryDataSource.SIMULATED_DEMO, enriched.store.inventorySource)
            assertTrue(enriched.stockItems.all { it.stockCountText.contains("SIMULATED") })
        }
    }
}
