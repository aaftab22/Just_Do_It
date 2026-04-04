package com.darksunTechnologies.justdoit

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * ShakeDetector uses the accelerometer to detect shake gestures and trigger an action.
 * Used to quickly launch the capture bottom sheet.
 */
class ShakeDetector(
    context: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Minimum acceleration magnitude to register as a shake (Gravity is ~9.8)
    private var shakeThresholdGravity = 2.5f
    // Debounce to prevent multiple triggers from one shake motion
    private val shakeSlopTimeMs = 2000
    private var lastShakeTime: Long = 0

    /**
     * Start listening for shakes. Tie this to Activity or Fragment onResume.
     */
    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    /**
     * Stop listening. Tie this to Activity or Fragment onPause.
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Set a custom threshold (multiplier of normal gravity standard 9.81m/s^2).
     * E.g. 2.0 = gentle shake, 3.0 = forceful shake. Default 2.5.
     */
    fun setThreshold(threshold: Float) {
        shakeThresholdGravity = threshold
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // gForce will be close to 1 when there is no movement.
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > shakeThresholdGravity) {
            val now = System.currentTimeMillis()
            // ignore shake events too close to each other (debounce)
            if (lastShakeTime + shakeSlopTimeMs > now) {
                return
            }

            lastShakeTime = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
