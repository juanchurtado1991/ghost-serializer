package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NullablePrimitives(
    val i: Int?,
    val l: Long?,
    val b: Boolean?,
    val d: Double?,
    val s: String?
)
