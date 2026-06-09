@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi

/**
 * Platform-specific heuristics to balance performance and memory usage.
 * Using 'expect' allows us to tune Ghost for different environments (JVM vs Mobile).
 */
@InternalGhostApi
expect object GhostHeuristics {
    /**
     * The initial capacity for ArrayLists and HashMaps during deserialization.
     * Prevents excessive resizing.
     */
    val initialCollectionCapacity: Int

    /**
     * The maximum length of a string to be pooled.
     * Prevents large payloads from polluting the heap.
     */
    val maxStringPoolLength: Int

    /**
     * The maximum number of items allowed in a collection (List/Map) during deserialization.
     * Security limit to prevent DoS via memory exhaustion.
     */
    val maxCollectionSize: Int

    /**
     * The maximum distance to scan for a discriminator before giving up.
     */
    val maxDiscriminatorPeekDistance: Int

    /**
     * Max retained capacity for [com.ghost.serialization.writer.FlatByteArrayWriter] after [com.ghost.serialization.writer.FlatByteArrayWriter.reset].
     * Larger encoded payloads can reuse the grown buffer on the same thread; capacity above this is released.
     */
    val maxWarmWriteBufferCapacity: Int

    /**
     * Max retained capacity for [com.ghost.serialization.writer.FlatCharArrayWriter] after
     * [com.ghost.serialization.writer.FlatCharArrayWriter.reset].
     * Deliberately smaller than [maxWarmWriteBufferCapacity] because the char writer only
     * serves [ghostInternalEncodeToString] — producing text output — and very large String
     * payloads are rare compared to binary encoding workloads.
     */
    val maxWarmCharWriteBufferCapacity: Int
}
