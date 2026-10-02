@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.prepareUtf8JsonSource
import com.ghost.serialization.parser.common.json.withPreparedUtf8Json
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.bytes.WriterSinkPair
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import okio.BufferedSource
import java.util.concurrent.ConcurrentHashMap

private val flatReaderPool = ThreadLocal<GhostJsonFlatReader>()
private val stringReaderPool = ThreadLocal<GhostJsonStringReader>()
private val sourceReaderPool = ThreadLocal<GhostJsonReader>()

@PublishedApi
internal val writerPool = ThreadLocal<WriterSinkPair>()

/** Per-thread [WriterSinkPair], reset for a fresh encode
 *  the buffer survives across calls so it only grows once and stays warm. */
@PublishedApi
internal fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = writerPool.get() ?: WriterSinkPair()
            .also { writerPool.set(it) }

    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

@PublishedApi
internal val stringWriterPool = ThreadLocal<WriterStringPair>()

@PublishedApi
internal fun acquireStringWriterPair(): WriterStringPair {
    val pair = stringWriterPool.get() ?: WriterStringPair()
        .also { stringWriterPool.set(it) }

    pair.writer.reset()
    pair.charWriter.reset()
    return pair
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

actual fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray,
    limit: Int,
    block: (GhostJsonFlatReader) -> T
): T {
    return withPreparedUtf8Json(bytes = bytes, limit = limit) { data, offset, length ->
        val reader = flatReaderPool.get()
            ?: GhostJsonFlatReader(rawData = data)
                .also { flatReaderPool.set(it) }

        reader.resetSlice(buffer = data, offset = offset, length = length)
        block(reader)
    }
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val reader = sourceReaderPool.get()
        ?: GhostJsonReader(source)
            .also { sourceReaderPool.set(it) }

    reader.reset(prepareUtf8JsonSource(source = source))
    return block(reader)
}

actual fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T {
    val reader = stringReaderPool.get()
        ?: GhostJsonStringReader(rawData = json)
            .also { stringReaderPool.set(it) }

    reader.reset(json)
    return block(reader)
}
