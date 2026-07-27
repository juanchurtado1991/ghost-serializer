package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class UrlItem(
    val url: String,
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String? = null,
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String? = null,
    val indices: List<Int>,
)
