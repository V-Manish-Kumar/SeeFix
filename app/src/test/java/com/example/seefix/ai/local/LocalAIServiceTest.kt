package com.example.seefix.ai.local

import com.example.seefix.ai.core.AICapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAIServiceTest {

    @Test
    fun testLocalModelInfoDataClass() {
        val caps = AICapabilities(supportsText = true, maxContextTokens = 2048)
        val info = LocalModelInfo(
            id = "test-id",
            name = "gemma-2b",
            filePath = "/path/to/model.bin",
            sizeBytes = 1024L,
            format = "BIN",
            isLoaded = false,
            capabilities = caps
        )

        assertEquals("test-id", info.id)
        assertEquals("gemma-2b", info.name)
        assertEquals("/path/to/model.bin", info.filePath)
        assertEquals(1024L, info.sizeBytes)
        assertEquals("BIN", info.format)
        assertFalse(info.isLoaded)
        assertEquals(caps, info.capabilities)
    }

    @Test
    fun testDeviceCompatibilityInfo() {
        val compat = DeviceCompatibilityInfo(
            isCompatible = true,
            totalRamMb = 4000L,
            freeRamMb = 2000L,
            freeStorageMb = 5000L,
            hasGpuSupport = true,
            warningMessages = emptyList()
        )

        assertTrue(compat.isCompatible)
        assertEquals(4000L, compat.totalRamMb)
        assertEquals(2000L, compat.freeRamMb)
        assertEquals(5000L, compat.freeStorageMb)
        assertTrue(compat.hasGpuSupport)
        assertTrue(compat.warningMessages.isEmpty())
    }

    @Test
    fun testCapabilities() {
        val caps = AICapabilities(
            supportsText = true,
            supportsImages = false,
            supportsMultipleImages = false,
            supportsAudio = false,
            supportsVideo = false,
            supportsVideoFrames = false,
            supportsToolCalling = false,
            supportsStructuredOutput = true,
            maxContextTokens = 2048,
            maxImages = 0
        )
        assertTrue(caps.supportsText)
        assertTrue(caps.supportsStreaming)
        assertFalse(caps.supportsMultimodal)
    }
}
