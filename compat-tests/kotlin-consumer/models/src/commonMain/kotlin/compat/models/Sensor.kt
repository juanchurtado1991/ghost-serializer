package compat.models

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class Sensor(val id: String, val celsius: Double, val labels: List<String> = emptyList())
