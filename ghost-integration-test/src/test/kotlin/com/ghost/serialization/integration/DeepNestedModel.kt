package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DeepNestedModel(
    val mapOfLists: Map<String, List<Map<String, List<Int>>>>
)
