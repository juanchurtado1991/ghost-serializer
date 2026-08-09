@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.prepareUtf8JsonSource
import com.ghost.serialization.parser.common.withPreparedUtf8Json
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.WriterSinkPair
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
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
@PublishedApi
internal var cachedWriterPair: WriterSinkPair? = null

@ThreadLocal
@PublishedApi
internal var cachedStringWriterPair: WriterStringPair? = null
actual fun discoverRegistries(): Iterable<GhostRegistry> = emptyList()


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
    return withPreparedUtf8Json(bytes, bytes.size) { data, offset, length ->
        val view = if (offset == 0) data else data.copyOfRange(offset, offset + length)
        val viewLimit = if (offset == 0) length else view.size
        val reader = cachedReader
            ?: GhostJsonReader(view)
                .also { cachedReader = it }

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
        val reader = cachedFlatReader
            ?: GhostJsonFlatReader(data)
                .also { cachedFlatReader = it }

        reader.resetSlice(data, offset, length)
        block(reader)
    }
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
    reader.reset(prepareUtf8JsonSource(source))
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

@PublishedApi
internal fun acquireStringWriterPair(): WriterStringPair {
    val pair = cachedStringWriterPair
        ?: WriterStringPair().also { cachedStringWriterPair = it }

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
    sink.write(
        pair.byteWriter.array,
        0,
        pair.byteWriter.size
    )
    pair.byteWriter.reset()
}
