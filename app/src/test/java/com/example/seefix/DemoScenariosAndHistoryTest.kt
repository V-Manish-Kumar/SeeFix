package com.example.seefix

import com.example.seefix.data.local.SeeFixDatabase
import com.example.seefix.data.repository.DemoScenarios
import com.example.seefix.data.repository.SessionHistoryRepositoryImpl
import com.example.seefix.domain.model.SafetyLevel
import com.example.seefix.domain.model.TroubleshootingStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenariosAndHistoryTest {

    @Test
    fun demoScenarios_washingMachine_hasCorrectStepsAndMissingTool() {
        val wmScenario = DemoScenarios.WASHING_MACHINE_SCENARIO

        assertNotNull(wmScenario.device)
        assertEquals("Smart Washing Machine (iQOO Demo Appliance)", wmScenario.device?.name)
        assertEquals(5, wmScenario.steps.size)

        // Step 1 check
        val step1 = wmScenario.steps.first()
        assertTrue(step1.title.contains("Disconnect Main AC Power Plug"))
        assertTrue(step1.requiresSafetyConfirmation)

        // Step 5 check missing tool (45uF Capacitor)
        val step5 = wmScenario.steps.last()
        assertTrue(step5.requiredTools.any { !it.isAvailable })
        assertTrue(wmScenario.missingTools.isNotEmpty())
    }

    @Test
    fun demoScenarios_circuitBreaker_hasCorrectSafetyLevelAndSteps() {
        val cbScenario = DemoScenarios.CIRCUIT_BREAKER_SCENARIO

        assertNotNull(cbScenario.device)
        assertEquals(SafetyLevel.DANGEROUS_STOP, cbScenario.safetyStatus.level)
        assertEquals(4, cbScenario.steps.size)
        assertTrue(cbScenario.steps.first().requiresSafetyConfirmation)
    }

    @Test
    fun demoScenarios_hvac_hasThermalFuseMissingPart() {
        val hvacScenario = DemoScenarios.HVAC_SCENARIO

        assertNotNull(hvacScenario.device)
        assertEquals(4, hvacScenario.steps.size)
        assertTrue(hvacScenario.missingTools.any { it.name.contains("Thermal Fuse") })
    }

    @Test
    fun sessionHistoryRepository_persistsAndRetrievesSessions() = runTest {
        val database = SeeFixDatabase(context = null)
        val repository = SessionHistoryRepositoryImpl(database)

        val history = repository.getHistory().first()
        assertTrue("Preloaded history should contain demo sessions", history.isNotEmpty())

        val wmSession = DemoScenarios.WASHING_MACHINE_SCENARIO
        repository.saveSession(wmSession)

        val updatedHistory = repository.getHistory().first()
        assertTrue(updatedHistory.any { it.id == wmSession.id })
    }

    @Test
    fun troubleshootingStep_safetyConfirmation_locksAndUnlocksCorrectly() {
        val step = TroubleshootingStep(
            stepNumber = 1,
            title = "Disconnect Power",
            instructionText = "Unplug power lead",
            requiresSafetyConfirmation = true,
            isSafetyConfirmed = false,
            safetyConfirmationPrompt = "CONFIRM POWER DISCONNECTED"
        )

        assertFalse(step.isSafetyConfirmed)
        assertTrue(step.requiresSafetyConfirmation)

        val confirmedStep = step.copy(isSafetyConfirmed = true)
        assertTrue(confirmedStep.isSafetyConfirmed)
    }
}
