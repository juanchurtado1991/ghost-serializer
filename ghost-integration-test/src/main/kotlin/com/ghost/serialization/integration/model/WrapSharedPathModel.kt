package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap

@GhostSerialization
data class WrapSharedPathModel(
    @GhostWrap("metadata.info")
    val name: String,
    @GhostWrap("metadata.auth")
    val token: String,
    @GhostWrap("system.flags.active")
    val active: Boolean
)
