package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class Address(
    val street: String,
    val city: String,
    val zipCode: String,
    val country: String = "US"
)
