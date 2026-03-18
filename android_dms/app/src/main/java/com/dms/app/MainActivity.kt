package com.dms.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var faceLandmarker: FaceLandmarker? = null

    companion object {
        private const val TAG = "DMS_CameraX"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupFaceLandmarker()

        // Solicitar permisos de cámara
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Provider de ciclo de vida para CameraX
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Configurar el Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Task 1.1.2: Crear un analizador de imágenes a max 640x480
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, DmsImageAnalyzer(faceLandmarker))
                }

            // Task 1.1.1: Usar cámara frontal
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Desvincular usos previos antes de re-vincular
                cameraProvider.unbindAll()

                // Vincular cámara al ciclo de vida de la Activity
                // Task 1.1.3: Manejo automático de rotación ocurre a nivel de CameraX internamente por el PreviewView
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permisos denegados por el usuario.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarker?.close()
    }

    private fun setupFaceLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener { result, inputImage ->
                handleFaceLandmarkerResult(result)
            }
            .setErrorListener { error ->
                Log.e(TAG, "Face Landmarker Error: ${error.message}")
            }

        faceLandmarker = FaceLandmarker.createFromOptions(this, optionsBuilder.build())
    }

    private fun handleFaceLandmarkerResult(result: FaceLandmarkerResult) {
        if (result.faceLandmarks().isNotEmpty()) {
            val landmarks = result.faceLandmarks()[0]

            // Task 2.1.4: Extract critical indices
            val leftEyeIndices = listOf(33, 160, 158, 133, 153, 144)
            val rightEyeIndices = listOf(362, 385, 387, 263, 373, 380)
            val mouthIndices = listOf(78, 191, 80, 81, 13, 311, 308, 402, 14, 178)
            val headIndices = listOf(1, 152)

            Log.d(TAG, "Extracted critical landmarks:")
            Log.d(TAG, "Left Eye: ${leftEyeIndices.map { landmarks[it] }}")
            Log.d(TAG, "Right Eye: ${rightEyeIndices.map { landmarks[it] }}")
            Log.d(TAG, "Mouth: ${mouthIndices.map { landmarks[it] }}")
            Log.d(TAG, "Head: ${headIndices.map { landmarks[it] }}")
        }
    }

    private class DmsImageAnalyzer(private val faceLandmarker: FaceLandmarker?) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (faceLandmarker == null) {
                imageProxy.close()
                return
            }

            // Convert ImageProxy to Bitmap
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { proxy ->
                bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
            }

            // Rotate Bitmap according to ImageProxy rotation
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f, imageProxy.width.toFloat() / 2, imageProxy.height.toFloat() / 2)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
            )

            // Convert to MPImage
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            // Run face landmarker
            try {
                faceLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error in detectAsync: ${e.message}")
            }
        }
    }
}
