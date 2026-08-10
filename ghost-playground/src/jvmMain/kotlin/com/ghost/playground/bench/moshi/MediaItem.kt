package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class MediaItem(
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val indices: List<Int>,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_url_https") val mediaUrlHttps: String,
    val url: String,
    @Json(name = "display_url") val displayUrl: String,
    @Json(name = "expanded_url") val expandedUrl: String,
    val type: String,
    val sizes: MediaSizes,
    @Json(name = "source_status_id") val sourceStatusId: Long? = null,
    @Json(name = "source_status_id_str") val sourceStatusIdStr: String? = null,
)
