package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostProtoSerialization

/**
 * Proto3 JSON mapping demo: `int64` fields round-trip as quoted strings, and fields at their
 * default value are omitted from encoded output.
 */
@GhostProtoSerialization
data class ProtoOrderEvent(
    val orderId: Long,
    val label: String,
    val retries: Int = 0,
)
