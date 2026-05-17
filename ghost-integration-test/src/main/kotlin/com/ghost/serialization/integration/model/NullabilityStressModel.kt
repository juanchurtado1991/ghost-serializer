package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NullabilityStressModel(
    val nullableList: List<String?>?,
    val nullableMap: Map<String, Int?>?,
    val nestedNullable: List<List<Int?>?>?
)