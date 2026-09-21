package com.example.seefix.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager as AndroidSensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class DeviceOrientation(
    val compassHeading: Float = 0.0f,
    val pitch: Float = 0.0f,
    val roll: Float = 0.0f,
    val cardinalDirection: String = "N"
)

/**
 * Lifecycle-aware repository that monitors Accelerometer, Gyroscope, and Magnetometer sensors.
 * Calculates compass heading (0° - 360°), tilt pitch/roll, and device motion stability.
 * Registers listeners ONLY when active to optimize battery usage.
 */
class SensorRepository(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager

    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _orientation = MutableStateFlow(DeviceOrientation())
    val orientation: StateFlow<DeviceOrientation> = _orientation.asStateFlow()

    private val _isDeviceStable = MutableStateFlow(true)
    val isDeviceStable: StateFlow<Boolean> = _isDeviceStable.asStateFlow()

    private val gravityData = FloatArray(3)
    private val geomagneticData = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    private var isListening = false

    fun startListening() {
        if (isListening || sensorManager == null) return
        isListening = true

        accelerometer?.let {
            sensorManager.registerListener(this, it, AndroidSensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, AndroidSensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, AndroidSensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        isListening = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityData, 0, 3)
                hasGravity = true

                // Calculate motion stability
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val accelMag = sqrt(ax * ax + ay * ay + az * az)
                // Earth gravity is ~9.81 m/s^2. Deviation > 2.5 m/s^2 indicates motion/shaking
                val isStable = abs(accelMag - 9.81f) < 2.5f
                _isDeviceStable.value = isStable
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagneticData, 0, 3)
                hasGeomagnetic = true
            }
        }

        if (hasGravity && hasGeomagnetic) {
            val rotationMatrix = FloatArray(9)
            val inclinationMatrix = FloatArray(9)
            val success = AndroidSensorManager.getRotationMatrix(
                rotationMatrix,
                inclinationMatrix,
                gravityData,
                geomagneticData
            )

            if (success) {
                val orientationAngles = FloatArray(3)
                AndroidSensorManager.getOrientation(rotationMatrix, orientationAngles)

                // Azimuth / Compass Heading in radians converted to degrees (0° to 360°)
                var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                azimuthDeg = (azimuthDeg + 360f) % 360f

                // Pitch and Roll in degrees
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                val cardinal = getCardinalFromAzimuth(azimuthDeg)

                _orientation.value = DeviceOrientation(
                    compassHeading = azimuthDeg,
                    pitch = pitchDeg,
                    roll = rollDeg,
                    cardinalDirection = cardinal
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun getCardinalFromAzimuth(azimuthDegrees: Float): String {
            return when {
                azimuthDegrees >= 337.5 || azimuthDegrees < 22.5 -> "N"
                azimuthDegrees >= 22.5 && azimuthDegrees < 67.5 -> "NE"
                azimuthDegrees >= 67.5 && azimuthDegrees < 112.5 -> "E"
                azimuthDegrees >= 112.5 && azimuthDegrees < 157.5 -> "SE"
                azimuthDegrees >= 157.5 && azimuthDegrees < 202.5 -> "S"
                azimuthDegrees >= 202.5 && azimuthDegrees < 247.5 -> "SW"
                azimuthDegrees >= 247.5 && azimuthDegrees < 292.5 -> "W"
                azimuthDegrees >= 292.5 && azimuthDegrees < 337.5 -> "NW"
                else -> "N"
            }
        }
    }
}
