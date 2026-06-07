@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.WriterSinkPair
import com.ghost.serialization.writer.WriterStringPair
import okio.BufferedSource
import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var cachedReader: GhostJsonReader? = null

@ThreadLocal
private var cachedFlatReader: GhostJsonFlatReader? = null

@ThreadLocal
private var cachedStringReader: GhostJsonStringReader? = null

@ThreadLocal
private var cachedSourceReader: GhostJsonReader? = null

@ThreadLocal
private var cachedWriterPair: WriterSinkPair? = null

@ThreadLocal
private var cachedStringWriterPair: WriterStringPair? = null

actual fun discoverRegistries(): Iterable<GhostRegistry> = emptyList()

/**
 * Thread-safe map for Kotlin/Native (iOS).
 *
 * All mutations and reads are guarded by [objc_sync_enter]/[objc_sync_exit] — the same
 * Objective-C @synchronized primitive used by [runSynchronized]. This guarantees
 * correct visibility under K/N's new memory model where objects are shareable across threads.
 *
 * [entries], [keys] and [values] return **snapshots** (copies) so that callers iterating
 * outside the lock cannot observe concurrent structural modifications.
 */
private class IosConcurrentMap<K, V> : MutableMap<K, V> {
    private val delegate = mutableMapOf<K, V>()
    private val lock = Any()

    private inline fun <T> withLock(block: () -> T): T {
        objc_sync_enter(lock)
        return try { block() } finally { objc_sync_exit(lock) }
    }

    override val size: Int get() = withLock { delegate.size }
    override fun isEmpty(): Boolean = withLock { delegate.isEmpty() }
    override fun containsKey(key: K): Boolean = withLock { delegate.containsKey(key) }
    override fun containsValue(value: V): Boolean = withLock { delegate.containsValue(value) }
    override fun get(key: K): V? = withLock { delegate[key] }
    override fun put(key: K, value: V): V? = withLock { delegate.put(key, value) }
    override fun remove(key: K): V? = withLock { delegate.remove(key) }
    override fun putAll(from: Map<out K, V>) = withLock { delegate.putAll(from) }
    override fun clear() = withLock { delegate.clear() }

    // Snapshots — callers iterate a frozen copy, never the live internal set.
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = withLock { delegate.entries.toMutableSet() }
    override val keys: MutableSet<K>
        get() = withLock { delegate.keys.toMutableSet() }
    override val values: MutableCollection<V>
        get() = withLock { delegate.values.toMutableList() }
}

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = IosConcurrentMap()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = try {
    objc_sync_enter(lock)
    block()
} finally {
    objc_sync_exit(lock)
}

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(bytes)
            .also { cachedReader = it }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray,
    limit: Int,
    block: (GhostJsonFlatReader) -> T
): T {
    val reader = cachedFlatReader
        ?: GhostJsonFlatReader(bytes)
            .also { cachedFlatReader = it }

    reader.reset(bytes, limit)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    // Separate pool from cachedReader to prevent re-entrancy corruption if the
    // same thread nests a ByteArray read inside a streaming read.
    val reader = cachedSourceReader
        ?: GhostJsonReader(source)
            .also { cachedSourceReader = it }

    // reset(BufferedSource) wraps source in a StreamingGhostSource — Okio pulls
    // data in 8 KB segments on demand instead of loading the entire payload.
    reader.reset(source)
    return block(reader)
}

actual fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T {
    val reader = cachedStringReader
        ?: GhostJsonStringReader(json)
            .also { cachedStringReader = it }

    reader.reset(json)
    return block(reader)
}

private fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = cachedWriterPair
        ?: WriterSinkPair()
            .also { cachedWriterPair = it }

    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

actual fun ghostInternalEncodeToString(
    block: (com.ghost.serialization.writer.GhostJsonStringWriter) -> Unit
): String {
    val pair = cachedStringWriterPair
        ?: WriterStringPair().also { cachedStringWriterPair = it }
    block(pair.writer)
    val result = pair.charWriter.array.concatToString(0, pair.charWriter.size)
    pair.charWriter.reset()
    return result
}

actual fun ghostInternalEncodeWithWriter(
    block: (GhostJsonFlatWriter) -> Unit
): ByteArray {
    val pair = acquireFlatWriterPair()
    block(pair.writer)

    val result = pair.byteWriter.toByteArray()
    pair.byteWriter.reset()

    return result
}

actual fun ghostInternalEncodeAndDiscard(
    block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    pair.byteWriter.reset()
}

actual fun ghostInternalEncodeAndDrainTo(
    sink: okio.BufferedSink,
    block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    sink.write(
        pair.byteWriter.array,
        0,
        pair.byteWriter.size
    )
    pair.byteWriter.reset()
}
