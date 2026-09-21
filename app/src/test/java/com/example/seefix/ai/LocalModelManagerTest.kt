package com.example.seefix.ai

import com.example.seefix.ai.core.AICapabilities
import com.example.seefix.ai.local.DeviceCompatibilityInfo
import com.example.seefix.ai.local.LocalModelInfo
import com.example.seefix.ai.local.LocalModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestableLocalModelManager(
        private val modelsDir: File
    ) : LocalModelManager(context = null) {

        override fun getAvailableModels(): List<LocalModelInfo> {
            if (!modelsDir.exists()) return emptyList()
            val modelFiles = modelsDir.listFiles()?.filter { file ->
                val name = file.name.lowercase()
                file.isFile && (name.endsWith(".bin") || name.endsWith(".task"))
            } ?: emptyList()

            return modelFiles
                .distinctBy { it.absolutePath }
                .map { file ->
                    LocalModelInfo(
                        id = file.absolutePath,
                        name = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        format = file.extension.uppercase(),
                        isLoaded = false,
                        capabilities = AICapabilities(
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
                    )
                }
        }
    }

    @Test
    fun getDeviceCompatibilityInfo_nullContext_returnsDefaultCompatibleValues() {
        val manager = LocalModelManager(context = null)
        val info = manager.getDeviceCompatibilityInfo()

        assertTrue(info.isCompatible)
        assertEquals(4000L, info.totalRamMb)
        assertEquals(2000L, info.freeRamMb)
        assertEquals(5000L, info.freeStorageMb)
        assertTrue(info.hasGpuSupport)
        assertTrue(info.warningMessages.isEmpty())
    }

    @Test
    fun deviceCompatibilityInfo_warningsAndThresholdCalculations() {
        // Low total RAM (< 3500 MB)
        val lowRamInfo = computeDeviceCompatibility(
            totalRamMb = 3000L,
            freeRamMb = 800L,
            freeStorageMb = 2000L,
            hasGpuSupport = true,
            modelSizeBytes = 0L
        )
        assertTrue(lowRamInfo.warningMessages.any { it.contains("total RAM") })
        assertTrue(lowRamInfo.warningMessages.any { it.contains("Available RAM") })

        // Low free storage (< 1500 MB)
        val lowStorageInfo = computeDeviceCompatibility(
            totalRamMb = 4000L,
            freeRamMb = 2000L,
            freeStorageMb = 1200L,
            hasGpuSupport = true,
            modelSizeBytes = 0L
        )
        assertTrue(lowStorageInfo.warningMessages.any { it.contains("Available storage") })

        // Model size exceeds available storage
        val insufficientStorageForModel = computeDeviceCompatibility(
            totalRamMb = 4000L,
            freeRamMb = 2000L,
            freeStorageMb = 2000L,
            hasGpuSupport = true,
            modelSizeBytes = 3L * 1024 * 1024 * 1024
        )
        assertTrue(insufficientStorageForModel.warningMessages.any { it.contains("insufficient for model size") })

        // No GPU support warning
        val noGpuInfo = computeDeviceCompatibility(
            totalRamMb = 4000L,
            freeRamMb = 2000L,
            freeStorageMb = 5000L,
            hasGpuSupport = false,
            modelSizeBytes = 0L
        )
        assertTrue(noGpuInfo.warningMessages.any { it.contains("GPU acceleration") })

        // Incompatible conditions: total RAM < 3000 MB or free storage < 1000 MB
        val incompatibleRam = computeDeviceCompatibility(
            totalRamMb = 2500L, freeRamMb = 1000L, freeStorageMb = 2000L, hasGpuSupport = true, modelSizeBytes = 0L
        )
        assertFalse(incompatibleRam.isCompatible)

        val incompatibleStorage = computeDeviceCompatibility(
            totalRamMb = 4000L, freeRamMb = 1000L, freeStorageMb = 800L, hasGpuSupport = true, modelSizeBytes = 0L
        )
        assertFalse(incompatibleStorage.isCompatible)
    }

    @Test
    fun getAvailableModels_scansBinAndTaskExtensionsOnly() {
        val modelsFolder = tempFolder.newFolder("models")

        File(modelsFolder, "gemma-2b-it-q4.bin").apply { writeText("bin content") }
        File(modelsFolder, "gemma2-cpu.task").apply { writeText("task content") }
        File(modelsFolder, "notes.txt").apply { writeText("text note") }
        File(modelsFolder, "config.json").apply { writeText("{}") }
        File(modelsFolder, "subfolder.bin").mkdirs()

        val manager = TestableLocalModelManager(modelsFolder)
        val models = manager.getAvailableModels()

        assertEquals(2, models.size)
        val fileNames = models.map { it.name }.toSet()
        assertTrue(fileNames.contains("gemma-2b-it-q4"))
        assertTrue(fileNames.contains("gemma2-cpu"))

        val formats = models.map { it.format }.toSet()
        assertTrue(formats.contains("BIN"))
        assertTrue(formats.contains("TASK"))
    }

    @Test
    fun localModelInfo_capabilitiesAssignment() {
        val modelsFolder = tempFolder.newFolder("models_caps")
        val binFile = File(modelsFolder, "gemma-2b.bin").apply { writeText("1234567890") }

        val manager = TestableLocalModelManager(modelsFolder)
        val models = manager.getAvailableModels()

        assertEquals(1, models.size)
        val modelInfo = models.first()

        assertEquals(binFile.absolutePath, modelInfo.id)
        assertEquals("gemma-2b", modelInfo.name)
        assertEquals(binFile.absolutePath, modelInfo.filePath)
        assertEquals(10L, modelInfo.sizeBytes)
        assertEquals("BIN", modelInfo.format)
        assertFalse(modelInfo.isLoaded)

        val caps = modelInfo.capabilities
        assertTrue(caps.supportsText)
        assertFalse(caps.supportsImage)
        assertFalse(caps.supportsAudio)
        assertTrue(caps.supportsStreaming)
        assertFalse(caps.supportsMultimodal)
        assertEquals(2048, caps.maxContextTokens)
    }

    private fun computeDeviceCompatibility(
        totalRamMb: Long,
        freeRamMb: Long,
        freeStorageMb: Long,
        hasGpuSupport: Boolean,
        modelSizeBytes: Long
    ): DeviceCompatibilityInfo {
        val warnings = mutableListOf<String>()
        if (totalRamMb < LocalModelManager.MIN_TOTAL_RAM_MB) {
            warnings.add("Device total RAM ($totalRamMb MB) is below the recommended 3.5 GB.")
        }
        if (freeRamMb < LocalModelManager.LOW_FREE_RAM_MB) {
            warnings.add("Available RAM ($freeRamMb MB) is low. Model execution may cause Out-Of-Memory errors.")
        }
        if (freeStorageMb < LocalModelManager.MIN_FREE_STORAGE_MB) {
            warnings.add("Available storage ($freeStorageMb MB) is below the recommended 1.5 GB.")
        }
        if (modelSizeBytes > 0L && (freeStorageMb * 1024 * 1024) < modelSizeBytes) {
            warnings.add("Available storage space is insufficient for model size ($modelSizeBytes bytes).")
        }
        if (!hasGpuSupport) {
            warnings.add("GPU acceleration feature not detected on this device. Performance may be degraded.")
        }

        val isCompatible = totalRamMb >= 3000L && freeStorageMb >= 1000L

        return DeviceCompatibilityInfo(
            isCompatible = isCompatible,
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            freeStorageMb = freeStorageMb,
            hasGpuSupport = hasGpuSupport,
            warningMessages = warnings
        )
    }
}
