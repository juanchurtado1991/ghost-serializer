package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NestedGenericModel(
    val data: Map<String, List<Map<String, Int>>>
)