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
data class UrlItem(
    val url: String,
    @Json(name = "expanded_url")
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String? = null,
    @Json(name = "display_url")
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String? = null,
    val indices: List<Int>
)
