package com.ghost.benchmark

/** Per-engine ranking row for synthetic result tables. */
internal data class EngineRank(
    val name: String,
    val nanos: Long,
    val mem: Long,
    val stDevNanos: Long
)
