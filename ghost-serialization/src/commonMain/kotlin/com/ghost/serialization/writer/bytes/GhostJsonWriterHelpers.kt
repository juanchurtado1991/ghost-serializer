package com.ghost.serialization.writer.bytes

import com.ghost.serialization.writer.common.GhostDoubleFormatter
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Shared JSON writer kernels used by [GhostJsonWriter]'s Okio-Buffer and [FlatByteArrayWriter]
 * backends — mirrors `GhostYamlWriterHelpers`'s pattern for the YAML pair. Sink flushes stay
 * inlined lambdas at call sites so both backends keep monomorphic writes, and each backend's
 * fused intrinsics (`write2Bytes`, `writeDotZero`, ...) are passed in rather than assumed, so
 * [FlatByteArrayWriter] keeps using its faster fused primitives via [GhostByteSink].
 */
internal object GhostJsonWriterHelpers {

    inline fun beginArrayCore(
        depth: Int,
        maxDepth: Int,
        appendSeparator: () -> Unit,
        writeByte: (Int) -> Unit,
        setDepth: (Int) -> Unit,
        throwDepthError: () -> Nothing,
    ) {
        if (depth >= maxDepth) throwDepthError()
        appendSeparator()
        writeByte(TOK.OPEN_ARR_INT)
        setDepth(depth + 1)
    }

    inline fun beginObjectCore(
        depth: Int,
        maxDepth: Int,
        appendSeparator: () -> Unit,
        writeByte: (Int) -> Unit,
        setDepth: (Int) -> Unit,
        throwDepthError: () -> Nothing,
    ) {
        if (depth >= maxDepth) throwDepthError()
        appendSeparator()
        writeByte(TOK.OPEN_OBJ_INT)
        setDepth(depth + 1)
    }

    inline fun endArrayCore(
        depth: Int,
        writeByte: (Int) -> Unit,
        setDepth: (Int) -> Unit,
    ) {
        writeByte(TOK.CLOSE_ARR_INT)
        setDepth(depth - 1)
    }

    inline fun endObjectCore(
        depth: Int,
        writeByte: (Int) -> Unit,
        setDepth: (Int) -> Unit,
    ) {
        writeByte(TOK.CLOSE_OBJ_INT)
        setDepth(depth - 1)
    }

