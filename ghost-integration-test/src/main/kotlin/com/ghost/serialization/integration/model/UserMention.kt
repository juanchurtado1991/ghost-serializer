package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class UserMention(
    @Json(name = "screen_name")
    @SerialName("screen_name") @GhostName("screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @Json(name = "id_str")
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>
)
