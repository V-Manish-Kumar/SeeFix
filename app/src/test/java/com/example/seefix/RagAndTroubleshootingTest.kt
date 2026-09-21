package com.example.seefix

import com.example.seefix.ai.rag.RagKnowledgeEngine
import com.example.seefix.data.repository.TroubleshootingRepositoryImpl
import com.example.seefix.domain.model.HardwareDevice
import com.example.seefix.domain.model.TroubleshootingSession
import com.example.seefix.domain.model.TroubleshootingStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RagAndTroubleshootingTest {

    @Test
    fun ragKnowledgeEngine_queriesDrainPumpKeywords_returnsMatchedManualAndSteps() {
        val ragEngine = RagKnowledgeEngine(context = null)
        val queryResult = ragEngine.queryKnowledge("drain pump water overflow e20", "washing machine")

        assertTrue(queryResult.matchingManualTitle.contains("Washing Machine"))
        assertNotNull(queryResult.matchedGuide)
        assertTrue(queryResult.recommendedSteps.isNotEmpty())
        assertTrue(queryResult.relevantSafetyWarnings.isNotEmpty())
        assertTrue(queryResult.specifications.containsKey("Capacitor Rating"))
    }

    @Test
    fun ragKnowledgeEngine_queriesBreakerTripKeywords_returnsCircuitBreakerManual() {
        val ragEngine = RagKnowledgeEngine(context = null)
        val queryResult = ragEngine.queryKnowledge("circuit breaker trip main panel overload", "breaker")

        assertTrue(queryResult.matchingManualTitle.contains("Circuit Breaker"))
        assertNotNull(queryResult.matchedGuide)
        assertTrue(queryResult.recommendedSteps.isNotEmpty())
        assertTrue(queryResult.specifications.containsKey("Panel Rating"))
    }

    @Test
    fun troubleshootingRepository_createsAndUpdatesSession_maintainsHistory() = runTest {
        val repository = TroubleshootingRepositoryImpl()

        val device = HardwareDevice(
            id = "d1",
            name = "Test Appliance",
            model = "TA-100",
            category = "Testing"
        )

        val session = repository.createSession(device, "Test Problem")
        assertEquals("Test Problem", session.detectedProblem)

        val active = repository.getActiveSession().first()
        assertNotNull(active)
        assertEquals("TA-100", active?.device?.model)

        repository.updateActiveSession { s ->
            s.copy(currentStepIndex = 2)
        }

        val updatedActive = repository.getActiveSession().first()
        assertEquals(2, updatedActive?.currentStepIndex)

        val history = repository.getSessionHistory().first()
        assertTrue(history.isNotEmpty())
    }

    @Test
    fun troubleshootingSession_helperProperties_calculateCorrectly() {
        val steps = listOf(
            TroubleshootingStep(stepNumber = 1, title = "Step 1", instructionText = "Text 1", isCompleted = true),
            TroubleshootingStep(stepNumber = 2, title = "Step 2", instructionText = "Text 2", isCompleted = false)
        )

        val session = TroubleshootingSession(
            id = "s1",
            currentStepIndex = 0,
            steps = steps
        )

        assertEquals("Step 1", session.currentStep?.title)
        assertEquals(1, session.completedSteps.size)
        assertEquals(true, session.completedSteps.first().isCompleted)
    }
}
