@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.prepareUtf8JsonSource
import com.ghost.serialization.parser.common.withPreparedUtf8Json
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.bytes.WriterSinkPair
import com.ghost.serialization.writer.strings.FlatCharArrayWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import okio.BufferedSource
import java.util.concurrent.ConcurrentHashMap


private val readerPool = ThreadLocal<GhostJsonReader>()
private val flatReaderPool = ThreadLocal<GhostJsonFlatReader>()
private val stringReaderPool = ThreadLocal<GhostJsonStringReader>()
private val sourceReaderPool = ThreadLocal<GhostJsonReader>()

@PublishedApi
internal val writerPool = ThreadLocal<WriterSinkPair>()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

/**
 * Acquires the per-thread [WriterSinkPair], resets it for a fresh encode,
 * and returns it. The pair survives across calls so the underlying
 * `FlatByteArrayWriter` grows once and stays warm.
 */
@PublishedApi
internal fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = writerPool.get()
        ?: WriterSinkPair()
            .also { writerPool.set(it) }

    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

@PublishedApi
internal class WriterStringPair {
    val charWriter = FlatCharArrayWriter()
    val writer = GhostJsonStringWriter(charWriter)
}

@PublishedApi
internal val stringWriterPool = ThreadLocal<WriterStringPair>()

@PublishedApi
internal fun acquireStringWriterPair(): WriterStringPair {
    val pair = stringWriterPool.get()
        ?: WriterStringPair().also { stringWriterPool.set(it) }
    pair.writer.reset()
    pair.charWriter.reset()
    return pair
}

@InternalGhostApi
actual inline fun ghostInternalEncodeToString(
    crossinline block: (GhostJsonStringWriter) -> Unit
): String {
    val pair = acquireStringWriterPair()
    block(pair.writer)
    val result = String(
        pair.charWriter.array,
        0,
        pair.charWriter.size
    )
    pair.charWriter.reset()
    return result
}

@InternalGhostApi
actual inline fun ghostInternalEncodeWithWriter(
    crossinline block: (GhostJsonWriter) -> Unit
): ByteArray {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    val result = pair.byteWriter.toByteArray()
    pair.byteWriter.reset()
    return result
}

@InternalGhostApi
actual inline fun ghostInternalEncodeAndDiscard(
    crossinline block: (GhostJsonWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    pair.byteWriter.reset()
}

@InternalGhostApi
actual inline fun ghostInternalEncodeAndDrainTo(
    sink: okio.BufferedSink,
    crossinline block: (GhostJsonWriter) -> Unit
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

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    return withPreparedUtf8Json(bytes, bytes.size) { data, offset, length ->
        val view = if (offset == 0) data else data.copyOfRange(offset, offset + length)
        val viewLimit = if (offset == 0) length else view.size
        val reader = readerPool.get()
            ?: GhostJsonReader(view)
                .also { readerPool.set(it) }

        reader.reset(view, viewLimit)
        block(reader)
    }
}

actual fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray,
    limit: Int,
    block: (GhostJsonFlatReader) -> T
): T {
    return withPreparedUtf8Json(bytes, limit) { data, offset, length ->
        val reader = flatReaderPool.get()
            ?: GhostJsonFlatReader(data)
                .also { flatReaderPool.set(it) }

        reader.resetSlice(data, offset, length)
        block(reader)
    }
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    // Separate pool from readerPool to prevent re-entrancy corruption if the
    // same thread nests a ByteArray read inside a streaming read.
    val reader = sourceReaderPool.get()
        ?: GhostJsonReader(source)
            .also { sourceReaderPool.set(it) }

    // reset(BufferedSource) wraps source in a StreamingGhostSource — Okio pulls
    // data in 8 KB segments on demand instead of loading the entire payload.
    reader.reset(prepareUtf8JsonSource(source))
    return block(reader)
}

actual fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T {
    val reader = stringReaderPool.get()
        ?: GhostJsonStringReader(json)
            .also { stringReaderPool.set(it) }

    reader.reset(json)
    return block(reader)
}
