package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class UserMention(
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>,
)
