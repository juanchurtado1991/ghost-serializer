package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys

@GhostProtoSerialization
data class ProtoOneofEvent(
    val id: String,
    @GhostWrappedKeys(keys = ["text", "code"])
    val payload: OneofPayload,
)
