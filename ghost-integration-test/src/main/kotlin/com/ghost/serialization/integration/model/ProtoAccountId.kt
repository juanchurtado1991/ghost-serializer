package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@GhostSerialization
value class ProtoAccountId(val value: Long)
