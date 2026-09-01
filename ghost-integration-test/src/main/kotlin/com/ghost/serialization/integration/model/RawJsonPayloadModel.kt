package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class RawJsonPayloadModel(
    val id: String,
    val body: RawJson
)
