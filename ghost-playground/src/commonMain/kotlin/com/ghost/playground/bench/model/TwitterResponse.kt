package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

/**
 * Matches the `twitter_macro.json` dataset used by ghost-benchmark's TwitterBenchmark.
 * Dual-annotated so the Speed Test tab can compare Ghost codegen against kotlinx.serialization.
 */
@Serializable
@GhostSerialization(textChannel = true)
data class TwitterResponse(
    val statuses: List<Tweet>,
)
