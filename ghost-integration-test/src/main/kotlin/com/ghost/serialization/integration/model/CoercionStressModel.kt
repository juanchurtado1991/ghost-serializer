package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class CoercionStressModel(
    val b1: Boolean,
    val b2: Boolean,
    val b3: Boolean,
    val b4: Boolean,
    val b5: Boolean,
    val b6: Boolean
)