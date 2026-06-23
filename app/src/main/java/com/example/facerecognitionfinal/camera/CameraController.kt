package com.example.facerecognitionfinal.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val cameraExecutor: ExecutorService,
    private val previewSurfaceProvider: () -> Preview.SurfaceProvider,
    private val fullScreenSurfaceProvider: () -> Preview.SurfaceProvider,
    private val analyzer: (ImageProxy) -> Unit,
    private val onNoFrontCamera: () -> Unit,
    private val onCameraFailed: (String) -> Unit
) {

    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis
    private var previewUseCase: Preview? = null
    private var fullScreenPreviewVisible = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    val isReady: Boolean
        get() = ::imageCapture.isInitialized

    val isFrontCamera: Boolean
        get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    previewUseCase = it
                    setPreviewSurfaceProvider()
                }
                imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { image -> analyzer(image) }
                    }

                cameraProvider.unbindAll()
                if (!bindCamera(cameraProvider, CameraSelector.LENS_FACING_FRONT, preview)) {
                    cameraProvider.unbindAll()
                    if (bindCamera(cameraProvider, CameraSelector.LENS_FACING_BACK, preview)) {
                        lensFacing = CameraSelector.LENS_FACING_BACK
                        onNoFrontCamera()
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun setFullScreenPreviewVisible(visible: Boolean) {
        fullScreenPreviewVisible = visible
        setPreviewSurfaceProvider()
    }

    fun takePicture(callback: ImageCapture.OnImageCapturedCallback): Boolean {
        if (!isReady) return false
        imageCapture.takePicture(cameraExecutor, callback)
        return true
    }

    fun clearAnalyzer() {
        if (::imageAnalysis.isInitialized) {
            imageAnalysis.clearAnalyzer()
        }
    }

    private fun bindCamera(
        cameraProvider: ProcessCameraProvider,
        desiredLensFacing: Int,
        preview: Preview
    ): Boolean {
        return try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector(desiredLensFacing),
                preview,
                imageCapture,
                imageAnalysis
            )
            lensFacing = desiredLensFacing
            true
        } catch (error: Exception) {
            if (desiredLensFacing == CameraSelector.LENS_FACING_BACK) {
                onCameraFailed(error.message ?: "未知错误")
            }
            false
        }
    }

    private fun setPreviewSurfaceProvider() {
        val surfaceProvider = if (fullScreenPreviewVisible) {
            fullScreenSurfaceProvider()
        } else {
            previewSurfaceProvider()
        }
        previewUseCase?.setSurfaceProvider(surfaceProvider)
    }

    private fun cameraSelector(desiredLensFacing: Int): CameraSelector {
        return CameraSelector.Builder()
            .requireLensFacing(desiredLensFacing)
            .build()
    }
}
