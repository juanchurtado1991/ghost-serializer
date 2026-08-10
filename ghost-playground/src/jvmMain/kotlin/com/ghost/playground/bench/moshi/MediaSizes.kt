package com.ghost.playground.bench.moshi

import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class MediaSizes(
    val medium: MediaSize,
    val small: MediaSize,
    val thumb: MediaSize,
    val large: MediaSize,
)
