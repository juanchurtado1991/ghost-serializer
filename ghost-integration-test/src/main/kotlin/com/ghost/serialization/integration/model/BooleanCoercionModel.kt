package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class BooleanCoercionModel(
    val isActive: Boolean,
    val isEnabled: Boolean
)
