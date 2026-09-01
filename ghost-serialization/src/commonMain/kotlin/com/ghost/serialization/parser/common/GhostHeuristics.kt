@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics.maxWarmWriteBufferCapacity


/**
 * Platform-specific heuristics ('expect') to tune perf/memory tradeoffs per environment.
 *
 * [maxCollectionSize] and related caps differ per platform (Android tighter than
 * JVM/Native/Wasm), not a single cross-platform constant. Registry discovery
 * ([com.ghost.serialization.discoverRegistries]) is empty on iOS/Wasm and needs manual
 * [com.ghost.serialization.Ghost.addRegistry].
 */
@InternalGhostApi
expect object GhostHeuristics {
    /** Initial capacity for ArrayLists/HashMaps during deserialization; avoids resizing. */
    val initialCollectionCapacity: Int

    /** Max length of a string to be pooled; keeps large payloads off the heap. */
    val maxStringPoolLength: Int

    /**
     * Max items allowed in a List/Map during deserialization (DoS limit).
     * Platform defaults differ (e.g. Android 50k, Native/Wasm 500k, JVM 1M).
     */
    val maxCollectionSize: Int

    /** Max distance to scan for a discriminator before giving up. */
    val maxDiscriminatorPeekDistance: Int

    /**
     * Max retained capacity for `FlatByteArrayWriter` after `.reset`; capacity above
     * this is released instead of kept for reuse on the same thread.
     */
    val maxWarmWriteBufferCapacity: Int

    /**
     * Max retained capacity for `FlatCharArrayWriter` after `.reset`. Smaller than
     * [maxWarmWriteBufferCapacity] since the char writer only serves
     * `ghostInternalEncodeToString`, where huge String outputs are rare.
     */
    val maxWarmCharWriteBufferCapacity: Int

    /**
     * When true, [com.ghost.serialization.Ghost.encodeToString] goes through the UTF-8
     * flat writer + byte-to-String conversion instead of [GhostJsonStringWriter] +
     * `CharArray.concatToString`.
     *
     * Wasm-only, resolved at runtime: true on JavaScriptCore (Safari/iOS), where the char
     * writer collapses (~3x behind kotlinx.serialization); false on V8 (Chrome). Other
     * platforms are always false.
     */
    val encodeToStringViaUtf8Bytes: Boolean
}
