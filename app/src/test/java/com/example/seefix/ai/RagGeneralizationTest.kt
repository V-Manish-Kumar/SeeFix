package com.example.seefix.ai

import com.example.seefix.ai.context.WorkContextBuilderImpl
import com.example.seefix.ai.core.AIProviderConfig
import com.example.seefix.ai.core.AIProviderType
import com.example.seefix.ai.core.MachineInfoPayload
import com.example.seefix.ai.local.LocalGemmaAIService
import com.example.seefix.ai.providers.AIProviderRegistry
import com.example.seefix.ai.providers.GeminiAIService
import com.example.seefix.ai.rag.DocumentSection
import com.example.seefix.ai.rag.DocumentType
import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.ai.rag.RagQueryContext
import com.example.seefix.ai.rag.TechnicalDocument
import com.example.seefix.ai.router.AIMode
import com.example.seefix.ai.router.AIRouter
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RagGeneralizationTest {

    private lateinit var ragEngine: RagKnowledgeEngine

    @Before
    fun setUp() {
        ragEngine = RagKnowledgeEngine(context = null)
    }

    @Test
    fun testEmptyKnowledgeBase_returnsEmptyResult() {
        ragEngine.clearKnowledgeBase()
        val results = ragEngine.retrieve(RagQueryContext(query = "brake fluid level"))
        assertTrue("Expected empty results from cleared knowledge base", results.isEmpty())
    }

    @Test
    fun testIngestionAndRetrievalAcrossMultipleDomains() {
        // Test pre-loaded multi-domain knowledge retrieval
        val autoResults = ragEngine.retrieve(RagQueryContext(query = "brake fluid rotor bleed"))
        assertTrue("Automotive domain query should yield matching chunks", autoResults.isNotEmpty())
        assertEquals(WorkDomain.AUTOMOTIVE, autoResults.first().domain)

        val elecResults = ragEngine.retrieve(RagQueryContext(query = "induction motor insulation megger 3-phase"))
        assertTrue("Electrical domain query should yield matching chunks", elecResults.isNotEmpty())
        assertEquals(WorkDomain.ELECTRICAL, elecResults.first().domain)

        val itResults = ragEngine.retrieve(RagQueryContext(query = "router wan interface vlan reset"))
        assertTrue("IT/Networking domain query should yield matching chunks", itResults.isNotEmpty())
        assertEquals(WorkDomain.IT_NETWORKING, itResults.first().domain)

        val plumbResults = ragEngine.retrieve(RagQueryContext(query = "sump pump check valve impeller"))
        assertTrue("Plumbing domain query should yield matching chunks", plumbResults.isNotEmpty())
        assertEquals(WorkDomain.PLUMBING, plumbResults.first().domain)

        val appResults = ragEngine.retrieve(RagQueryContext(query = "washing machine drain pump capacitor e20"))
        assertTrue("Appliance domain query should yield matching chunks", appResults.isNotEmpty())
        assertEquals(WorkDomain.APPLIANCE, appResults.first().domain)

        // Dynamic runtime document ingestion test for Mechanical domain
        val mechanicalDoc = TechnicalDocument(
            documentId = "DOC-MECH-GEAR-001",
            title = "Industrial Gearbox & Shaft Alignment Manual",
            source = "Mechanical_Gearbox_Manual.json",
            domain = WorkDomain.MECHANICAL,
            documentType = DocumentType.REPAIR_GUIDE,
            manufacturer = "Flender",
            model = "GBX-2000",
            equipmentType = "Industrial Gearbox",
            component = "Helical Gear & Drive Shaft",
            safetyLevel = SafetySeverity.HIGH,
            sections = listOf(
                DocumentSection(
                    sectionId = "sec_shaft_align",
                    sectionTitle = "Drive Shaft Laser Alignment Procedure",
                    content = "Mount dual laser alignment sensors on driving and driven shafts. Adjust shim stack beneath motor mounting feet to achieve angular offset within 0.03 mm.",
                    pageNumber = 1,
                    problemKeywords = listOf("shaft", "alignment", "gearbox", "vibration", "shim"),
                    steps = listOf("Attach laser sensors to shafts.", "Adjust motor shim plates.")
                )
            )
        )

        ragEngine.ingestDocument(mechanicalDoc)

        val mechResults = ragEngine.retrieve(RagQueryContext(query = "gearbox drive shaft laser alignment shim"))
        assertTrue("Ingested Mechanical document should yield matching chunks", mechResults.isNotEmpty())
        assertEquals(WorkDomain.MECHANICAL, mechResults.first().domain)
        assertEquals("DOC-MECH-GEAR-001", mechResults.first().documentId)
    }

    @Test
    fun testDomainFiltering_prioritizesMatchingDomain() {
        val queryWithDomain = RagQueryContext(
            query = "brake valve bleed procedure",
            domain = WorkDomain.AUTOMOTIVE
        )
        val queryWithoutDomain = RagQueryContext(
            query = "brake valve bleed procedure"
        )

        val domainFilteredResults = ragEngine.retrieve(queryWithDomain)
        val neutralResults = ragEngine.retrieve(queryWithoutDomain)

        assertTrue(domainFilteredResults.isNotEmpty())
        val topChunkWithDomain = domainFilteredResults.first()
        val topChunkNeutral = neutralResults.first()

        assertEquals(WorkDomain.AUTOMOTIVE, topChunkWithDomain.domain)
        assertTrue(
            "Domain-matched chunk score should be higher than neutral score",
            topChunkWithDomain.relevanceScore > topChunkNeutral.relevanceScore
        )
    }

    @Test
    fun testEquipmentAndComponentMatchingBoost() {
        val baseQuery = RagQueryContext(query = "brake rotor bleed")
        val boostedQuery = RagQueryContext(
            query = "brake rotor bleed",
            equipmentType = "Brake System",
            component = "Brake Caliper & Bleeder"
        )

        val baseResults = ragEngine.retrieve(baseQuery)
        val boostedResults = ragEngine.retrieve(boostedQuery)

        assertTrue(baseResults.isNotEmpty())
        assertTrue(boostedResults.isNotEmpty())

        val baseScore = baseResults.first().relevanceScore
        val boostedScore = boostedResults.first().relevanceScore

        assertTrue(
            "Equipment/Component boost should increase total relevance score by +0.25f",
            boostedScore >= baseScore + 0.25f
        )
    }

    @Test
    fun testManufacturerAndModelMatchingBoost() {
        val baseQuery = RagQueryContext(query = "motor winding insulation")
        val boostedQuery = RagQueryContext(
            query = "motor winding insulation",
            manufacturer = "Siemens",
            model = "1LE1001-1AC4"
        )

        val baseResults = ragEngine.retrieve(baseQuery)
        val boostedResults = ragEngine.retrieve(boostedQuery)

        assertTrue(baseResults.isNotEmpty())
        assertTrue(boostedResults.isNotEmpty())

        val baseScore = baseResults.first().relevanceScore
        val boostedScore = boostedResults.first().relevanceScore

        assertTrue(
            "Manufacturer/Model boost should increase total relevance score by +0.2f",
            boostedScore >= baseScore + 0.2f
        )
    }

    @Test
    fun testDocumentTypePreservation() {
        val docWithTypes = TechnicalDocument(
            documentId = "DOC-TYPES-TEST-001",
            title = "Multi-Type Test Manual",
            source = "Test_Manual.json",
            domain = WorkDomain.ELECTRICAL,
            documentType = DocumentType.WIRING_DIAGRAM,
            manufacturer = "TestCo",
            sections = listOf(
                DocumentSection(
                    sectionId = "sec_schematic_1",
                    sectionTitle = "3-Phase Power Wiring Diagram",
                    content = "Detailed L1 L2 L3 power line wiring schematic diagram."
                )
            )
        )

        ragEngine.ingestDocument(docWithTypes)

        val results = ragEngine.retrieve(RagQueryContext(query = "3-Phase Power Wiring Diagram", documentType = DocumentType.WIRING_DIAGRAM))
        assertTrue(results.isNotEmpty())

        val chunk = results.first { it.documentId == "DOC-TYPES-TEST-001" }
        assertEquals(DocumentType.WIRING_DIAGRAM, chunk.documentType)
    }

    @Test
    fun testSafetyKnowledgeRetrieval() {
        val safetyFilterQuery = RagQueryContext(
            query = "high voltage shock hazard",
            safetyFilterOnly = true
        )

        val results = ragEngine.retrieve(safetyFilterQuery)
        assertTrue(results.isNotEmpty())

        val topSafetyChunk = results.first()
        assertTrue(
            "Safety filter query should prioritize high safety severity or chunks with safety warnings",
            topSafetyChunk.safetyLevel != SafetySeverity.LOW ||
                    !topSafetyChunk.metadata["safetyWarnings"].isNullOrBlank() ||
                    topSafetyChunk.documentType == DocumentType.SAFETY_DOCUMENT
        )
    }

    @Test
    fun testProvenanceMetadataRetention() {
        val results = ragEngine.retrieve(RagQueryContext(query = "sump pump check valve"))
        assertTrue(results.isNotEmpty())

        val chunk = results.first()
        assertNotNull("Document ID must be retained", chunk.documentId)
        assertTrue("Document title must be retained", chunk.documentTitle.isNotBlank())
        assertTrue("Section title must be retained", chunk.sectionTitle.isNotBlank())
        assertNotNull("Page number should be retained", chunk.pageNumber)
        assertTrue("Source file name must be retained", chunk.source.isNotBlank())
    }

    @Test
    fun testPhase6Integration_RagChunkToWorkContextToAIRouter() = runTest {
        // 1. Retrieve RAG chunk from RagKnowledgeEngine
        val chunks = ragEngine.retrieve(RagQueryContext(query = "brake fluid leak DOT4", domain = WorkDomain.AUTOMOTIVE))
        assertTrue(chunks.isNotEmpty())
        val topChunk = chunks.first()

        // 2. Wrap chunk into WorkContext
        val workContext = WorkContext(
            domain = WorkDomain.AUTOMOTIVE,
            userInput = "Brake fluid leaking near master cylinder",
            machineInfo = MachineInfoPayload(
                deviceName = topChunk.manufacturer ?: "Automotive Vehicle",
                modelNumber = topChunk.model ?: "ABS-Pro",
                category = "Brake System"
            ),
            retrievedKnowledge = listOf(
                "[${topChunk.documentTitle} - ${topChunk.sectionTitle} (p.${topChunk.pageNumber})] ${topChunk.content}"
            )
        )

        // 3. Assemble AIRequest using WorkContextBuilderImpl
        val builder = WorkContextBuilderImpl()
        val aiRequest = builder.buildAIRequest(
            userPrompt = "Diagnose brake fluid leak",
            workContext = workContext
        )

        assertNotNull(aiRequest)
        assertEquals("Diagnose brake fluid leak", aiRequest.prompt)
        assertTrue("AIRequest should contain retrieved RAG knowledge", aiRequest.retrievedKnowledge.isNotEmpty())
        assertTrue("System prompt should contain RAG section marker", aiRequest.systemPrompt?.contains("[RETRIEVED MANUAL KNOWLEDGE]") == true)

        // 4. Verify AIRouter integration without network/API calls
        val providerConfig = AIProviderConfig(
            providerType = AIProviderType.GOOGLE_GEMINI,
            name = "Gemini",
            apiKey = "", // Empty API key ensures offline fallback path
            model = "gemini-2.5-flash"
        )
        val registry = AIProviderRegistry().apply {
            registerProvider(GeminiAIService(providerConfig))
            setActiveCloudProviderType(AIProviderType.GOOGLE_GEMINI)
        }
        val localGemmaService = LocalGemmaAIService(context = null)
        val aiRouter = AIRouter(registry, localGemmaService)

        // Generate response using LOCAL_ONLY mode (or default)
        val response = aiRouter.generate(aiRequest, mode = AIMode.LOCAL_ONLY)
        assertNotNull(response)
        assertTrue("AIRouter should produce non-null response text or error message", response.text.isNotBlank() || response.error != null)
    }
}
