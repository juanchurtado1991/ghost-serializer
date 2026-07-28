package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ProtoAccountIds(val value: List<Long>)

@GhostProtoSerialization
@Serializable
data class ProtoAccountIdsMessage(
    val account_ids: ProtoAccountIds,
)

@GhostProtoSerialization
@Serializable
data class ProtoUInt64FieldMessage(
    val shard_id: ULong,
)

@GhostProtoSerialization
@Serializable
data class ProtoDeviceEventListItem(
    val device_id: Long,
    val label: String,
)
