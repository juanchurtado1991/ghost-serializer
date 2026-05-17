package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class ComplexResponse(
    val status: String,
    val users: List<BenchUser>,
    val metadata: ExtremeMetadata,
    val shards: Map<String, String>
)
