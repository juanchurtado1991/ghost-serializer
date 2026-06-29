package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class RawPayloadModel(val id: String, val body: ByteArray)
