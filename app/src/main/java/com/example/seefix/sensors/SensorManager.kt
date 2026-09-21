package com.example.seefix.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager as AndroidSensorManager

/**
 * Device sensor manager wrapping hardware sensors and SensorRepository.
 */
class SensorManager(private val context: Context) {
    val sensorRepository = SensorRepository(context)

    fun hasAccelerometer(): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? AndroidSensorManager
        return sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}
