package com.ghost.playground.bench.moshi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class UserMention(
    @Json(name = "screen_name") val screenName: String,
    val name: String,
    val id: Long,
    @Json(name = "id_str") val idStr: String,
    val indices: List<Int>,
)
