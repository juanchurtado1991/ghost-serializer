package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class RawJsonPayloadModel(
    val id: String,
    val body: RawJson
)

@GhostSerialization
data class RawJsonAttributeState(
    @GhostName("value") val value: RawJson? = null,
    @GhostName("data") val data: Map<String, RawJson>? = null
)

@GhostSerialization
data class RawJsonListModel(
    val items: List<RawJson>
)
