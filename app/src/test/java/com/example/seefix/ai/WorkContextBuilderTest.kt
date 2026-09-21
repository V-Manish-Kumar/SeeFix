package com.example.seefix.ai

import com.example.seefix.ai.context.WorkContextBuilder
import com.example.seefix.ai.context.WorkContextBuilderImpl
import com.example.seefix.ai.context.WorkContextConfig
import com.example.seefix.ai.core.AIImage
import com.example.seefix.ai.core.AIMessage
import com.example.seefix.ai.core.AIRole
import com.example.seefix.ai.core.LocationPayload
import com.example.seefix.ai.core.MachineInfoPayload
import com.example.seefix.ai.core.SensorDataPayload
import com.example.seefix.ai.core.VideoContext
import com.example.seefix.domain.model.Observation
import com.example.seefix.domain.model.ObservationSource
import com.example.seefix.domain.model.SafetyRequirement
import com.example.seefix.domain.model.SafetySeverity
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.WorkContext
import com.example.seefix.domain.model.WorkDomain
import com.example.seefix.domain.model.WorkTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkContextBuilderTest {

    private val builder: WorkContextBuilder = WorkContextBuilderImpl()

    @Test
    fun testEmptyContext_textPromptOnly() = runTest {
        val userPrompt = "How do I start troubleshooting?"
        val emptyContext = WorkContext()

        val request = builder.buildAIRequest(userPrompt, emptyContext)

        assertEquals(userPrompt, request.prompt)
        assertTrue(request.images.isEmpty())
        assertNotNull(request.systemPrompt)
        assertTrue(request.systemPrompt?.contains("[TASK & DOMAIN]") == true)

        val reqs = request.computeRequirements()
        assertTrue(reqs.requiresText)
        assertFalse(reqs.requiresImages)
        assertEquals(0, reqs.requiredImageCount)
    }

    @Test
    fun testPhotoImageIncorporation() = runTest {
        val photos = listOf("file:///path/photo1.jpg", "file:///path/photo2.jpg")
        val context = WorkContext(images = photos)

        val request = builder.buildAIRequest("Inspect photos", context)

        assertEquals(2, request.images.size)
        assertEquals("file:///path/photo1.jpg", request.images[0].uri)
        assertEquals("file:///path/photo2.jpg", request.images[1].uri)

        val reqs = request.computeRequirements()
        assertTrue(reqs.requiresImages)
        assertTrue(reqs.requiresMultipleImages)
        assertEquals(2, reqs.requiredImageCount)
    }

    @Test
    fun testVideoContextSelectedFramesIncorporation() = runTest {
        val videoContext = VideoContext(
            videoUri = "file:///path/video.mp4",
            durationMs = 5000L,
            selectedFrames = listOf(
                AIImage(uri = "file:///frame1.jpg"),
                AIImage(uri = "file:///frame2.jpg")
            ),
            transcript = "Engine sound humming irregularly"
        )
        val context = WorkContext(videoContext = videoContext)

        val request = builder.buildAIRequest("Analyze video frames", context)

        assertEquals(2, request.images.size)
        assertEquals("file:///frame1.jpg", request.images[0].uri)
        assertEquals("file:///frame2.jpg", request.images[1].uri)
        assertEquals("file:///path/video.mp4", request.videoContext?.videoUri)
        assertEquals("Engine sound humming irregularly", request.audioTranscript)
        assertTrue(request.systemPrompt?.contains("[AUDIO TRANSCRIPT]") == true)
        assertTrue(request.systemPrompt?.contains("Engine sound humming irregularly") == true)

        val reqs = request.computeRequirements()
        assertTrue(reqs.requiresImages)
        assertEquals(2, reqs.requiredImageCount)
    }

    @Test
    fun testDeduplicationAndImageCountClamping() = runTest {
        val photos = listOf(
            "file:///duplicate.jpg",
            "file:///photo1.jpg",
            "file:///photo2.jpg",
            "file:///photo3.jpg"
        )
        val videoContext = VideoContext(
            selectedFrames = listOf(
                AIImage(uri = "file:///duplicate.jpg"),
                AIImage(uri = "file:///frame1.jpg"),
                AIImage(uri = "file:///frame2.jpg")
            )
        )
        val context = WorkContext(
            images = photos,
            videoContext = videoContext
        )

        // Clamping test with maxTotalImages = 4
        val configClamp = WorkContextConfig(maxTotalImages = 4)
        val requestClamped = builder.buildAIRequest("Clamped request", context, configClamp)

        assertEquals(4, requestClamped.images.size)
        // Verify user photos are prioritized first: duplicate, photo1, photo2, photo3
        val expectedUrisClamped = listOf(
            "file:///duplicate.jpg",
            "file:///photo1.jpg",
            "file:///photo2.jpg",
            "file:///photo3.jpg"
        )
        assertEquals(expectedUrisClamped, requestClamped.images.map { it.uri })
        assertEquals(4, requestClamped.computeRequirements().requiredImageCount)

        // Deduplication test with maxTotalImages = 10 (no clamping, deduplication yields 6)
        val configDedup = WorkContextConfig(maxTotalImages = 10)
        val requestDedup = builder.buildAIRequest("Dedup request", context, configDedup)

        assertEquals(6, requestDedup.images.size)
        val expectedUrisDedup = listOf(
            "file:///duplicate.jpg",
            "file:///photo1.jpg",
            "file:///photo2.jpg",
            "file:///photo3.jpg",
            "file:///frame1.jpg",
            "file:///frame2.jpg"
        )
        assertEquals(expectedUrisDedup, requestDedup.images.map { it.uri })
        assertEquals(6, requestDedup.computeRequirements().requiredImageCount)
    }

    @Test
    fun testProvenanceFormatting() = runTest {
        val context = WorkContext(
            task = WorkTask(
                id = "task1",
                title = "Fix HVAC Compressor Leak",
                description = "Water leak observed near compressor valve",
                domain = WorkDomain.HVAC
            ),
            domain = WorkDomain.HVAC,
            observations = listOf(
                Observation(
                    id = "obs1",
                    description = "Visible liquid puddle beneath unit",
                    source = ObservationSource.USER,
                    supportingEvidence = "Photo 1"
                )
            ),
            audioTranscript = "Customer reported loud rattling before leak occurred",
            sensorData = SensorDataPayload(
                accelerometer = floatArrayOf(0.1f, 0.2f, 9.8f),
                gyroscope = floatArrayOf(0.0f, 0.01f, 0.0f),
                compassHeading = 180.0f,
                metadata = mapOf("Temperature" to "72F")
            ),
            location = LocationPayload(
                latitude = 37.7749,
                longitude = -122.4194,
                address = "123 Main St, San Francisco"
            ),
            machineInfo = MachineInfoPayload(
                deviceName = "Carrier Industrial Chiller",
                modelNumber = "XC-500",
                category = "HVAC Equipment"
            ),
            workHistory = listOf(
                "Service 1: Filter replaced 3 months ago",
                "Service 2: Pressure valve inspected 6 months ago",
                "Service 3: Coolant flushed 1 year ago",
                "Service 4: Electrical check 2 years ago",
                "Service 5: Belt tensioned 3 years ago",
                "Service 6: Initial installation 4 years ago"
            ),
            availableTools = listOf(
                ToolItem(id = "tool1", name = "Pipe Wrench", isAvailable = true)
            ),
            availableParts = listOf(
                ToolItem(id = "part1", name = "Replacement Seal Gasket", isAvailable = false)
            ),
            retrievedKnowledge = listOf(
                "Manual Chunk 1: Inspect gasket seal for cracks or erosion.",
                "Manual Chunk 2: Torque mounting bolts to 25 Nm.",
                "Manual Chunk 3: Check drain pan alignment.",
                "Manual Chunk 4: Flush overflow tube."
            ),
            safetyContext = listOf(
                SafetyRequirement(
                    hazard = "High Voltage Mains Line",
                    severity = SafetySeverity.HIGH,
                    precaution = "Disconnect main power breaker before opening chassis",
                    ppe = listOf("Insulated Gloves", "Safety Glasses"),
                    stopCondition = "Live voltage detected"
                ),
                SafetyRequirement(
                    hazard = "Low Surface Heat",
                    severity = SafetySeverity.MEDIUM,
                    precaution = "Allow pipe to cool down"
                )
            ),
            conversationHistory = (1..15).map { AIMessage(role = AIRole.USER, content = "Turn $it") }
        )

        val config = WorkContextConfig(
            maxHistoryEntries = 3,
            maxRagChunks = 2,
            maxConversationTurns = 5
        )

        val request = builder.buildAIRequest("Diagnose HVAC issue", context, config)

        val sysPrompt = request.systemPrompt!!

        assertTrue(sysPrompt.contains("[TASK & DOMAIN]"))
        assertTrue(sysPrompt.contains("Fix HVAC Compressor Leak"))
        assertTrue(sysPrompt.contains("Domain: HVAC"))

        assertTrue(sysPrompt.contains("[USER OBSERVATIONS]"))
        assertTrue(sysPrompt.contains("Visible liquid puddle beneath unit"))

        assertTrue(sysPrompt.contains("[AUDIO TRANSCRIPT]"))
        assertTrue(sysPrompt.contains("Customer reported loud rattling before leak occurred"))

        assertTrue(sysPrompt.contains("[SENSOR MEASUREMENTS]"))
        assertTrue(sysPrompt.contains("Compass Heading: 180.0°"))

        assertTrue(sysPrompt.contains("[LOCATION CONTEXT]"))
        assertTrue(sysPrompt.contains("123 Main St, San Francisco"))

        assertTrue(sysPrompt.contains("[MACHINE METADATA]"))
        assertTrue(sysPrompt.contains("Carrier Industrial Chiller"))

        assertTrue(sysPrompt.contains("[SERVICE HISTORY]"))
        assertTrue(sysPrompt.contains("Service 1:"))
        assertTrue(sysPrompt.contains("Service 3:"))
        assertFalse(sysPrompt.contains("Service 4:")) // Max history entries = 3

        assertTrue(sysPrompt.contains("[AVAILABLE TOOLS & PARTS]"))
        assertTrue(sysPrompt.contains("Pipe Wrench"))
        assertTrue(sysPrompt.contains("Replacement Seal Gasket"))

        assertTrue(sysPrompt.contains("[RETRIEVED MANUAL KNOWLEDGE]"))
        assertTrue(sysPrompt.contains("Manual Chunk 1:"))
        assertTrue(sysPrompt.contains("Manual Chunk 2:"))
        assertFalse(sysPrompt.contains("Manual Chunk 3:")) // Max RAG chunks = 2

        assertTrue(sysPrompt.contains("[SAFETY CONSTRAINTS]"))
        assertTrue(sysPrompt.contains("High Voltage Mains Line"))
        assertFalse(sysPrompt.contains("Low Surface Heat")) // Filtered out MEDIUM severity

        // Verify conversation history bounded to maxConversationTurns = 5
        assertEquals(5, request.conversationHistory.size)
        assertEquals("Turn 11", request.conversationHistory.first().content)
        assertEquals("Turn 15", request.conversationHistory.last().content)

        // Verify repairHistory bounded to maxHistoryEntries = 3
        assertEquals(3, request.repairHistory.size)

        // Verify retrievedKnowledge bounded to maxRagChunks = 2
        assertEquals(2, request.retrievedKnowledge.size)
    }

    @Test
    fun testFullIntegrationRealisticContext() = runTest {
        val userPhotos = listOf("file:///photo1.jpg", "file:///photo2.jpg", "file:///photo3.jpg")
        val videoCtx = VideoContext(
            videoUri = "file:///inspection.mp4",
            durationMs = 12000L,
            selectedFrames = listOf(
                AIImage(uri = "file:///frame1.jpg"),
                AIImage(uri = "file:///frame2.jpg")
            ),
            transcript = "Audio note: pump is humming louder than normal"
        )

        val fullContext = WorkContext(
            task = WorkTask(
                id = "task_100",
                title = "Washing Machine Pump Inspection",
                description = "Drain pump error E20",
                domain = WorkDomain.APPLIANCE
            ),
            domain = WorkDomain.APPLIANCE,
            images = userPhotos,
            videoContext = videoCtx,
            machineInfo = MachineInfoPayload(
                deviceName = "Smart Washer 3000",
                modelNumber = "WM-3000-X",
                category = "Appliance"
            ),
            availableTools = listOf(
                ToolItem(id = "t1", name = "Screwdriver Set", isAvailable = true)
            ),
            availableParts = listOf(
                ToolItem(id = "p1", name = "Drain Hose", isAvailable = false)
            ),
            retrievedKnowledge = listOf(
                "Check pump impeller for debris."
            ),
            safetyContext = listOf(
                SafetyRequirement(
                    hazard = "Risk of Electric Shock",
                    severity = SafetySeverity.HIGH,
                    precaution = "Unplug appliance before servicing"
                )
            )
        )

        val userPrompt = "How do I clear the pump debris?"
        val request = builder.buildAIRequest(userPrompt, fullContext)

        assertEquals(userPrompt, request.prompt)
        assertEquals(5, request.images.size) // 3 photos + 2 video frames

        val requirements = request.computeRequirements()
        assertEquals(5, requirements.requiredImageCount)
        assertTrue(requirements.requiresImages)
        assertTrue(requirements.requiresMultipleImages)
        assertTrue(requirements.requiresToolCalling)
        assertTrue(requirements.requiresStructuredOutput)
        assertNotNull(request.workContext)
        assertEquals("Smart Washer 3000", request.machineInfo?.deviceName)
    }
}
