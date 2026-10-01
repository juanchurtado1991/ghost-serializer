@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.prepareUtf8JsonSource
import com.ghost.serialization.parser.common.json.withPreparedUtf8Json
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.bytes.WriterSinkPair
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import okio.BufferedSource
import kotlin.native.concurrent.ThreadLocal

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

actual fun discoverRegistries(): Iterable<GhostRegistry> = emptyList()

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
        source = pair.byteWriter.array,
        offset = 0,
        byteCount = pair.byteWriter.size
    )
    pair.byteWriter.reset()
}

@InternalGhostApi
actual inline fun ghostInternalEncodeToString(
    crossinline block: (GhostJsonStringWriter) -> Unit
): String {
    val pair = acquireStringWriterPair()
    block(pair.writer)
    val result = pair.charWriter.array.concatToString(
        startIndex = 0,
        endIndex = pair.charWriter.size
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
        val reader = cachedFlatReader
            ?: GhostJsonFlatReader(rawData = data)
                .also { cachedFlatReader = it }

        reader.resetSlice(buffer = data, offset = offset, length = length)
        block(reader)
    }
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    val reader = cachedSourceReader
        ?: GhostJsonReader(okioSource = source)
            .also { cachedSourceReader = it }

    reader.reset(okioSource = prepareUtf8JsonSource(source = source))
    return block(reader)
}

actual fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T {
    val reader = cachedStringReader
        ?: GhostJsonStringReader(rawData = json)
            .also { cachedStringReader = it }

    reader.reset(newData = json)
    return block(reader)
}
