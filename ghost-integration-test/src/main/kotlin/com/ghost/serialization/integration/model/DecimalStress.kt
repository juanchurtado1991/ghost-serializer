package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DecimalStress(
    val big: Double,
    val small: Float,
    val precise: Double
)
