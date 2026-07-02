package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class ExtendedScalarsModel(
    val tags: Set<String>,
    val code: Byte,
    val port: Short,
    val letter: Char,
    val ratio: Float
)
