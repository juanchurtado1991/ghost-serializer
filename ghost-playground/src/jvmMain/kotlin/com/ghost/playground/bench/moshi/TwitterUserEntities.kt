package com.ghost.playground.bench.moshi

import com.squareup.moshi.JsonClass

/** JVM-only Moshi codegen models for the Speed Test lab, mirroring `com.ghost.playground.bench.model`. */
@JsonClass(generateAdapter = true)
data class TwitterUserEntities(
    val url: UrlContainer? = null,
    val description: UrlContainer,
)
