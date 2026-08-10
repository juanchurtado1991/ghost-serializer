package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class UrlItem(
    val url: String,
    @Json(name = "expanded_url") val expandedUrl: String? = null,
    @Json(name = "display_url") val displayUrl: String? = null,
    val indices: List<Int>,
)
