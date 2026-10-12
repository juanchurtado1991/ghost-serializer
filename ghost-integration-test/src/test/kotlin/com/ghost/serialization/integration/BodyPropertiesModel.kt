package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class BodyPropertiesModel(
    val base: Int,
) {
    val computed: Int get() = base + 1
    val stored: String = "stored"
}
