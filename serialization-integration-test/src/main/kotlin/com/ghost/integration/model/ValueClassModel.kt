package com.ghost.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
@GhostSerialization
value class UserId(val value: Int)

@GhostSerialization
@Serializable
data class UserWithValueClass(
    val id: UserId,
    val name: String
)
