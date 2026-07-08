package com.ghost.serialization.spring.fixture

import com.ghost.serialization.annotations.GhostProtoSerialization

@GhostProtoSerialization
data class ProtoEventMessage(
    val device_id: Long,
    val label: String,
)
