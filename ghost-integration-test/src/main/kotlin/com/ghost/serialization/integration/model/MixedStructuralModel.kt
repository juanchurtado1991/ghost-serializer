package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap

@GhostSerialization
data class MixedStructuralModel(
    val id: Int,
    @GhostFlatten("nested.flat")
    val flatValue: String,
    @GhostWrap("wrapped.nested")
    val wrappedValue: String
)