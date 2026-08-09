package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ProtoAccountIds(val value: List<Long>)
