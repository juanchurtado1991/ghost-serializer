package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DefaultValueNullModel(
    val name: String = "Default",
    val age: Int? = 42
)