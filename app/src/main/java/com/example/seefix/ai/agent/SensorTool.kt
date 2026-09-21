package com.example.seefix.ai.agent

import com.example.seefix.sensors.DeviceOrientation
import com.example.seefix.sensors.SensorRepository

class SensorTool(
    private val sensorRepository: SensorRepository? = null
) : SeeFixTool {
    override val name: String = "sensor_tool"
    override val description: String = "Reads device compass heading and accelerometer orientation."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = emptyList(),
        optionalParameters = listOf("sensorType"),
        description = "Read device sensors"
    )

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        return ToolValidationResult(isValid = true)
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val orientation = sensorRepository?.orientation?.value ?: DeviceOrientation(
            compassHeading = 180.0f,
            pitch = 2.5f,
            roll = 0.1f,
            cardinalDirection = "S"
        )
        val isStable = sensorRepository?.isDeviceStable?.value ?: true
        val motionState = if (isStable) "STATIONARY" else "MOVING"

        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "compassHeading" to orientation.compassHeading.toString(),
                "cardinalDirection" to orientation.cardinalDirection,
                "pitch" to orientation.pitch.toString(),
                "roll" to orientation.roll.toString(),
                "motionState" to motionState,
                "isStable" to isStable.toString()
            )
        )
    }
}
