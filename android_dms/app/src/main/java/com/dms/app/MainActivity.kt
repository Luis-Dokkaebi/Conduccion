package com.dms.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.NetworkType
import androidx.work.Constraints
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var redFlashOverlay: android.view.View
    private lateinit var dimmingOverlay: android.view.View
    private lateinit var cameraExecutor: ExecutorService
    private var faceLandmarker: FaceLandmarker? = null

    private lateinit var alertManager: AlertManager

    // Task 3.1: FSM para detección de somnolencia
    private val drowsinessDetector = DrowsinessDetector()

    // Task 5.1: Prevención de Thermal Throttling
    @Volatile private var isThrottled = false
    private var lastFrameTime = 0L
    private val THROTTLED_FRAME_INTERVAL_MS = 100L // 10 FPS
    private var dimmingTemporarilyDisabledUntil = 0L

    // Task 6.1: Almacenamiento Local (Store and Forward)
    private var sleepStartTimeMs: Long = 0L
    private var lastRecordedEar: Float = 0f

    companion object {
        private const val TAG = "DMS_CameraX"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        redFlashOverlay = findViewById(R.id.redFlashOverlay)
        dimmingOverlay = findViewById(R.id.dimmingOverlay)
        cameraExecutor = Executors.newSingleThreadExecutor()

        alertManager = AlertManager(this)

        setupFaceLandmarker()

        setupDimmingTouchListener()

        setupTelemetrySyncWorker()

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
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
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

    // Task 5.1.1: Monitor thermal status
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) // Given in tenths of a degree Celsius

                // > 40°C triggers throttling
                if (temperature >= 400 && !isThrottled) {
                    Log.w(TAG, "Thermal Throttling Activated! Temp: ${temperature / 10f}°C")
                    isThrottled = true
                    drowsinessDetector.setThrottled(true)
                }
                // < 38°C untriggers throttling (hysteresis)
                else if (temperature <= 380 && isThrottled) {
                    Log.i(TAG, "Thermal Throttling Deactivated. Temp: ${temperature / 10f}°C")
                    isThrottled = false
                    drowsinessDetector.setThrottled(false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        alertManager.stopAlarm(redFlashOverlay)
        unregisterReceiver(batteryReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        alertManager.stopAlarm(redFlashOverlay)
        cameraExecutor.shutdown()
        faceLandmarker?.close()
    }

    // Task 6.2.2: Crear un servicio de WorkManager que corra cada 15 min
    private fun setupTelemetrySyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<TelemetrySyncWorker>(
            15, TimeUnit.MINUTES // Minimum periodic interval is 15 minutes
        )
        .setConstraints(constraints)
        .build()

        WorkManager.getInstance(this).enqueue(syncWorkRequest)
        Log.i(TAG, "Enqueued periodic telemetry sync worker.")
    }

    // Task 5.1.3: Dimming mode touch logic
    private fun setupDimmingTouchListener() {
        dimmingOverlay.setOnClickListener {
            // Disable dimming for 5 seconds when touched
            dimmingTemporarilyDisabledUntil = SystemClock.uptimeMillis() + 5000L
            dimmingOverlay.visibility = View.GONE
        }

        // Clicks on the normal view also disable dimming for 5s
        viewFinder.setOnClickListener {
             dimmingTemporarilyDisabledUntil = SystemClock.uptimeMillis() + 5000L
        }
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

            // Task 2.2: Compute math metrics (EAR, MAR, and Head Pose)
            val leftEar = DrowsinessMath.calculateEar(landmarks, leftEyeIndices)
            val rightEar = DrowsinessMath.calculateEar(landmarks, rightEyeIndices)
            val avgEar = (leftEar + rightEar) / 2.0f

            val marMouthIndices = listOf(78, 13, 308, 14) // Left, Top, Right, Bottom
            val mar = DrowsinessMath.calculateMar(landmarks, marMouthIndices)

            val (yaw, pitch) = DrowsinessMath.calculateHeadPose(landmarks)

            Log.d(TAG, "Metrics -> EAR: $avgEar (L: $leftEar, R: $rightEar), MAR: $mar, HeadPose(Yaw,Pitch): ($yaw, $pitch)")

            // Task 3.1: Actualizar máquina de estados de somnolencia con el EAR
            val state = drowsinessDetector.processEar(avgEar, SystemClock.uptimeMillis())
            Log.d(TAG, "Drowsiness State: $state")
            if (drowsinessDetector.isCurrentlyCalibrating()) {
                Log.d(TAG, "Calibrating baseline EAR...")
            } else {
                Log.d(TAG, "Baseline EAR: ${drowsinessDetector.getBaselineEar()}")
            }

            // Task 4.1: Interfaz y Alertas Físicas
            when (state) {
                DrowsinessState.EMERGENCY_SLEEP_DETECTED -> {
                    if (sleepStartTimeMs == 0L) {
                        sleepStartTimeMs = SystemClock.uptimeMillis()
                        lastRecordedEar = avgEar
                    }

                    alertManager.startAlarm(redFlashOverlay)

                    // Task 5.1.3: Hide dimming overlay if alarm triggered
                    runOnUiThread { dimmingOverlay.visibility = View.GONE }
                }
                DrowsinessState.DRIVER_AWAKE -> {
                    if (sleepStartTimeMs != 0L) {
                        // Sleep episode just ended, record it
                        val durationSeconds = (SystemClock.uptimeMillis() - sleepStartTimeMs) / 1000f
                        recordMicroSleepEvent(lastRecordedEar, durationSeconds)
                        sleepStartTimeMs = 0L
                    }
                    alertManager.stopAlarm(redFlashOverlay)
                }
                else -> { /* Do nothing for normal state */ }
            }

            // Task 5.1.3: Update dimming overlay visibility based on throttling and touch timeout
            runOnUiThread {
                if (state != DrowsinessState.EMERGENCY_SLEEP_DETECTED) {
                    if (isThrottled && SystemClock.uptimeMillis() > dimmingTemporarilyDisabledUntil) {
                        dimmingOverlay.visibility = View.VISIBLE
                    } else {
                        dimmingOverlay.visibility = View.GONE
                    }
                }
            }
        }
    }

    // Task 6.1.2: Grabar en DB local cada vez que se dispare una alerta
    private fun recordMicroSleepEvent(earValue: Float, durationSeconds: Float) {
        val event = MicroSleepEvent(
            timestamp = System.currentTimeMillis(),
            earValue = earValue,
            durationSeconds = durationSeconds,
            gpsLat = 0.0, // Assuming GPS tracking is not implemented yet
            gpsLng = 0.0
        )
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(this@MainActivity).microSleepEventDao().insertEvent(event)
            Log.d(TAG, "Recorded MicroSleepEvent: $event")
        }
    }

    private inner class DmsImageAnalyzer(private val faceLandmarker: FaceLandmarker?) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            if (faceLandmarker == null) {
                imageProxy.close()
                return
            }

            // Task 5.1.2: Drop frames to meet 10 FPS if throttled
            val currentTime = SystemClock.uptimeMillis()
            if (isThrottled) {
                if (currentTime - lastFrameTime < THROTTLED_FRAME_INTERVAL_MS) {
                    imageProxy.close()
                    return // Skip frame
                }
            }
            lastFrameTime = currentTime

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
