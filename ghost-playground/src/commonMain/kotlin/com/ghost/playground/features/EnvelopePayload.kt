package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class EnvelopePayload(
    val event: String,
    val meta: RawJson,
)
