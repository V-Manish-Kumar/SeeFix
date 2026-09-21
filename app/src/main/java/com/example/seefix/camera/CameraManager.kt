package com.example.seefix.camera

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX manager handling camera lifecycle, preview binding,
 * frame analysis, photo capture, video recording, and torch control.
 * Features multi-tier fallback use-case binding for maximum physical device compatibility.
 */
class CameraManager(private val context: Context) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysisExecutor: ExecutorService? = null
    private var isTorchOn: Boolean = false
    private var imageCapture: ImageCapture? = null

    val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.HD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
            )
        )
        .build()

    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    val videoRecorder: VideoRecorder = VideoRecorder(context, videoCapture)

    fun isCameraAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        analyzer: ImageAnalysis.Analyzer? = null,
        onFrameAnalyzed: ((ImageProxy) -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()

                // Shutdown previous executor to prevent thread leaks
                imageAnalysisExecutor?.shutdown()
                imageAnalysisExecutor = null

                val effectiveAnalyzer = analyzer ?: onFrameAnalyzed?.let { callback ->
                    ImageAnalysis.Analyzer { imageProxy -> callback(imageProxy) }
                }

                // Standard live preview use-cases: Preview + ImageCapture + (ImageAnalysis if analyzer present)
                // Note: videoCapture is NOT bound concurrently by default to avoid exceeding hardware stream limits
                val primaryUseCases = mutableListOf<UseCase>(preview, capture)

                if (effectiveAnalyzer != null) {
                    val executor = Executors.newSingleThreadExecutor()
                    imageAnalysisExecutor = executor
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor, effectiveAnalyzer)
                    primaryUseCases.add(imageAnalysis)
                }

                // Tier 1: Try binding primary use cases (Preview + ImageCapture + ImageAnalysis)
                try {
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *primaryUseCases.toTypedArray()
                    )
                } catch (e1: Exception) {
                    Log.w("CameraManager", "Tier 1 camera binding failed: ${e1.message}. Trying Tier 2 fallback.")
                    // Tier 2 Fallback: If hardware stream combination limits are exceeded,
                    // try binding Preview + ImageAnalysis (omitting ImageCapture)
                    if (effectiveAnalyzer != null) {
                        try {
                            provider.unbindAll()
                            val fallbackUseCases = mutableListOf<UseCase>(preview)
                            val executor = Executors.newSingleThreadExecutor()
                            imageAnalysisExecutor?.shutdown()
                            imageAnalysisExecutor = executor
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            imageAnalysis.setAnalyzer(executor, effectiveAnalyzer)
                            fallbackUseCases.add(imageAnalysis)

                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                *fallbackUseCases.toTypedArray()
                            )
                        } catch (e2: Exception) {
                            Log.w("CameraManager", "Tier 2 camera binding failed: ${e2.message}. Trying Tier 3 fallback.")
                            // Tier 3 Fallback: Preview + ImageCapture
                            try {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    capture
                                )
                            } catch (e3: Exception) {
                                Log.w("CameraManager", "Tier 3 camera binding failed: ${e3.message}. Trying Tier 4 fallback.")
                                // Tier 4 Fallback: Preview only
                                try {
                                    provider.unbindAll()
                                    camera = provider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview
                                    )
                                } catch (e4: Exception) {
                                    Log.e("CameraManager", "All camera binding tiers failed", e4)
                                    onError?.invoke(e4)
                                }
                            }
                        }
                    } else {
                        // Tier 3 Fallback when no analyzer: Preview + ImageCapture
                        try {
                            provider.unbindAll()
                            camera = provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                capture
                            )
                        } catch (e3: Exception) {
                            Log.w("CameraManager", "Fallback camera binding failed: ${e3.message}. Trying Preview only.")
                            try {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview
                                )
                            } catch (e4: Exception) {
                                Log.e("CameraManager", "All camera binding attempts failed", e4)
                                onError?.invoke(e4)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Failed to initialize ProcessCameraProvider", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePhoto(
        executor: Executor = ContextCompat.getMainExecutor(context),
        onPhotoCaptured: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture ?: return
        capture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                onPhotoCaptured(image)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        })
    }

    fun capturePhoto(
        executor: Executor = ContextCompat.getMainExecutor(context),
        onPhotoCaptured: (ImageProxy) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        takePhoto(executor, onPhotoCaptured, onError)
    }

    fun capturePhotoBytes(
        executor: Executor = ContextCompat.getMainExecutor(context),
        onPhotoCaptured: (ByteArray) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        takePhoto(executor, { imageProxy ->
            try {
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                imageProxy.close()
                onPhotoCaptured(bytes)
            } catch (e: Exception) {
                imageProxy.close()
                onError(ImageCaptureException(ImageCapture.ERROR_UNKNOWN, "Failed to extract image bytes", e))
            }
        }, onError)
    }

    fun toggleTorch(): Boolean {
        camera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                isTorchOn = !isTorchOn
                cam.cameraControl.enableTorch(isTorchOn)
            }
        }
        return isTorchOn
    }

    fun shutdown() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w("CameraManager", "Error unbinding camera provider during shutdown: ${e.message}")
        }
        try {
            imageAnalysisExecutor?.shutdown()
        } catch (e: Exception) {
            Log.w("CameraManager", "Error shutting down image analysis executor: ${e.message}")
        }
        imageAnalysisExecutor = null
    }
}
