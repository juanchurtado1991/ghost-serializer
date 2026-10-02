@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.bytes

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages.ERR_DEPTH_EXCEEDED
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages.ERR_NON_FINITE
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits.MAX_DEPTH
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.COMMA_INT
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.QUOTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.COLON_QUOTE_BS
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.MIN_INT_BS
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.MIN_LONG_BS
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.PLAIN_ASCII_FAST_PATH_LIMIT
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.WRITER_SCRATCH_SIZE
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.writer.common.GhostJsonEscapeHelpers
import okio.BufferedSink
import okio.ByteString

/**
 * A highly optimized, low-allocation JSON writer for Kotlin Multiplatform.
 *
 * Backed by either a streaming Okio [BufferedSink] or, for in-memory encodes,
 * a [FlatByteArrayWriter] — both funnel through [GhostByteSink], so the
 * body of this class is written once and shared by both channels.
 *
 * Every byte goes out through the `emit*` helpers, which call the concrete [FlatByteArrayWriter]
 * directly when that is the backend and fall back to the [GhostByteSink] interface otherwise: one
 * writer serving both channels made each sink call site bimorphic, and keeping the in-memory path
 * monomorphic measured +3% Encode (Bytes) on benchmarkTwitterFast (4 alternated A/B pairs). Those
 * helpers are `inline` on purpose (hence `NOTHING_TO_INLINE` suppressed): the flat-sink check must
 * be fused into each call site to stay monomorphic.
 */
