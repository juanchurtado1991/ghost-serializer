@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.WriterSinkPair
import okio.BufferedSource

import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.FlatCharArrayWriter

@PublishedApi
internal var cachedWriterPair: WriterSinkPair? = null
private var cachedReader: GhostJsonReader? = null
private var cachedFlatReader: GhostJsonFlatReader? = null
private var cachedStringReader: GhostJsonStringReader? = null
private var cachedSourceReader: GhostJsonReader? = null
@PublishedApi
internal var cachedStringWriterPair: WriterStringPair? = null

@PublishedApi
internal class WriterStringPair {
    val charWriter = FlatCharArrayWriter()
    val writer = GhostJsonStringWriter(charWriter)
}
actual fun discoverRegistries(): Iterable<GhostRegistry> = emptyList()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = block()

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = mutableMapOf()

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray, block: (GhostJsonReader) -> T
): T {
    val reader = cachedReader
        ?: GhostJsonReader(bytes)
            .also { cachedReader = it }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray, limit: Int, block: (GhostJsonFlatReader) -> T
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
    // reset(BufferedSource) wraps source in a StreamingGhostSource — Okio pulls
    // data in 8 KB segments on demand instead of loading the entire payload.
    val reader = cachedSourceReader
        ?: GhostJsonReader(source)
            .also { cachedSourceReader = it }

    reader.reset(source)
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

@PublishedApi
internal fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = cachedWriterPair
        ?: WriterSinkPair()
            .also { cachedWriterPair = it }

    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

@InternalGhostApi
actual inline fun ghostInternalEncodeToString(
    crossinline block: (GhostJsonStringWriter) -> Unit
): String {
    val pair = cachedStringWriterPair
        ?: WriterStringPair().also { cachedStringWriterPair = it }
    block(pair.writer)
    val result = pair.charWriter.array.concatToString(0, pair.charWriter.size)
    pair.charWriter.reset()
    return result
}

@InternalGhostApi
actual inline fun ghostInternalEncodeWithWriter(
    crossinline block: (GhostJsonFlatWriter) -> Unit
): ByteArray {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    val result = pair.byteWriter.toByteArray()
    pair.byteWriter.reset()
    return result
}

@InternalGhostApi
actual inline fun ghostInternalEncodeAndDiscard(
    crossinline block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    pair.byteWriter.reset()
}

@InternalGhostApi
actual inline fun ghostInternalEncodeAndDrainTo(
    sink: okio.BufferedSink,
    crossinline block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    sink.write(pair.byteWriter.array, 0, pair.byteWriter.size)
    pair.byteWriter.reset()
}
