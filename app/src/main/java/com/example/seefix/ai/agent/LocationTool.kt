package com.example.seefix.ai.agent

import com.example.seefix.location.LocationManager

class LocationTool(
    private val locationManager: LocationManager? = null
) : SeeFixTool {
    override val name: String = "location_tool"
    override val description: String = "Retrieves current user location coordinates and city/address."
    override val permission: ToolPermission = ToolPermission.READ_ONLY
    override val inputSchema: ToolInputSchema = ToolInputSchema(
        requiredParameters = emptyList(),
        optionalParameters = listOf("accuracy"),
        description = "Get location data"
    )

    override suspend fun validate(arguments: Map<String, String>): ToolValidationResult {
        return ToolValidationResult(isValid = true)
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val loc = locationManager?.userLocation?.value
        if (loc == null) {
            return ToolResult(
                toolName = name,
                isSuccess = false,
                errorMessage = "LOCATION_UNAVAILABLE: Location is not currently available."
            )
        }
        val accuracyArg = arguments["accuracy"] ?: "10.0m"
        return ToolResult(
            toolName = name,
            isSuccess = true,
            outputData = mapOf(
                "latitude" to loc.latitude.toString(),
                "longitude" to loc.longitude.toString(),
                "accuracy" to accuracyArg,
                "city" to loc.address,
                "timestampMs" to System.currentTimeMillis().toString()
            )
        )
    }
}
