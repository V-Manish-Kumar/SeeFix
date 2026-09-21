package com.example.seefix.ai.local

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.example.seefix.ai.core.AICapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

open class LocalModelManager(private val context: Context?) {

    companion object {
        const val MIN_TOTAL_RAM_MB: Long = 3500L // 3.5 GB
        const val MIN_FREE_STORAGE_MB: Long = 1500L // 1.5 GB
        const val LOW_FREE_RAM_MB: Long = 1000L // 1.0 GB
    }

    /**
     * Retrieves list of available local model files in the app's models directories.
     */
    open fun getAvailableModels(): List<LocalModelInfo> {
        val ctx = context ?: return emptyList()
        val modelFiles = mutableListOf<File>()

        val extDir = ctx.getExternalFilesDir("models")
        if (extDir?.exists() == true) {
            extDir.listFiles()?.filter { isModelFile(it) }?.let { modelFiles.addAll(it) }
        }

        val intDir = File(ctx.filesDir, "models")
        if (intDir.exists()) {
            intDir.listFiles()?.filter { isModelFile(it) }?.let { modelFiles.addAll(it) }
        }

        return modelFiles
            .distinctBy { it.absolutePath }
            .map { createModelInfoFromFile(it) }
    }

    /**
     * Computes device compatibility for running local LLM models like Gemma 2B.
     */
    open fun getDeviceCompatibilityInfo(modelSizeBytes: Long = 0L): DeviceCompatibilityInfo {
        val ctx = context ?: return DeviceCompatibilityInfo(
            isCompatible = true,
            totalRamMb = 4000L,
            freeRamMb = 2000L,
            freeStorageMb = 5000L,
            hasGpuSupport = true,
            warningMessages = emptyList()
        )

        val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMb = (memoryInfo.totalMem) / (1024 * 1024)
        val freeRamMb = (memoryInfo.availMem) / (1024 * 1024)

        val modelsDir = ctx.getExternalFilesDir("models") ?: ctx.filesDir
        val statFs = StatFs(modelsDir.path)
        val freeStorageMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)

        val hasGpuSupport = checkGpuSupport()

        val warningMessages = mutableListOf<String>()

        if (totalRamMb < MIN_TOTAL_RAM_MB) {
            warningMessages.add("Device total RAM ($totalRamMb MB) is below the recommended 3.5 GB.")
        }
        if (freeRamMb < LOW_FREE_RAM_MB) {
            warningMessages.add("Available RAM ($freeRamMb MB) is low. Model execution may cause Out-Of-Memory errors.")
        }
        if (freeStorageMb < MIN_FREE_STORAGE_MB) {
            warningMessages.add("Available storage ($freeStorageMb MB) is below the recommended 1.5 GB.")
        }
        if (modelSizeBytes > 0L && (freeStorageMb * 1024 * 1024) < modelSizeBytes) {
            warningMessages.add("Available storage space is insufficient for model size ($modelSizeBytes bytes).")
        }
        if (!hasGpuSupport) {
            warningMessages.add("GPU acceleration feature not detected on this device. Performance may be degraded.")
        }

        val isCompatible = totalRamMb >= 3000L && freeStorageMb >= 1000L

        return DeviceCompatibilityInfo(
            isCompatible = isCompatible,
            totalRamMb = totalRamMb,
            freeRamMb = freeRamMb,
            freeStorageMb = freeStorageMb,
            hasGpuSupport = hasGpuSupport,
            warningMessages = warningMessages,
        )
    }

    /**
     * Imports a model file from an android.net.Uri by copying it to the local app models directory.
     */
    open suspend fun importModelFromUri(uri: Uri, preferredFileName: String? = null): Result<LocalModelInfo> =
        withContext(Dispatchers.IO) {
            val ctx = context ?: return@withContext Result.failure(Exception("Context is null"))
            try {
                val targetDir = ctx.getExternalFilesDir("models") ?: File(ctx.filesDir, "models")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                val fileName = preferredFileName
                    ?: getFileNameFromUri(uri)
                    ?: "model_${System.currentTimeMillis()}.bin"

                val targetFile = File(targetDir, fileName)

                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: return@withContext Result.failure(Exception("Unable to open input stream for URI: $uri"))

                Result.success(createModelInfoFromFile(targetFile))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Deletes a model file by ID or path.
     */
    open fun deleteModel(modelIdOrPath: String): Boolean {
        val models = getAvailableModels()
        val found = models.find { it.id == modelIdOrPath || it.filePath == modelIdOrPath }
        val targetFile = if (found != null) File(found.filePath) else File(modelIdOrPath)

        return if (targetFile.exists()) {
            targetFile.delete()
        } else {
            false
        }
    }

    /**
     * Returns a model File matching the given ID or path, or preferred model.
     */
    open fun getModelFile(modelIdOrPath: String? = null): File? {
        if (!modelIdOrPath.isNullOrBlank()) {
            val directFile = File(modelIdOrPath)
            if (directFile.exists()) return directFile
        }

        val available = getAvailableModels()
        if (available.isEmpty()) return null

        if (!modelIdOrPath.isNullOrBlank()) {
            val matched = available.find { it.id == modelIdOrPath || it.filePath == modelIdOrPath || it.name == modelIdOrPath }
            if (matched != null) return File(matched.filePath)
        }

        return File(available.first().filePath)
    }

    private fun isModelFile(file: File): Boolean {
        val name = file.name.lowercase()
        return file.isFile && (name.endsWith(".bin") || name.endsWith(".task"))
    }

    private fun createModelInfoFromFile(file: File): LocalModelInfo {
        return LocalModelInfo(
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
            ),
        )
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val ctx = context ?: return null
        var name: String? = null
        if (uri.scheme == "content") {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { p ->
                val cut = p.lastIndexOf('/')
                if (cut != -1) p.substring(cut + 1) else p
            }
        }
        return name
    }

    private fun checkGpuSupport(): Boolean {
        val ctx = context ?: return true
        return try {
            val pm = ctx.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK) ||
                    pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } catch (_: Exception) {
            true
        }
    }
}
