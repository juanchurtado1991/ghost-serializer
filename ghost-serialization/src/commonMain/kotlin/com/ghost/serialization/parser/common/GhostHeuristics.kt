@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics.maxWarmWriteBufferCapacity


/**
 * Platform-specific heuristics to balance performance and memory usage.
 * Using 'expect' allows us to tune Ghost for different environments (JVM vs Mobile).
 *
 * Debt: [maxCollectionSize] and related caps differ by actual (Android tighter than JVM/Native/Wasm);
 * not a single cross-platform constant. Registry discovery is separate
 * ([com.ghost.serialization.discoverRegistries]) — iOS/Wasm discovery is empty and requires
 * manual [com.ghost.serialization.Ghost.addRegistry].
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
     *
     * Platform defaults differ (e.g. Android 50k, Native/Wasm 500k, JVM 1M); not unified.
     */
    val maxCollectionSize: Int

    /**
     * The maximum distance to scan for a discriminator before giving up.
     */
    val maxDiscriminatorPeekDistance: Int

    /**
     * Max retained capacity for `FlatByteArrayWriter` after `FlatByteArrayWriter.reset`.
     * Larger encoded payloads can reuse the grown buffer on the same thread; capacity above this is released.
     */
    val maxWarmWriteBufferCapacity: Int

    /**
     * Max retained capacity for `FlatCharArrayWriter` after
     * `FlatCharArrayWriter.reset`.
     * Deliberately smaller than [maxWarmWriteBufferCapacity] because the char writer only
     * serves `ghostInternalEncodeToString` — producing text output — and very large String
     * payloads are rare compared to binary encoding workloads.
     */
    val maxWarmCharWriteBufferCapacity: Int

    /**
     * When true, [com.ghost.serialization.Ghost.encodeToString] serializes through the UTF-8
     * flat writer and converts the bytes to a [String], instead of [GhostJsonStringWriter] +
     * `CharArray.concatToString`.
     *
     * Wasm: resolved at runtime — **true** on JavaScriptCore (Safari / iOS), where the char
     * writer collapses (~3× behind kotlinx.serialization encode); **false** on V8 (Chrome)
     * where the char writer stays ahead. JVM/Android/Native always false.
     */
    val encodeToStringViaUtf8Bytes: Boolean
}
