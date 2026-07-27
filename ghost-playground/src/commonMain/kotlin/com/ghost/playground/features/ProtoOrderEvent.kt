package com.ghost.playground.features

import com.ghost.serialization.annotations.GhostProtoSerialization

/**
 * proto3 JSON mapping: int64 fields round-trip as quoted strings, and fields left at their
 * default value are omitted from the encoded output — both visible in this lab's output.
 */
@GhostProtoSerialization
data class ProtoOrderEvent(
    val orderId: Long,
    val label: String,
    val retries: Int = 0,
)
