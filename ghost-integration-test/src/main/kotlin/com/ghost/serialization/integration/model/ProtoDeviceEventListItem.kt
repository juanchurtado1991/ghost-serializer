package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

@GhostProtoSerialization
@Serializable
data class ProtoDeviceEventListItem(
    val device_id: Long,
    val label: String,
)
