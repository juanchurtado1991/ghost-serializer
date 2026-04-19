package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class MaliceModel(
    val simple: String = "",
    val nested: MaliceModel? = null
)

@GhostSerialization
@Serializable
data class DecimalStress(
    val value: Double = 0.0,
    val text: String = ""
)
