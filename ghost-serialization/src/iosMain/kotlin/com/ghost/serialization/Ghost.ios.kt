@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.WriterSinkPair
import okio.BufferedSource
import platform.objc.objc_sync_enter
import platform.objc.objc_sync_exit
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var cachedReader: GhostJsonReader? = null

@ThreadLocal
private var cachedWriterPair: WriterSinkPair? = null

actual fun discoverRegistries(): Iterable<GhostRegistry> = emptyList()

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = mutableMapOf()

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

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    source.request(Long.MAX_VALUE)
    val bytes = source.buffer.readByteArray()

    val reader = cachedReader
        ?: GhostJsonReader(bytes)
            .also { cachedReader = it }

    reader.reset(bytes)
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
    block: (GhostJsonFlatWriter) -> Unit
): String {
    val pair = acquireFlatWriterPair()
    block(pair.writer)

    val result = pair
        .byteWriter
        .array
        .decodeToString(
            0,
            pair.byteWriter.size
        )

    pair.byteWriter.reset()
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
