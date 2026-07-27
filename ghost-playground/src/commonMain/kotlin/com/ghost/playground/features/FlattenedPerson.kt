package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class FlattenedPerson(
    val name: String,
    @GhostFlatten("address.city")
    val city: String,
    @GhostFlatten("address.zip")
    val zip: String,
)
