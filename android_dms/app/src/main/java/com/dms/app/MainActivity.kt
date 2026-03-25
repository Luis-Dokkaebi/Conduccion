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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.button.MaterialButton
import android.widget.ImageButton

enum class HmsState { ACTIVE, WARNING, CRITICAL }

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var redFlashOverlay: android.view.View
    private lateinit var dimmingOverlay: android.view.View
    private lateinit var backgroundRoot: android.view.View
    private lateinit var statusRing: CircularProgressIndicator
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var bottomControlBar: LinearLayout
    private lateinit var btnShiftSummary: MaterialButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnNetworkSync: ImageButton

    private lateinit var cameraExecutor: ExecutorService
    private var faceLandmarker: FaceLandmarker? = null

    private lateinit var alertManager: AlertManager
    private val drowsinessDetector = DrowsinessDetector()

    @Volatile private var isThrottled = false
    private var lastFrameTime = 0L
    private val THROTTLED_FRAME_INTERVAL_MS = 100L

    private var sleepStartTimeMs: Long = 0L
    private var lastRecordedEar: Float = 0f

    private val inactivityHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val blackoutRunnable = Runnable { enterBlackoutMode() }
    private var pulsingAnimators = mutableListOf<android.animation.ObjectAnimator>()

    private val clearanceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TelemetrySyncWorker.ACTION_CLEARANCE_UPDATE) {
                val status = intent.getStringExtra("status")
                val frsScore = intent.getFloatExtra("frs_score", 0f)
                val message = intent.getStringExtra("message")
                val restMinutes = intent.getIntExtra("mandatory_rest_minutes", 0)

                if (status == "BLOCKED_FATIGUE") {
                    val clearanceIntent = Intent(this@MainActivity, ClearanceActivity::class.java).apply {
                        putExtra("status", status)
                        putExtra("frs_score", frsScore)
                        putExtra("message", message)
                        putExtra("mandatory_rest_minutes", restMinutes)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(clearanceIntent)
                    finish()
                } else if (status == "WARNING") {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
        backgroundRoot = findViewById(R.id.backgroundRoot)
        statusRing = findViewById(R.id.statusRing)
        statusIcon = findViewById(R.id.statusIcon)
        statusText = findViewById(R.id.statusText)
        bottomControlBar = findViewById(R.id.bottomControlBar)
        btnShiftSummary = findViewById(R.id.btnShiftSummary)
        btnSettings = findViewById(R.id.btnSettings)
        btnNetworkSync = findViewById(R.id.btnNetworkSync)

        cameraExecutor = Executors.newSingleThreadExecutor()
        alertManager = AlertManager(this)

        setupFaceLandmarker()
        setupListeners()
        setupTelemetrySyncWorker()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        
        resetBlackoutTimer()
    }

    private fun setupListeners() {
        btnShiftSummary.setOnClickListener {
            exitBlackoutMode(immediate = false)
            ShiftSummaryBottomSheet().show(supportFragmentManager, "ShiftSummary")
        }

        val touchListener = View.OnTouchListener { _, _ ->
            exitBlackoutMode(immediate = false)
            false
        }
        
        dimmingOverlay.setOnTouchListener(touchListener)
        backgroundRoot.setOnTouchListener(touchListener)
        viewFinder.setOnTouchListener(touchListener)
    }

    private fun resetBlackoutTimer() {
        inactivityHandler.removeCallbacks(blackoutRunnable)
        inactivityHandler.postDelayed(blackoutRunnable, 45000L)
    }

    private fun enterBlackoutMode() {
        dimmingOverlay.animate().alpha(0.9f).setDuration(500).start()
        bottomControlBar.animate().alpha(0.0f).setDuration(500).withEndAction {
            bottomControlBar.visibility = View.GONE
        }.start()
        statusText.animate().alpha(0.0f).setDuration(500).start()
        statusRing.animate().alpha(0.5f).setDuration(500).start()
        statusIcon.animate().alpha(0.5f).setDuration(500).start()
        startPulsingAnimation()
    }

    private fun exitBlackoutMode(immediate: Boolean) {
        if (immediate) {
            dimmingOverlay.alpha = 0.0f
            bottomControlBar.alpha = 1.0f
            statusText.alpha = 1.0f
            statusRing.alpha = 1.0f
            statusIcon.alpha = 1.0f
            bottomControlBar.visibility = View.VISIBLE
        } else {
            dimmingOverlay.animate().alpha(0.0f).setDuration(300).start()
            bottomControlBar.visibility = View.VISIBLE
            bottomControlBar.animate().alpha(1.0f).setDuration(300).start()
            statusText.animate().alpha(1.0f).setDuration(300).start()
            statusRing.animate().alpha(1.0f).setDuration(300).start()
            statusIcon.animate().alpha(1.0f).setDuration(300).start()
        }
        stopPulsingAnimation()
        resetBlackoutTimer()
    }

    private fun startPulsingAnimation() {
        if (pulsingAnimators.isEmpty()) {
            pulsingAnimators.add(android.animation.ObjectAnimator.ofFloat(statusRing, "scaleX", 1f, 1.05f))
            pulsingAnimators.add(android.animation.ObjectAnimator.ofFloat(statusRing, "scaleY", 1f, 1.05f))
            pulsingAnimators.add(android.animation.ObjectAnimator.ofFloat(statusIcon, "scaleX", 1f, 1.05f))
            pulsingAnimators.add(android.animation.ObjectAnimator.ofFloat(statusIcon, "scaleY", 1f, 1.05f))
            pulsingAnimators.forEach {
                it.duration = 1000
                it.repeatCount = android.animation.ValueAnimator.INFINITE
                it.repeatMode = android.animation.ValueAnimator.REVERSE
                it.start()
            }
        }
    }

    private fun stopPulsingAnimation() {
        pulsingAnimators.forEach { it.cancel() }
        pulsingAnimators.clear()
        statusRing.scaleX = 1f
        statusRing.scaleY = 1f
        statusIcon.scaleX = 1f
        statusIcon.scaleY = 1f
    }

    private fun updateUIState(state: HmsState) {
        runOnUiThread {
            when(state) {
                HmsState.ACTIVE -> {
                    statusRing.setIndicatorColor(getColor(R.color.hms_green))
                    statusIcon.setColorFilter(getColor(R.color.hms_green))
                    statusIcon.setImageResource(R.drawable.ic_shield_check)
                    statusText.text = getString(R.string.hms_status_active)
                    statusText.setTextColor(Color.WHITE)
                }
                HmsState.WARNING -> {
                    statusRing.setIndicatorColor(getColor(R.color.hms_yellow))
                    statusIcon.setColorFilter(getColor(R.color.hms_yellow))
                    statusIcon.setImageResource(R.drawable.ic_warning)
                    statusText.text = getString(R.string.hms_status_warning)
                    statusText.setTextColor(getColor(R.color.hms_yellow))
                }
                HmsState.CRITICAL -> {
                    statusRing.setIndicatorColor(getColor(R.color.hms_red))
                    statusIcon.setColorFilter(getColor(R.color.hms_red))
                    statusIcon.setImageResource(R.drawable.ic_sleep)
                    statusText.text = getString(R.string.hms_status_critical)
                    statusText.setTextColor(getColor(R.color.hms_red))
                    exitBlackoutMode(immediate = true)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, DmsImageAnalyzer(faceLandmarker))
                }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (temperature >= 400 && !isThrottled) {
                    isThrottled = true
                    drowsinessDetector.setThrottled(true)
                } else if (temperature <= 380 && isThrottled) {
                    isThrottled = false
                    drowsinessDetector.setThrottled(false)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        LocalBroadcastManager.getInstance(this).registerReceiver(
            clearanceReceiver, IntentFilter(TelemetrySyncWorker.ACTION_CLEARANCE_UPDATE)
        )
    }

    override fun onPause() {
        super.onPause()
        alertManager.stopAlarm(redFlashOverlay)
        unregisterReceiver(batteryReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(clearanceReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        alertManager.stopAlarm(redFlashOverlay)
        cameraExecutor.shutdown()
        faceLandmarker?.close()
        endShiftAndGenerateReport()
        inactivityHandler.removeCallbacksAndMessages(null)
        stopPulsingAnimation()
    }

    private fun endShiftAndGenerateReport() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://10.0.2.2:8000")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val dmsApi = retrofit.create(DmsApi::class.java)
                val response = dmsApi.endShift("driver_123")
                if (response.isSuccessful) {
                    Log.i(TAG, "Successfully triggered end_shift API.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling end_shift API", e)
            }
        }
    }

    private fun setupTelemetrySyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val syncWorkRequest = PeriodicWorkRequestBuilder<TelemetrySyncWorker>(
            15, TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TelemetrySyncWorker",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
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
            val leftEyeIndices = listOf(33, 160, 158, 133, 153, 144)
            val rightEyeIndices = listOf(362, 385, 387, 263, 373, 380)

            val leftEar = DrowsinessMath.calculateEar(landmarks, leftEyeIndices)
            val rightEar = DrowsinessMath.calculateEar(landmarks, rightEyeIndices)
            val avgEar = (leftEar + rightEar) / 2.0f

            val (yaw, pitch) = DrowsinessMath.calculateHeadPose(landmarks)

            val state = drowsinessDetector.processEar(avgEar, SystemClock.uptimeMillis())
            
            var hmsState = HmsState.ACTIVE

            when (state) {
                DrowsinessState.EMERGENCY_SLEEP_DETECTED -> {
                    hmsState = HmsState.CRITICAL
                    if (sleepStartTimeMs == 0L) {
                        sleepStartTimeMs = SystemClock.uptimeMillis()
                        lastRecordedEar = avgEar
                    }
                    alertManager.startAlarm(redFlashOverlay)
                }
                DrowsinessState.DRIVER_AWAKE -> {
                    if (sleepStartTimeMs != 0L) {
                        val durationSeconds = (SystemClock.uptimeMillis() - sleepStartTimeMs) / 1000f
                        recordMicroSleepEvent(lastRecordedEar, durationSeconds)
                        sleepStartTimeMs = 0L
                    }
                    alertManager.stopAlarm(redFlashOverlay)
                }
                else -> { 
                    if (Math.abs(yaw) > 20 || Math.abs(pitch) > 20) {
                        hmsState = HmsState.WARNING
                    }
                }
            }
            
            updateUIState(hmsState)
        }
    }

    private fun recordMicroSleepEvent(earValue: Float, durationSeconds: Float) {
        val event = MicroSleepEvent(
            timestamp = System.currentTimeMillis(),
            earValue = earValue,
            durationSeconds = durationSeconds,
            gpsLat = 0.0,
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
            val currentTime = SystemClock.uptimeMillis()
            if (isThrottled) {
                if (currentTime - lastFrameTime < THROTTLED_FRAME_INTERVAL_MS) {
                    imageProxy.close()
                    return
                }
            }
            lastFrameTime = currentTime
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { proxy ->
                bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
            }
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                postScale(-1f, 1f, imageProxy.width.toFloat() / 2, imageProxy.height.toFloat() / 2)
            }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
            )
            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            try {
                faceLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Error in detectAsync: ${e.message}")
            }
        }
    }
}
