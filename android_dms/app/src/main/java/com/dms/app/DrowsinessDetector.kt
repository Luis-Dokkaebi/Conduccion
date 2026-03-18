package com.dms.app

import java.util.LinkedList

enum class DrowsinessState {
    NORMAL,
    EMERGENCY_SLEEP_DETECTED,
    DRIVER_AWAKE
}

class DrowsinessDetector(
    private val bufferSize: Int = 45,
    private val sleepThreshold: Float = 0.22f,
    private val awakeThreshold: Float = 0.28f,
    private val awakeFramesRequired: Int = 15,
    private val calibrationTimeMs: Long = 10000L
) {
    private val earBuffer = LinkedList<Float>()
    private var currentState = DrowsinessState.NORMAL
    private var awakeFramesCount = 0

    private var isCalibrating = true
    private val calibrationEarValues = mutableListOf<Float>()
    private var baselineEar: Float? = null
    private var startTimeMs: Long = -1L

    /**
     * Procesa un nuevo valor EAR y devuelve el estado resultante.
     */
    fun processEar(ear: Float, timestampMs: Long): DrowsinessState {
        // Inicializar el tiempo de inicio
        if (startTimeMs == -1L) {
            startTimeMs = timestampMs
        }

        // Lógica de calibración dinámica
        if (isCalibrating) {
            if (timestampMs - startTimeMs <= calibrationTimeMs) {
                calibrationEarValues.add(ear)
            } else {
                isCalibrating = false
                if (calibrationEarValues.isNotEmpty()) {
                    baselineEar = calibrationEarValues.average().toFloat()
                }
            }
        }

        // Manejo del búfer circular
        earBuffer.addLast(ear)
        if (earBuffer.size > bufferSize) {
            earBuffer.removeFirst()
        }

        val averageEar = if (earBuffer.isNotEmpty()) earBuffer.average().toFloat() else 0f
        var emittedState = DrowsinessState.NORMAL

        if (currentState == DrowsinessState.NORMAL || currentState == DrowsinessState.DRIVER_AWAKE) {
            if (earBuffer.size == bufferSize && averageEar < sleepThreshold) {
                currentState = DrowsinessState.EMERGENCY_SLEEP_DETECTED
                emittedState = DrowsinessState.EMERGENCY_SLEEP_DETECTED
                awakeFramesCount = 0
            } else {
                emittedState = DrowsinessState.NORMAL
                currentState = DrowsinessState.NORMAL
            }
        } else if (currentState == DrowsinessState.EMERGENCY_SLEEP_DETECTED) {
            emittedState = DrowsinessState.EMERGENCY_SLEEP_DETECTED

            if (ear > awakeThreshold) {
                awakeFramesCount++
                if (awakeFramesCount >= awakeFramesRequired) {
                    currentState = DrowsinessState.NORMAL
                    emittedState = DrowsinessState.DRIVER_AWAKE
                }
            } else {
                awakeFramesCount = 0
            }
        }

        return emittedState
    }

    fun getBaselineEar(): Float? = baselineEar
    fun isCurrentlyCalibrating(): Boolean = isCalibrating
}
