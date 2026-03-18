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

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService

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
                    it.setAnalyzer(cameraExecutor, DmsImageAnalyzer())
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
    }

    private class DmsImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(image: ImageProxy) {
            // Se obtiene el buffer YUV del frame
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.capacity())
            buffer.get(data)

            Log.d(TAG, "Frame YUV analizado. Tamaño: ${data.size} bytes. Rotación: ${image.imageInfo.rotationDegrees}")

            // TODO: Integrar Tensor TFLite / MediaPipe Face Mesh aquí

            image.close()
        }
    }
}
