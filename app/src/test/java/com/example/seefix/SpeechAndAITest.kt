package com.example.seefix

import com.example.seefix.ai.GeminiAIEngine
import com.example.seefix.ai.LocationInfo
import com.example.seefix.ai.MockAIEngine
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.ToolItem
import com.example.seefix.domain.model.TroubleshootingStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechAndAITest {

    @Test
    fun mockAiEngine_analyzesWashingMachine_returnsStepsAndSafety() = runTest {
        val aiEngine = MockAIEngine()
        val result = aiEngine.analyzeEquipment(null, "Washing Machine not draining water E20")

        assertNotNull(result.device)
        assertEquals("Home Appliance", result.device?.category)
        assertTrue(result.steps.isNotEmpty())
        assertEquals(SafetyLevel.DANGEROUS_STOP, result.safetyStatus.level)
        assertTrue(result.confidence > 0.70f)
    }

    @Test
    fun mockAiEngine_analyzesCircuitBreaker_returnsDangerousSafetyLevel() = runTest {
        val aiEngine = MockAIEngine()
        val result = aiEngine.analyzeEquipment(null, "Main circuit breaker tripped in electrical panel")

        assertNotNull(result.device)
        assertEquals("Electrical & Mains", result.device?.category)
        assertEquals(SafetyLevel.DANGEROUS_STOP, result.safetyStatus.level)
    }

    @Test
    fun mockAiEngine_verifiesStepCompletion_returnsHighConfidence() = runTest {
        val aiEngine = MockAIEngine()
        val step = TroubleshootingStep(
            stepNumber = 1,
            title = "Test Capacitor C42",
            instructionText = "Test discharge voltage"
        )

        val verification = aiEngine.verifyStepCompletion(null, step)

        assertTrue(verification.isVerified)
        assertTrue(verification.confidence > 0.90f)
        assertTrue(verification.feedbackMessage.isNotEmpty())
        assertTrue(verification.detectedBoxes.isNotEmpty())
    }

    @Test
    fun mockAiEngine_recommendsStoresForMissingTools_returnsLocations() = runTest {
        val aiEngine = MockAIEngine()
        val missingTools = listOf(
            ToolItem("t1", "45uF Capacitor", isAvailable = false, isRequired = true)
        )
        val location = LocationInfo(37.7749, -122.4194)

        val stores = aiEngine.recommendStoresForMissingTools(missingTools, location)

        assertTrue(stores.isNotEmpty())
        assertTrue(stores.first().name.isNotEmpty())
        assertTrue(stores.first().phone.isNotEmpty())
    }

    @Test
    fun geminiAiEngine_fallbackToMock_returnsValidResponse() = runTest {
        val geminiEngine = GeminiAIEngine(apiKey = null)
        val result = geminiEngine.analyzeEquipment(null, "Washing Machine issue")

        assertNotNull(result.device)
        assertTrue(result.steps.isNotEmpty())
    }
}
