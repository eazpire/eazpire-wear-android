package com.eazpire.wear.discovery

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Samsung devices (and some others) can crash ARCore on [Session.resume] when uncalibrated
 * gyro/accel sensors are not already streaming — see google-ar/arcore-android-sdk#1762.
 * Hold those sensors in continuous mode before the AR session starts.
 */
object ArCoreSensorWarmup {
    private var listener: SensorEventListener? = null
    private var sensorManager: SensorManager? = null

    fun start(context: Context) {
        if (listener != null) return
        val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val noop = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) = Unit
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val sensors = listOfNotNull(
            manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED),
            manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED),
        )
        sensors.forEach { sensor ->
            manager.registerListener(noop, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (sensors.isNotEmpty()) {
            listener = noop
            sensorManager = manager
        }
    }

    fun stop() {
        val manager = sensorManager ?: return
        listener?.let { manager.unregisterListener(it) }
        listener = null
        sensorManager = null
    }
}
