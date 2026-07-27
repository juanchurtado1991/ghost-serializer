package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class MediaItem(
    val id: Long,
    @SerialName("id_str") @GhostName("id_str") val idStr: String,
    val indices: List<Int>,
    @SerialName("media_url") @GhostName("media_url") val mediaUrl: String,
    @SerialName("media_url_https") @GhostName("media_url_https") val mediaUrlHttps: String,
    val url: String,
    @SerialName("display_url") @GhostName("display_url") val displayUrl: String,
    @SerialName("expanded_url") @GhostName("expanded_url") val expandedUrl: String,
    val type: String,
    val sizes: MediaSizes,
    @SerialName("source_status_id") @GhostName("source_status_id") val sourceStatusId: Long? = null,
    @SerialName("source_status_id_str") @GhostName("source_status_id_str") val sourceStatusIdStr: String? = null,
)
