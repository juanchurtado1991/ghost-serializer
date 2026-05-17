package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DeepGenericModel(
    val data: Map<String, List<Map<String, List<String>>>>
)