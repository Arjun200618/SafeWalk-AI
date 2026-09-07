package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.MotionData
import com.example.data.MotionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * MotionDetector: Captures accelerometer and gyroscope data from hardware sensors.
 *
 * Employs a low-pass/high-pass filtering approach to isolate gravity vs dynamic movement,
 * and detects sudden acceleration spikes, sharp angular rotations, and free-fall impact sequences.
 */
class MotionDetector(
    context: Context,
    private val onSuddenMotionDetected: () -> Unit,
    private val onPossibleFallDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val isAccelerometerAvailable: Boolean = accelerometer != null
    val isGyroscopeAvailable: Boolean = gyroscope != null

    private val _motionData = MutableStateFlow(MotionData())
    val motionData: StateFlow<MotionData> = _motionData.asStateFlow()

    // Motion detection thresholds & state
    private var freeFallTimestamp: Long = 0L
    private var lastEventEmittedTimestamp: Long = 0L
    private val eventCooldownMs = 2500L

    // Exponential smoothing for gravity
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.8f
    private val alpha = 0.8f // filter coefficient

    fun startListening() {
        reset()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        _motionData.value = _motionData.value.copy(status = MotionStatus.NORMAL)
    }

    fun reset() {
        freeFallTimestamp = 0L
        lastEventEmittedTimestamp = 0L
        _motionData.value = MotionData()
    }

    fun simulateNormalWalking() {
        _motionData.value = MotionData(
            accelX = 0.4f,
            accelY = 1.2f,
            accelZ = 9.8f,
            accelMagnitude = 9.9f,
            gyroX = 0.05f,
            gyroY = 0.12f,
            gyroZ = 0.08f,
            gyroMagnitude = 0.2f,
            status = MotionStatus.NORMAL,
            lastEventTimestamp = System.currentTimeMillis()
        )
    }

    fun simulateSuddenMotion() {
        _motionData.value = MotionData(
            accelX = 14.5f,
            accelY = 18.2f,
            accelZ = 12.0f,
            accelMagnitude = 26.2f,
            gyroX = 3.2f,
            gyroY = 4.1f,
            gyroZ = 2.8f,
            gyroMagnitude = 5.8f,
            status = MotionStatus.SUDDEN_MOVEMENT,
            lastEventTimestamp = System.currentTimeMillis()
        )
    }

    fun simulatePossibleFall() {
        _motionData.value = MotionData(
            accelX = 1.1f,
            accelY = 0.8f,
            accelZ = 24.5f,
            accelMagnitude = 24.6f,
            gyroX = 4.5f,
            gyroY = 3.8f,
            gyroZ = 2.1f,
            gyroMagnitude = 6.2f,
            status = MotionStatus.POSSIBLE_FALL,
            lastEventTimestamp = System.currentTimeMillis()
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                // Low-pass filter for gravity
                gravityX = alpha * gravityX + (1 - alpha) * x
                gravityY = alpha * gravityY + (1 - alpha) * y
                gravityZ = alpha * gravityZ + (1 - alpha) * z

                // High-pass filter for dynamic linear acceleration
                val linearX = x - gravityX
                val linearY = y - gravityY
                val linearZ = z - gravityZ
                val dynamicMagnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
                val totalMagnitude = sqrt(x * x + y * y + z * z)

                // 1. Fall detection pattern (Free-fall dip followed by impact spike)
                if (totalMagnitude < 3.8f) {
                    freeFallTimestamp = now
                } else if (freeFallTimestamp > 0 && (now - freeFallTimestamp) in 150L..900L) {
                    if (totalMagnitude > 22.0f || dynamicMagnitude > 14.0f) {
                        freeFallTimestamp = 0L
                        if (now - lastEventEmittedTimestamp > eventCooldownMs) {
                            lastEventEmittedTimestamp = now
                            _motionData.value = _motionData.value.copy(
                                accelX = x, accelY = y, accelZ = z,
                                accelMagnitude = totalMagnitude,
                                status = MotionStatus.POSSIBLE_FALL,
                                lastEventTimestamp = now
                            )
                            onPossibleFallDetected()
                            return
                        }
                    }
                } else if (now - freeFallTimestamp > 1000L) {
                    freeFallTimestamp = 0L
                }

                // 2. Sudden movement spike detection
                var detectedStatus = MotionStatus.NORMAL
                if (dynamicMagnitude > 13.5f || totalMagnitude > 24.0f) {
                    detectedStatus = MotionStatus.SUDDEN_MOVEMENT
                    if (now - lastEventEmittedTimestamp > eventCooldownMs) {
                        lastEventEmittedTimestamp = now
                        onSuddenMotionDetected()
                    }
                }

                _motionData.value = _motionData.value.copy(
                    accelX = x,
                    accelY = y,
                    accelZ = z,
                    accelMagnitude = totalMagnitude,
                    status = if (now - lastEventEmittedTimestamp < 2000L) {
                        _motionData.value.status
                    } else {
                        detectedStatus
                    }
                )
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                val gyroMag = sqrt(gx * gx + gy * gy + gz * gz)

                // Rapid rotational spin check (> 4.5 rad/sec)
                if (gyroMag > 4.5f && (now - lastEventEmittedTimestamp > eventCooldownMs)) {
                    lastEventEmittedTimestamp = now
                    _motionData.value = _motionData.value.copy(
                        gyroX = gx, gyroY = gy, gyroZ = gz,
                        gyroMagnitude = gyroMag,
                        status = MotionStatus.SUDDEN_MOVEMENT,
                        lastEventTimestamp = now
                    )
                    onSuddenMotionDetected()
                    return
                }

                _motionData.value = _motionData.value.copy(
                    gyroX = gx, gyroY = gy, gyroZ = gz,
                    gyroMagnitude = gyroMag
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
