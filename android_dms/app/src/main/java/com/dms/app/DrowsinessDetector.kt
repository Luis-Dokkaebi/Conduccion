package com.dms.app

import java.util.LinkedList

enum class DrowsinessState {
    NORMAL,
    EMERGENCY_SLEEP_DETECTED,
    DRIVER_AWAKE
}

class DrowsinessDetector(
    private var bufferSize: Int = 45,
    private val sleepThreshold: Float = 0.22f,
    private val awakeThreshold: Float = 0.28f,
    private var awakeFramesRequired: Int = 15,
    private val calibrationTimeMs: Long = 10000L
) {
    private val earBuffer = LinkedList<Float>()
    private var currentState = DrowsinessState.NORMAL
    private var isThrottled = false

    fun setThrottled(throttled: Boolean) {
        if (isThrottled == throttled) return
        isThrottled = throttled

        if (throttled) {
            bufferSize = 15
            awakeFramesRequired = 5
            while (earBuffer.size > bufferSize) {
                earBuffer.removeFirst()
            }
        } else {
            bufferSize = 45
            awakeFramesRequired = 15
        }
    }

    private var isCalibrating = true
    private val calibrationEarValues = mutableListOf<Float>()
    private var baselineEar: Float? = null
    private var startTimeMs: Long = -1L
    private var sleepStartTimeMs: Long = -1L
    private var awakeStartTimeMs: Long = -1L

    fun processEar(ear: Float, timestampMs: Long): DrowsinessState {
        if (startTimeMs == -1L) {
            startTimeMs = timestampMs
        }

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

        earBuffer.addLast(ear)
        if (earBuffer.size > bufferSize) {
            earBuffer.removeFirst()
        }

        val averageEar = if (earBuffer.isNotEmpty()) earBuffer.average().toFloat() else 0f
        var emittedState = DrowsinessState.NORMAL

        val requiredSleepMs = if (isThrottled) 500L else 1500L
        val requiredAwakeMs = if (isThrottled) 165L else 500L

        if (currentState == DrowsinessState.NORMAL || currentState == DrowsinessState.DRIVER_AWAKE) {
            if (averageEar < sleepThreshold) {
                if (sleepStartTimeMs == -1L) sleepStartTimeMs = timestampMs
                if ((timestampMs - sleepStartTimeMs) >= requiredSleepMs) {
                    currentState = DrowsinessState.EMERGENCY_SLEEP_DETECTED
                    emittedState = DrowsinessState.EMERGENCY_SLEEP_DETECTED
                    awakeStartTimeMs = -1L
                } else {
                    emittedState = DrowsinessState.NORMAL
                    currentState = DrowsinessState.NORMAL
                }
            } else {
                sleepStartTimeMs = -1L
                emittedState = DrowsinessState.NORMAL
                currentState = DrowsinessState.NORMAL
            }
        } else if (currentState == DrowsinessState.EMERGENCY_SLEEP_DETECTED) {
            emittedState = DrowsinessState.EMERGENCY_SLEEP_DETECTED

            if (ear > awakeThreshold) {
                if (awakeStartTimeMs == -1L) awakeStartTimeMs = timestampMs
                if ((timestampMs - awakeStartTimeMs) >= requiredAwakeMs) {
                    currentState = DrowsinessState.NORMAL
                    emittedState = DrowsinessState.DRIVER_AWAKE
                    sleepStartTimeMs = -1L
                }
            } else {
                awakeStartTimeMs = -1L
            }
        }

        return emittedState
    }

    fun getBaselineEar(): Float? = baselineEar
    fun isCurrentlyCalibrating(): Boolean = isCalibrating
}
