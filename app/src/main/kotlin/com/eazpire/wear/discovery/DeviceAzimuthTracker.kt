package com.eazpire.wear.discovery

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/** Max pitch/roll (degrees) for compass-style map bearing — roughly portrait / upright. */
private const val MAX_COMPASS_PITCH_DEG = 45f
private const val MAX_COMPASS_ROLL_DEG = 50f

/**
 * Device heading sample from [Sensor.TYPE_ROTATION_VECTOR] remapped for portrait use.
 *
 * [isReliable] is false when the phone is too flat, tilted sideways, or the sensor is unreliable
 * (e.g. while lifting the device). Map rotation should freeze in that case.
 */
data class DeviceCompassReading(
    val azimuthDegrees: Float,
    val isReliable: Boolean,
)

/**
 * Device heading in degrees (0 = north, clockwise) from [Sensor.TYPE_ROTATION_VECTOR]
 * remapped for portrait use (phone held upright).
 */
@Composable
fun rememberDeviceAzimuth(): Float? {
    val reading = rememberDeviceCompassReading() ?: return null
    return reading.azimuthDegrees.takeIf { reading.isReliable }
}

@Composable
fun rememberDeviceCompassReading(): DeviceCompassReading? {
    val context = LocalContext.current
    var reading by remember { mutableStateOf<DeviceCompassReading?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (sensor == null) {
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedMatrix,
                    )
                    SensorManager.getOrientation(remappedMatrix, orientation)
                    val azimuth = (Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f
                    val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                    val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    val tiltOk = abs(pitchDeg) <= MAX_COMPASS_PITCH_DEG &&
                        abs(rollDeg) <= MAX_COMPASS_ROLL_DEG
                    val accuracyOk = sensorAccuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
                    reading = DeviceCompassReading(
                        azimuthDegrees = azimuth,
                        isReliable = tiltOk && accuracyOk,
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    sensorAccuracy = accuracy
                }
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    return reading
}
