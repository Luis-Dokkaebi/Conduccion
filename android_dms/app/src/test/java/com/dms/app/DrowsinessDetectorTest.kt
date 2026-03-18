package com.dms.app

import org.junit.Assert.*
import org.junit.Test

class DrowsinessDetectorTest {

    @Test
    fun testInitialCalibration() {
        val detector = DrowsinessDetector(calibrationTimeMs = 1000L) // 1 second calibration
        var currentTime = 0L

        // During first second, it should be calibrating
        for (i in 1..30) {
            val state = detector.processEar(0.30f, currentTime)
            assertEquals(DrowsinessState.NORMAL, state)
            assertTrue(detector.isCurrentlyCalibrating())
            currentTime += 33L // ~30 fps
        }

        // Jump ahead to finish calibration
        currentTime += 1000L
        detector.processEar(0.30f, currentTime)

        assertFalse(detector.isCurrentlyCalibrating())
        assertEquals(0.30f, detector.getBaselineEar() ?: 0f, 0.01f)
    }

    @Test
    fun testEmergencySleepDetection() {
        val detector = DrowsinessDetector(bufferSize = 45, sleepThreshold = 0.22f)
        var currentTime = 0L

        // Fill buffer with sleepy values
        for (i in 1..44) {
            val state = detector.processEar(0.20f, currentTime)
            assertEquals(DrowsinessState.NORMAL, state) // Not 45 frames yet
            currentTime += 33L
        }

        // 45th frame triggers the alarm
        val alarmState = detector.processEar(0.20f, currentTime)
        assertEquals(DrowsinessState.EMERGENCY_SLEEP_DETECTED, alarmState)
    }

    @Test
    fun testRecoveryAwake() {
        val detector = DrowsinessDetector(
            bufferSize = 10, // Short buffer for test
            sleepThreshold = 0.22f,
            awakeThreshold = 0.28f,
            awakeFramesRequired = 5,
            calibrationTimeMs = 0L
        )

        var currentTime = 0L

        // Trigger sleep
        for (i in 1..10) {
            detector.processEar(0.20f, currentTime)
            currentTime += 33L
        }

        // Now we should be asleep
        var state = detector.processEar(0.20f, currentTime)
        assertEquals(DrowsinessState.EMERGENCY_SLEEP_DETECTED, state)

        // Wake up for 4 frames
        for (i in 1..4) {
            state = detector.processEar(0.30f, currentTime)
            assertEquals(DrowsinessState.EMERGENCY_SLEEP_DETECTED, state)
            currentTime += 33L
        }

        // 5th frame of waking up should trigger DRIVER_AWAKE
        state = detector.processEar(0.30f, currentTime)
        assertEquals(DrowsinessState.DRIVER_AWAKE, state)

        // 6th frame goes back to NORMAL processing mode
        state = detector.processEar(0.30f, currentTime)
        assertEquals(DrowsinessState.NORMAL, state)
    }

    @Test
    fun testResetAwakeFramesCountIfDroppingBelowThreshold() {
        val detector = DrowsinessDetector(
            bufferSize = 10,
            sleepThreshold = 0.22f,
            awakeThreshold = 0.28f,
            awakeFramesRequired = 5,
            calibrationTimeMs = 0L
        )

        var currentTime = 0L
        for (i in 1..10) {
            detector.processEar(0.20f, currentTime)
        }

        // 2 awake frames
        detector.processEar(0.30f, currentTime)
        detector.processEar(0.30f, currentTime)

        // 1 sleepy frame interrupts the recovery
        val state = detector.processEar(0.20f, currentTime)
        assertEquals(DrowsinessState.EMERGENCY_SLEEP_DETECTED, state)

        // Now we need 5 full awake frames again
        for (i in 1..4) {
            detector.processEar(0.30f, currentTime)
        }
        val nextState = detector.processEar(0.30f, currentTime)
        assertEquals(DrowsinessState.DRIVER_AWAKE, nextState)
    }
}
