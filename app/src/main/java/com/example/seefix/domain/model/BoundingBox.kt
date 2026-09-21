package com.example.seefix.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BoundingBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
)
