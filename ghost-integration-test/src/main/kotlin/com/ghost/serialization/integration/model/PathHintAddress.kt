package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

/** Nested list path: `$.user.addresses[1].zip`. */
@GhostSerialization
data class PathHintAddress(
    val zip: Int,
)
