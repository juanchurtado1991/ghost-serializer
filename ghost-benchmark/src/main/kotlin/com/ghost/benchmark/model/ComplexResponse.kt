package com.ghost.benchmark.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class ComplexResponse(
    val status: String,
    val data: List<BenchUser>,
    val meta: ExtremeMetadata,
    val extras: Map<String, String>
)