@Suppress("NOTHING_TO_INLINE")
class GhostJsonWriter private constructor(
    @PublishedApi internal val sink: GhostByteSink
) {

    constructor(sink: BufferedSink) : this(BufferGhostByteSink(sink = sink))

    @InternalGhostApi
    constructor(flatBuffer: FlatByteArrayWriter) : this(flatBuffer as GhostByteSink)


    // ── Sink dispatch ─────────────────────────────────────────────────────────

    @PublishedApi
    internal val flatSink: FlatByteArrayWriter? = sink as? FlatByteArrayWriter

    @PublishedApi
    internal inline fun emit(bytes: ByteArray) {
        val flat = flatSink
        if (flat != null) flat.write(bytes = bytes) else sink.write(bytes = bytes)
    }

    @PublishedApi
    internal inline fun emit(bytes: ByteArray, offset: Int, length: Int) {
        val flat = flatSink
        if (flat != null) flat.write(bytes = bytes, offset = offset, length = length)
        else sink.write(bytes = bytes, offset = offset, length = length)
    }

    @PublishedApi
    internal inline fun emit(byteString: ByteString) {
        val flat = flatSink
        if (flat != null) flat.write(byteString = byteString) else sink.write(byteString = byteString)
    }

    @PublishedApi
    internal inline fun emit2Bytes(firstByte: Int, secondByte: Int) {
        val flat = flatSink
        if (flat != null) flat.write2Bytes(firstByte = firstByte, secondByte = secondByte)
        else sink.write2Bytes(firstByte = firstByte, secondByte = secondByte)
    }

    @PublishedApi
    internal inline fun emitByte(byteAsInt: Int) {
        val flat = flatSink
        if (flat != null) flat.writeByte(byteAsInt = byteAsInt) else sink.writeByte(byteAsInt = byteAsInt)
    }

    @PublishedApi
    internal inline fun emitDotZero() {
        val flat = flatSink
        if (flat != null) flat.writeDotZero() else sink.writeDotZero()
    }

    @PublishedApi
    internal inline fun emitFalse() {
        val flat = flatSink
        if (flat != null) flat.writeFalse() else sink.writeFalse()
    }

    @PublishedApi
    internal inline fun emitNull() {
        val flat = flatSink
        if (flat != null) flat.writeNull() else sink.writeNull()
    }

    @PublishedApi
    internal inline fun emitQuotedAscii(text: String, length: Int) {
        val flat = flatSink
        if (flat != null) flat.writeQuotedAscii(text = text, length = length)
        else sink.writeQuotedAscii(text = text, length = length)
    }

    @PublishedApi
    internal inline fun emitQuotedBmpCodeUnit(codePoint: Int) {
        val flat = flatSink
        if (flat != null) flat.writeQuotedBmpCodeUnit(codePoint = codePoint)
        else sink.writeQuotedBmpCodeUnit(codePoint = codePoint)
    }

    @PublishedApi
    internal inline fun emitTrue() {
        val flat = flatSink
        if (flat != null) flat.writeTrue() else sink.writeTrue()
    }

    @PublishedApi
    internal inline fun emitUtf8(text: String) {
        val flat = flatSink
        if (flat != null) flat.writeUtf8(text = text) else sink.writeUtf8(text = text)
    }

    @PublishedApi
    internal inline fun emitUtf8(text: String, beginIndex: Int, endIndex: Int) {
        val flat = flatSink
        if (flat != null) flat.writeUtf8(text = text, beginIndex = beginIndex, endIndex = endIndex)
        else sink.writeUtf8(text = text, beginIndex = beginIndex, endIndex = endIndex)
    }

    internal var needsComma = false

    private var depth = 0

    internal var scratch: ByteArray? = null

    internal fun acquireScratch(): ByteArray {
        val currentScratch = scratch
        if (currentScratch != null) {
            return currentScratch
        }
        val newScratch = acquireScratchBuffer(minSize = WRITER_SCRATCH_SIZE)
        scratch = newScratch
        return newScratch
    }

    /**
     * Ensures all buffered bytes are pushed to the underlying sink.
     * No-op for the in-memory (flat) channel.
     */
    @InternalGhostApi
    fun flush() {
        sink.flush()
    }

    /**
     * Releases the internal scratch buffer back to the pool.
     * Must be called at the end of the root serialization process.
     */
    @InternalGhostApi
    fun release() {
        val currentScratch = scratch
        if (currentScratch != null) {
            releaseScratchBuffer(buffer = currentScratch)
            scratch = null
        }
        needsComma = false
        depth = 0
    }

    /**
     * Resets writer state for reuse from a pool.
     * Does NOT release the scratch buffer — it is kept warm for the next call.
     */
    @InternalGhostApi
    fun reset() {
        needsComma = false
        depth = 0
    }

    // ── Structural ────────────────────────────────────────────────────────────

    fun beginArray(): GhostJsonWriter {
        GhostJsonWriterHelpers.beginArrayCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { emitByte(it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    fun beginObject(): GhostJsonWriter {
        GhostJsonWriterHelpers.beginObjectCore(
            depth = depth,
            maxDepth = MAX_DEPTH,
            appendSeparator = { appendSeparator() },
            writeByte = { emitByte(it) },
            setDepth = { depth = it },
            throwDepthError = { throwDepthError() },
        )
        needsComma = false
        return this
    }

    fun endArray(): GhostJsonWriter {
        GhostJsonWriterHelpers.endArrayCore(
            depth = depth,
            writeByte = { emitByte(it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    fun endObject(): GhostJsonWriter {
        GhostJsonWriterHelpers.endObjectCore(
            depth = depth,
            writeByte = { emitByte(it) },
            setDepth = { depth = it },
        )
        needsComma = true
        return this
    }

    /** Writes an escaped field name followed by the colon separator. */
    fun name(key: String): GhostJsonWriter {
        appendSeparator()
        emitByte(QUOTE_INT)
        writeEscaped(key)
        emit(COLON_QUOTE_BS)
        needsComma = false
        return this
    }

    /** Writes a pre-encoded field name, avoiding runtime escaping — the fastest way to write names. */
    fun name(key: ByteString): GhostJsonWriter {
        appendSeparator()
        emit(key)
        needsComma = false
        return this
    }

    /** Fused name + value with automatic comma handling; used by KSP-generated serializers. */
    @InternalGhostApi
    fun writeField(header: ByteString, value: Int): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeIntValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Long): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeLongValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: ULong): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeULongValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: String): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeStringValueRaw(value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Boolean): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeBooleanValueRaw(value = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Double): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeDoubleValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeField(header: ByteString, value: Float): GhostJsonWriter {
        appendSeparator()
        emit(header)
        writeFloatValueRaw(number = value)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeNameRaw(header: ByteString): GhostJsonWriter {
        return name(key = header)
    }

    // ── value() public API ────────────────────────────────────────────────────

    fun nullValue(): GhostJsonWriter {
        appendSeparator()
        emitNull()
        needsComma = true
        return this
    }

    /** Writes raw JSON bytes directly, without quoting or escaping — for a pre-serialized fragment. */
    fun rawValue(bytes: ByteArray): GhostJsonWriter {
        appendSeparator()
        emit(bytes)
        needsComma = true
        return this
    }

    fun rawValue(bytes: ByteArray, offset: Int, length: Int): GhostJsonWriter {
        appendSeparator()
        emit(bytes, offset, length)
        needsComma = true
        return this
    }

    /** Writes [raw] without copying slice data when possible. */
    fun rawValue(raw: RawJson): GhostJsonWriter =
        rawValue(bytes = raw.storage, offset = raw.storageOffset, length = raw.storageLength)

    fun value(text: String): GhostJsonWriter {
        appendSeparator()
        writeStringValueRaw(text)
        needsComma = true
        return this
    }

    fun value(number: Int): GhostJsonWriter {
        appendSeparator()
        writeIntValueRaw(value = number)
        needsComma = true
        return this
    }

    fun value(number: Long): GhostJsonWriter {
        appendSeparator()
        writeLongValueRaw(number)
        needsComma = true
        return this
    }

    fun value(number: ULong): GhostJsonWriter {
        appendSeparator()
        writeULongValueRaw(value = number)
        needsComma = true
        return this
    }

    fun value(number: Double): GhostJsonWriter {
        appendSeparator()
        writeDoubleValueRaw(number = number)
        needsComma = true
        return this
    }

    fun value(number: Float): GhostJsonWriter {
        appendSeparator()
        writeFloatValueRaw(number = number)
        needsComma = true
        return this
    }

    fun value(value: Boolean): GhostJsonWriter {
        appendSeparator()
        if (value) {
            emitTrue()
        } else {
            emitFalse()
        }
        needsComma = true
        return this
    }

    /** Writes a single [Char] as a JSON string without allocating an intermediate [String]. */
    fun value(char: Char): GhostJsonWriter {
        appendSeparator()
        emitQuotedBmpCodeUnit(codePoint = char.code)
        needsComma = true
        return this
    }

    @InternalGhostApi
    fun writeBooleanValueRaw(value: Boolean) {
        if (value) {
            emitTrue()
        } else {
            emitFalse()
        }
    }

    @InternalGhostApi
    fun writeIntValueRaw(value: Int) {
        GhostJsonWriterHelpers.writeIntValueRawCore(
            value = value,
            writeByte = { emitByte(it) },
            write2Bytes = { a, b -> emit2Bytes(a, b) },
            writeMinIntBs = { emit(MIN_INT_BS) },
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
        )
    }

    @InternalGhostApi
    fun writeLongValueRaw(value: Long) {
        GhostJsonWriterHelpers.writeLongValueRawCore(
            value = value,
            writeByte = { emitByte(it) },
            write2Bytes = { a, b -> emit2Bytes(a, b) },
            writeMinIntBs = { emit(MIN_INT_BS) },
            writeMinLongBs = { emit(MIN_LONG_BS) },
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
        )
    }

    @InternalGhostApi
    fun writeULongValueRaw(value: ULong) {
        GhostJsonWriterHelpers.writeULongValueRawCore(
            value = value,
            writeLongValueRaw = { writeLongValueRaw(it) },
            writeStringValueRaw = { writeStringValueRaw(it) },
        )
    }

    private fun writeLongValueRawInternal(value: Long) {
        GhostJsonWriterHelpers.writeLongValueRawInternalCore(
            value = value,
            scratch = scratch,
            acquireScratch = { acquireScratch() },
            writeMinLongBs = { emit(MIN_LONG_BS) },
            writeBytes = { buf, offset, length -> emit(buf, offset, length) },
        )
    }

    internal inline fun appendSeparator() {
        if (needsComma) {
            emitByte(COMMA_INT)
            needsComma = false
        }
    }

    @InternalGhostApi
    fun writeDoubleValueRaw(number: Double) {
        GhostJsonWriterHelpers.writeDoubleValueRawCore(
            number = number,
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
            writeDotZero = { emitDotZero() },
            acquireScratch = { acquireScratch() },
            writeBytes = { buf, offset, length -> emit(buf, offset, length) },
            writeUtf8 = { emitUtf8(it) },
            throwNonFinite = { throw GhostJsonException(ERR_NON_FINITE, 0, 0) },
        )
    }

    @InternalGhostApi
    fun writeFloatValueRaw(number: Float) {
        GhostJsonWriterHelpers.writeFloatValueRawCore(
            number = number,
            writeLongValueRawInternal = { writeLongValueRawInternal(it) },
            writeDotZero = { emitDotZero() },
            acquireScratch = { acquireScratch() },
            writeBytes = { buf, offset, length -> emit(buf, offset, length) },
            writeUtf8 = { emitUtf8(it) },
            throwNonFinite = { throw GhostJsonException(ERR_NON_FINITE, 0, 0) },
        )
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            emit2Bytes(QUOTE_INT, QUOTE_INT)
            return
        }

        // Short strings: scan for the first char that needs escaping / UTF-8. All-plain →
        // writeQuotedAscii. Mixed → keep the ASCII prefix (breakIndex) so the slow path does
        // not re-scan it (same shape as GhostJsonStringWriter).
        var breakIndex = 0
        if (length <= PLAIN_ASCII_FAST_PATH_LIMIT) {
            while (breakIndex < length) {
                if (!GhostJsonEscapeHelpers.isPlainAsciiSafe(code = value[breakIndex].code)) {
                    break
                }
                breakIndex++
            }
            if (breakIndex == length) {
                emitQuotedAscii(text = value, length = length)
                return
            }
        }

        writeStringValueRawSlow(value = value, length = length, breakIndex = breakIndex)
    }

    private fun throwDepthError(): Nothing =
        throw GhostJsonException(
            "$ERR_DEPTH_EXCEEDED (${MAX_DEPTH})",
            0,
            0
        )

    private fun writeEscaped(text: String, start: Int = 0) {
        GhostJsonEscapeHelpers.writeEscapedBytes(
            text = text,
            start = start,
            scratchBuf = acquireScratch(),
            writeBytes = { buf, offset, len -> emit(buf, offset, len) },
            writeReplacement = { replacement -> emit(replacement) },
            writeUtf8Range = { s, begin, end -> emitUtf8(s, begin, end) },
        )
    }

    private fun writeEscapedIntoScratch(text: String, length: Int, scratchBuf: ByteArray) {
        GhostJsonEscapeHelpers.writeEscapedIntoByteScratch(
            text = text,
            length = length,
            scratchBuf = scratchBuf,
            writeBytes = { buf, offset, len -> emit(buf, offset, len) },
            writeReplacement = { replacement -> emit(replacement) },
            writeUtf8Range = { s, begin, end -> emitUtf8(s, begin, end) },
            writeQuoteByte = { emitByte(QUOTE_INT) },
        )
    }

    private fun writeStringValueRawSlow(value: String, length: Int, breakIndex: Int) {
        GhostJsonWriterHelpers.writeStringValueRawSlowCore(
            value = value,
            length = length,
            breakIndex = breakIndex,
            scratchBuf = acquireScratch(),
            writeQuoteByte = { emitByte(QUOTE_INT) },
            writeUtf8Range = { text, begin, end -> emitUtf8(text, begin, end) },
            writeEscapedIntoScratch = { text, len, buf -> writeEscapedIntoScratch(text, len, buf) },
            writeEscaped = { text, start -> writeEscaped(text, start) },
        )
    }
}
