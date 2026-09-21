package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkDomain {
    MECHANICAL,
    ELECTRICAL,
    ELECTRONICS,
    AUTOMOTIVE,
    INDUSTRIAL,
    CONSTRUCTION,
    PLUMBING,
    HVAC,
    AGRICULTURE,
    APPLIANCE,
    IT_NETWORKING,
    HOME_MAINTENANCE,
    GENERAL;

    fun toUserFacingName(): String = when (this) {
        MECHANICAL -> "Mechanical"
        ELECTRICAL -> "Electrical"
        ELECTRONICS -> "Electronics"
        AUTOMOTIVE -> "Automotive"
        INDUSTRIAL -> "Industrial"
        CONSTRUCTION -> "Construction"
        PLUMBING -> "Plumbing"
        HVAC -> "HVAC"
        AGRICULTURE -> "Agriculture"
        APPLIANCE -> "Appliance"
        IT_NETWORKING -> "IT & Networking"
        HOME_MAINTENANCE -> "Home Maintenance"
        GENERAL -> "General"
    }
}
