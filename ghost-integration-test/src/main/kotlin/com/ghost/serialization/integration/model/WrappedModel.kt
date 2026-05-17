package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap

@GhostSerialization
data class WrappedModel(
    val id: Int,
    @GhostWrap("metadata.info")
    val name: String,
    @GhostWrap("metadata.info")
    val age: Int,
    @GhostWrap("system.flags")
    val active: Boolean
)