    inline fun writeDoubleValueRawCore(
        number: Double,
        writeLongValueRawInternal: (Long) -> Unit,
        writeDotZero: () -> Unit,
        acquireScratch: () -> ByteArray,
        writeBytes: (buf: ByteArray, offset: Int, length: Int) -> Unit,
        writeUtf8: (String) -> Unit,
        throwNonFinite: () -> Nothing,
    ) {
        val isIntegralDouble = number in NUM.MIN_SAFE_INTEGER_DOUBLE..NUM.MAX_SAFE_INTEGER_DOUBLE &&
            number % NUM.WHOLE_NUMBER_CHECK == NUM.ZERO_DOUBLE &&
            !(number == 0.0 && number.toRawBits() < 0)
        if (isIntegralDouble) {
            writeLongValueRawInternal(number.toLong())
            writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeDoubleDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
        )
        if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
            if (!number.isFinite()) {
                throwNonFinite()
            }
            writeUtf8(number.toString())
        } else if (bytesWrittenLength > 0) {
            writeBytes(scratchBuf, 0, bytesWrittenLength)
        }
    }

    inline fun writeFloatValueRawCore(
        number: Float,
        writeLongValueRawInternal: (Long) -> Unit,
        writeDotZero: () -> Unit,
        acquireScratch: () -> ByteArray,
        writeBytes: (buf: ByteArray, offset: Int, length: Int) -> Unit,
        writeUtf8: (String) -> Unit,
        throwNonFinite: () -> Nothing,
    ) {
        val doubleVal = number.toDouble()
        val isIntegralFloat = doubleVal in NUM.MIN_SAFE_INTEGER_DOUBLE..NUM.MAX_SAFE_INTEGER_DOUBLE &&
            doubleVal % NUM.WHOLE_NUMBER_CHECK == NUM.ZERO_DOUBLE &&
            !(number == 0.0f && number.toRawBits() < 0)
        if (isIntegralFloat) {
            writeLongValueRawInternal(doubleVal.toLong())
            writeDotZero()
            return
        }

        val scratchBuf = acquireScratch()
        val bytesWrittenLength = GhostDoubleFormatter.writeFloatDirect(
            value = number,
            scratch = scratchBuf,
            offset = 0,
        )
        if (bytesWrittenLength == GhostDoubleFormatter.FALLBACK_REQUIRED) {
            if (!number.isFinite()) {
                throwNonFinite()
            }
            writeUtf8(number.toString())
        } else if (bytesWrittenLength > 0) {
            writeBytes(scratchBuf, 0, bytesWrittenLength)
        }
    }

    /**
     * Preserves the exact branch order of the original hand-tuned fast paths (single-digit
     * positive → single-digit negative → MIN_VALUE → delegate) — these are the hottest,
     * most frequently hit branches (IDs, counts, status codes), so branch order matters for
     * prediction, not just net behavior.
     */
    inline fun writeIntValueRawCore(
        value: Int,
        writeByte: (Int) -> Unit,
        write2Bytes: (Int, Int) -> Unit,
        writeMinIntBs: () -> Unit,
        writeLongValueRawInternal: (Long) -> Unit,
    ) {
        if (value in 0..9) {
            writeByte(TOK.ZERO_INT + value)
            return
        }
        if (value in -9..-1) {
            write2Bytes(TOK.MINUS_INT, TOK.ZERO_INT - value)
            return
        }
        if (value == Int.MIN_VALUE) {
            writeMinIntBs()
            return
        }
        writeLongValueRawInternal(value.toLong())
    }

    inline fun writeLongValueRawCore(
        value: Long,
        writeByte: (Int) -> Unit,
        write2Bytes: (Int, Int) -> Unit,
        writeMinIntBs: () -> Unit,
        writeMinLongBs: () -> Unit,
        writeLongValueRawInternal: (Long) -> Unit,
    ) {
        if (value in 0L..9L) {
            writeByte(TOK.ZERO_INT + value.toInt())
            return
        }
        if (value in -9L..-1L) {
            val intVal = value.toInt()
            write2Bytes(TOK.MINUS_INT, TOK.ZERO_INT - intVal)
            return
        }
        if (value == Int.MIN_VALUE.toLong()) {
            writeMinIntBs()
            return
        }
        if (value == Long.MIN_VALUE) {
            writeMinLongBs()
            return
        }
        writeLongValueRawInternal(value)
    }

    /**
     * Already-identical body between both writers — [scratch] is read once at the call site
     * ([GhostJsonWriter] holds its own nullable field per instance) and
     * [acquireScratch] lazily fills it in, exactly as each writer did inline before.
     */
    inline fun writeLongValueRawInternalCore(
        value: Long,
        scratch: ByteArray?,
        acquireScratch: () -> ByteArray,
        writeMinLongBs: () -> Unit,
        writeBytes: (buf: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        val scratchBuf = scratch ?: acquireScratch()
        var localValue = value
        val isNegative = localValue < 0
        if (isNegative) {
            if (localValue == Long.MIN_VALUE) {
                writeMinLongBs()
                return
            }
            localValue = -localValue
        }

        val scratchEnd = WR.LONG_SCRATCH_SIZE
        val pos = GhostWriterLongDigits.writeDigitsBytes(
            absoluteValue = localValue,
            negative = isNegative,
            scratch = scratchBuf,
            scratchEnd = scratchEnd,
        )
        writeBytes(scratchBuf, pos, scratchEnd - pos)
    }

    inline fun writeStringValueRawSlowCore(
        value: String,
        length: Int,
        breakIndex: Int,
        scratchBuf: ByteArray,
        writeQuoteByte: () -> Unit,
        writeUtf8Range: (text: String, begin: Int, end: Int) -> Unit,
        writeEscapedIntoScratch: (text: String, length: Int, scratchBuf: ByteArray) -> Unit,
        writeEscaped: (text: String, start: Int) -> Unit,
    ) {
        if (breakIndex == 0 && length + WR.STRING_QUOTE_PAIR_BYTES <= scratchBuf.size) {
            scratchBuf[0] = TOK.QUOTE_BYTE
            writeEscapedIntoScratch(value, length, scratchBuf)
            return
        }

        writeQuoteByte()
        if (breakIndex > 0) {
            writeUtf8Range(value, 0, breakIndex)
        }
        writeEscaped(value, breakIndex)
        writeQuoteByte()
    }

    inline fun writeULongValueRawCore(
        value: ULong,
        writeLongValueRaw: (Long) -> Unit,
        writeStringValueRaw: (String) -> Unit,
    ) {
        if (value <= Long.MAX_VALUE.toULong()) {
            writeLongValueRaw(value.toLong())
        } else {
            writeStringValueRaw(value.toString())
        }
    }
}
