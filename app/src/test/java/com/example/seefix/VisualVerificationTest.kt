package com.example.seefix

import com.example.seefix.camera.VisualVerificationAnalyzer
import com.example.seefix.domain.model.BoundingBox
import com.example.seefix.domain.model.TroubleshootingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualVerificationTest {

    @Test
    fun visualVerificationAnalyzer_initialState_handlesNullTargetStep() {
        val analyzer = VisualVerificationAnalyzer(targetStep = null) { _ -> }
        analyzer.setTargetStep(null)
    }

    @Test
    fun troubleshootingStep_targetBoundingBox_calculatesCorrectly() {
        val step = TroubleshootingStep(
            stepNumber = 1,
            title = "Unclip Capacitor C42 Terminal Wire",
            instructionText = "Pull red connector terminal straight off capacitor post.",
            visualVerificationPrompt = "Verify capacitor wire is disconnected.",
            targetBoundingBox = BoundingBox(0.2f, 0.3f, 0.7f, 0.8f)
        )

        assertNotNull(step.targetBoundingBox)
        assertEquals(0.2f, step.targetBoundingBox?.xMin ?: 0f, 0.001f)
        assertEquals(0.8f, step.targetBoundingBox?.yMax ?: 0f, 0.001f)
        assertTrue(step.visualVerificationPrompt.contains("wire"))
    }
}
