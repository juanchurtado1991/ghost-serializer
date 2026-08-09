package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

@GhostProtoSerialization
@Serializable
data class ProtoUInt64FieldMessage(
    val shard_id: ULong,
)
