package com.example.seefix.camera

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.seefix.ai.core.AIImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoFrameExtractor(
    private val context: Context?,
    private val config: VideoFrameExtractionConfig = VideoFrameExtractionConfig()
) {

    fun calculateSamplingTimestamps(durationMs: Long): List<Long> {
        val rawN = (durationMs / 2000L).toInt()
        val frameCount = rawN.coerceIn(config.minFrames, config.maxFrames)

        val startOffset = 500L
        val endOffset = maxOf(500L, durationMs - 500L)

        if (frameCount <= 1 || startOffset >= endOffset) {
            return List(frameCount) { startOffset }
        }

        val step = (endOffset - startOffset).toDouble() / (frameCount - 1)
        return (0 until frameCount).map { i ->
            (startOffset + i * step).toLong()
        }
    }

    suspend fun extractFrames(videoUri: Uri, durationMs: Long = 0L): VideoFrameExtractionResult =
        extractFrames(videoUri.toString(), durationMs)

    suspend fun extractFrames(videoUriString: String, durationMs: Long = 0L): VideoFrameExtractionResult =
        withContext(Dispatchers.IO) {
            if (context == null) {
                // Mock / headless test environment
                val timestamps = calculateSamplingTimestamps(durationMs)
                val mockFrames = timestamps.map { ts ->
                    AIImage(
                        uri = "$videoUriString#frame_$ts",
                        mimeType = "image/jpeg",
                        timestampMs = ts
                    )
                }
                return@withContext VideoFrameExtractionResult(
                    videoUri = videoUriString,
                    durationMs = durationMs,
                    frames = mockFrames,
                    extractedCount = mockFrames.size,
                    skippedCount = 0
                )
            }

            cleanupStaleFrames()

            val parsedUri = try { Uri.parse(videoUriString) } catch (_: Exception) { null }
            if (parsedUri == null) {
                return@withContext VideoFrameExtractionResult(
                    videoUri = videoUriString,
                    durationMs = durationMs,
                    frames = emptyList(),
                    extractedCount = 0,
                    skippedCount = 0
                )
            }

            val retriever = MediaMetadataRetriever()
            val extractedFrames = mutableListOf<AIImage>()
            var skippedCount = 0

            val actualDurationMs = if (durationMs > 0L) {
                durationMs
            } else {
                try {
                    retriever.setDataSource(context, parsedUri)
                    val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durStr?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }

            val timestamps = calculateSamplingTimestamps(actualDurationMs)
            val framesDir = File(context.cacheDir, "extracted_frames").apply { mkdirs() }

            try {
                try {
                    retriever.setDataSource(context, parsedUri)
                } catch (_: Exception) {
                    return@withContext VideoFrameExtractionResult(
                        videoUri = videoUriString,
                        durationMs = actualDurationMs,
                        frames = emptyList(),
                        extractedCount = 0,
                        skippedCount = timestamps.size
                    )
                }

                for (ts in timestamps) {
                    val timestampUs = ts * 1000L
                    val frameBitmap = try {
                        retriever.getFrameAtTime(timestampUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Exception) {
                        null
                    }

                    if (frameBitmap == null) {
                        skippedCount++
                        continue
                    }

                    val scaledBitmap = scaleDownBitmap(frameBitmap, config.maxDimension)
                    val frameFile = File(framesDir, "seefix_frame_${ts}.jpg")

                    val saved = try {
                        FileOutputStream(frameFile).use { out ->
                            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, config.jpegQuality, out)
                        }
                    } catch (_: Exception) {
                        false
                    } finally {
                        scaledBitmap.recycle()
                    }

                    if (saved) {
                        extractedFrames.add(
                            AIImage(
                                uri = frameFile.toURI().toString(),
                                mimeType = "image/jpeg",
                                timestampMs = ts
                            )
                        )
                    } else {
                        skippedCount++
                    }
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }

            VideoFrameExtractionResult(
                videoUri = videoUriString,
                durationMs = actualDurationMs,
                frames = extractedFrames,
                extractedCount = extractedFrames.size,
                skippedCount = skippedCount
            )
        }

    private fun scaleDownBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= maxDimension) {
            return bitmap
        }

        val ratio = maxDimension.toFloat() / maxDim
        val newWidth = (width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (height * ratio).toInt().coerceAtLeast(1)

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled != bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun cleanupStaleFrames(maxAgeMs: Long = 24 * 3600_000L) {
        if (context == null) return
        try {
            val framesDir = File(context.cacheDir, "extracted_frames")
            if (framesDir.exists() && framesDir.isDirectory) {
                val now = System.currentTimeMillis()
                framesDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
