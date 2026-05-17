package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
@GhostSerialization
value class UserId(val value: Int)