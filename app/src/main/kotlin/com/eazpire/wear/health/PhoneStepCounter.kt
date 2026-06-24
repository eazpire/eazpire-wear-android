package com.eazpire.wear.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Session-relative step delta from [Sensor.TYPE_STEP_COUNTER] (works indoors without GPS).
 */
class PhoneStepCounter(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var listener: SensorEventListener? = null
    private var baseline: Float? = null
    var onStepsDelta: ((Long) -> Unit)? = null

    fun isAvailable(): Boolean = stepSensor != null

    fun start() {
        stop()
        baseline = null
        if (stepSensor == null) {
            onStepsDelta?.invoke(0L)
            return
        }
        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (baseline == null) baseline = event.values[0]
                val delta = (event.values[0] - (baseline ?: event.values[0])).toLong().coerceAtLeast(0)
                onStepsDelta?.invoke(delta)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        baseline = null
    }

    /** Reset baseline on resume so only steps after resume count toward the new segment. */
    fun resetSegment() {
        baseline = null
    }
}
