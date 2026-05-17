package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ReservedWordModel(
    val `when`: String,
    val `val`: Int,
    val `fun`: Boolean,
    val reader: String,
    val writer: String,
    val index: Int,
    val mask: Long,
    val OPTIONS: String
)


