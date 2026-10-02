package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.writer.common.GhostWriterLongDigits
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.ByteString
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK
import com.ghost.serialization.yaml.GhostYamlWriterConstants as WR

/**
 * Shared YAML writer kernels used by [GhostYamlWriter], whether backed by an okio
 * `BufferedSink` or a `FlatByteArrayWriter`. Sink flushes stay at call sites via inlined lambdas.
 * [keyNeedsQuoting] used to live only on the (now-merged) flat writer, which left the streaming
 * path writing every mapping key bare/unescaped — found by fuzzing: `GhostYamlWriter.name("a: b")`
 * produced YAML `GhostYamlFlatReader` itself couldn't parse back.
 */
@OptIn(InternalGhostApi::class)
internal object GhostYamlWriterHelpers {

    /** Packed [prepareValue] result: bit0 justWroteDash, bit1 pendingSpace, bit2 incrementItemCount. */
    const val PREPARE_JUST_WROTE_DASH = 1
    const val PREPARE_PENDING_SPACE = 2
    const val PREPARE_INCREMENT_ITEM = 4

    fun extractKey(header: ByteString): String {
        val size = header.size
        val isQuotedHeaderWithColon = size >= WR.HEADER_MIN_SIZE &&
            header[WR.HEADER_QUOTE_START_OFFSET] == TOK.DOUBLE_QUOTE_BYTE &&
            header[size - WR.HEADER_QUOTE_END_OFFSET_SUB] == TOK.DOUBLE_QUOTE_BYTE &&
            header[size - WR.HEADER_COLON_OFFSET_SUB] == TOK.COLON_BYTE
        if (isQuotedHeaderWithColon) {
            return header.substring(WR.SUBSTRING_START_OFFSET, size - WR.HEADER_QUOTE_END_OFFSET_SUB)
                .utf8()
        }
        return header.utf8()
    }

    /**
     * True if [key] can't safely be written as bare plain-scalar text, i.e. it would round-trip
     * wrong when re-read: a leading '&'/'!'/'*' looks like an anchor/tag/alias, '"'/'\'' looks
     * like a quoted key, a bare '?' looks like an explicit-key indicator, and '['/'{' can send a
     * stringified complex key (Ghost collapses a non-scalar key via `toString()`, e.g. `"[a, b]"`)
     * through the flow-collection dispatch instead of being read as opaque text. An embedded
     * ": " gets misread as the key/value separator; an embedded newline truncates the key.
     * A leading/trailing space or tab is silently trimmed by the reader — found by fuzzing
     * (`GhostYamlWriterFuzzTest`): `" ?xup"` wrote bare and re-read as `"?xup"`. A leading '%' is
     * read as a `%YAML`/`%TAG` directive instead of a key — also found by fuzzing
     * (`GhostYamlStreamingWriterFuzzTest`). An empty key is fine bare (round-trips as `""`).
     */
    fun keyNeedsQuoting(key: String): Boolean {
        val length = key.length
        if (length == 0) return false
        val first = key[0].code
        val startsWithIndicator = first == TOK.AMPERSAND_BYTE.toInt() ||
            first == TOK.ASTERISK_BYTE.toInt() ||
            first == TOK.EXCLAMATION_BYTE.toInt() ||
            first == TOK.DOUBLE_QUOTE_INT ||
            first == TOK.SINGLE_QUOTE_BYTE.toInt() ||
            first == TOK.LEFT_BRACKET_BYTE.toInt() ||
            first == TOK.LEFT_BRACE_BYTE.toInt() ||
            first == TOK.SPACE_INT ||
            first == TOK.CHAR_TAB_INT ||
            first == TOK.PERCENT_BYTE.toInt()
        if (startsWithIndicator) {
            return true
        }
        val last = key[length - 1].code
        if (last == TOK.SPACE_INT || last == TOK.CHAR_TAB_INT) {
            return true
        }
        val isExplicitKeyIndicator = first == TOK.QUESTION_BYTE.toInt() &&
            (length == 1 || key[1].code == TOK.SPACE_INT || key[1].code == TOK.CHAR_TAB_INT)
        if (isExplicitKeyIndicator) {
            return true
        }
        var index = 0
        while (index < length) {
            val code = key[index].code
            if (code == TOK.NEWLINE_INT || code == TOK.CHAR_CR_INT) return true
            val isColonSeparator = code == TOK.COLON_INT &&
                (index + 1 == length || key[index + 1].code == TOK.SPACE_INT || key[index + 1].code == TOK.CHAR_TAB_INT)
            if (isColonSeparator) {
                return true
            }
            index++
        }
        return false
    }

    fun newScratch(): ByteArray = acquireScratchBuffer(minSize = SC.SCRATCH_BUFFER_SIZE)

    /**
     * Shared name() layout: validates depth, clears justWroteDash, writes newline+indent when needed.
     * Key bytes and Flat-only quoting stay at the call site.
     *
     * @return [depth] for the caller to index itemCounts after writing the key.
     */
    inline fun prepareNameLayout(
        depth: Int,
        itemCountAtDepth: Int,
        justWroteDash: Boolean,
        writeByte: (Int) -> Unit,
    ): Int {
        if (depth <= 0) {
            throw GhostYamlException(baseMessage = EM.ERR_NAME_OUTSIDE_OBJECT)
        }
        if (!justWroteDash) {
            if (itemCountAtDepth > 0 || depth > 1) {
                writeByte(TOK.NEWLINE_INT)
                writeIndentation(level = depth - 1, writeByte = writeByte)
            }
        }
        return depth
    }

    /**
     * Emits array-item dashes / pending key→value spaces.
     *
     * @return packed flags: [PREPARE_JUST_WROTE_DASH], [PREPARE_PENDING_SPACE],
     *   [PREPARE_INCREMENT_ITEM] (caller applies to writer fields / itemCounts).
     */
    inline fun prepareValue(
        isStructural: Boolean,
        depth: Int,
        contextAtDepth: Int,
        justWroteDash: Boolean,
        pendingSpace: Boolean,
        writeByte: (Int) -> Unit,
    ): Int {
        var dash = justWroteDash
        var space = pendingSpace
        var increment = false
        if (depth > 0 && contextAtDepth == WR.TYPE_ARRAY) {
            if (justWroteDash) {
                writeByte(TOK.DASH_INT)
                writeByte(TOK.SPACE_INT)
            } else {
                writeByte(TOK.NEWLINE_INT)
                writeIndentation(level = depth - 1, writeByte = writeByte)
                writeByte(TOK.DASH_INT)
                writeByte(TOK.SPACE_INT)
            }
            increment = true
            dash = isStructural
        } else {
            if (isStructural) {
                space = false
            } else if (space) {
                writeByte(TOK.SPACE_INT)
                space = false
            }
        }
        var flags = 0
        if (dash) flags = flags or PREPARE_JUST_WROTE_DASH
        if (space) flags = flags or PREPARE_PENDING_SPACE
        if (increment) flags = flags or PREPARE_INCREMENT_ITEM
        return flags
    }

    fun releaseScratch(current: ByteArray?) {
        if (current != null) {
            releaseScratchBuffer(buffer = current)
        }
    }

    inline fun writeEmptyPlaceholderIfNeeded(
        depth: Int,
        itemCountAtDepth: Int,
        parentContext: Int,
        openInt: Int,
        closeInt: Int,
        writeByte: (Int) -> Unit,
        writeOpenClose: (Int, Int) -> Unit,
    ) {
        if (itemCountAtDepth != 0) return
        val parentDepth = depth - 1
        if (parentDepth > 0 && parentContext == WR.TYPE_OBJECT) {
            writeByte(TOK.SPACE_INT)
        }
        writeOpenClose(openInt, closeInt)
    }

    inline fun writeEscaped(
        text: String,
        writeByte: (Int) -> Unit,
        writeUtf8Range: (text: String, beginIndex: Int, endIndex: Int) -> Unit,
    ) {
        val length = text.length
        var index = 0

        while (index < length) {
            when (val charCode = text[index].code) {
                TOK.DOUBLE_QUOTE_INT -> {
                    writeByte(TOK.BACKSLASH_INT)
                    writeByte(TOK.DOUBLE_QUOTE_INT)
                }
                TOK.BACKSLASH_INT -> {
                    writeByte(TOK.BACKSLASH_INT)
                    writeByte(TOK.BACKSLASH_INT)
                }
                else -> {
                    when (charCode) {
                        TOK.NEWLINE_INT -> {
                            writeByte(TOK.BACKSLASH_INT)
                            writeByte(TOK.CHAR_N_INT)
                        }

                        TOK.CHAR_CR_INT -> {
                            writeByte(TOK.BACKSLASH_INT)
                            writeByte(TOK.CHAR_R_INT)
                        }

                        TOK.CHAR_TAB_INT -> {
                            writeByte(TOK.BACKSLASH_INT)
                            writeByte(TOK.CHAR_T_INT)
                        }

                        TOK.CHAR_BS_INT -> {
                            writeByte(TOK.BACKSLASH_INT)
                            writeByte(TOK.CHAR_B_INT)
                        }

                        TOK.CHAR_FF_INT -> {
                            writeByte(TOK.BACKSLASH_INT)
                            writeByte(TOK.CHAR_F_INT)
                        }

                        else -> {
                            if (charCode < TOK.SPACE_INT) {
                                writeByte(TOK.BACKSLASH_INT)
                                writeByte(TOK.CHAR_U_INT)
                                writeUnicodeHex(code = charCode, writeByte = writeByte)
                            } else if (charCode < WR.ASCII_LIMIT) {
                                writeByte(charCode)
                            } else {
                                val charVal = text[index]
                                val isSurrogatePair = charVal.isHighSurrogate() &&
                                    index + 1 < length &&
                                    text[index + 1].isLowSurrogate()
                                if (isSurrogatePair) {
                                    writeUtf8Range(text, index, index + 2)
                                    index++
                                } else {
                                    writeUtf8Range(text, index, index + 1)
                                }
                            }
                        }
                    }
                }
            }
            index++
        }
    }

    inline fun writeIndentation(
        level: Int,
        writeByte: (Int) -> Unit,
    ) {
        val spacesCount = level * WR.SPACES_PER_LEVEL
        var count = 0
        while (count < spacesCount) {
            writeByte(TOK.SPACE_INT)
            count++
        }
    }

    inline fun writeLong(
        value: Long,
        scratch: ByteArray?,
        acquireScratch: () -> ByteArray,
        writeByte: (Int) -> Unit,
        writeUtf8: (String) -> Unit,
        writeBytes: (buf: ByteArray, offset: Int, length: Int) -> Unit,
    ) {
        if (value == 0L) {
            writeByte(TOK.ZERO_INT)
            return
        }
        var remaining = value
        val isNegative = remaining < 0
        if (isNegative) {
            writeByte(TOK.DASH_INT)
            if (remaining == Long.MIN_VALUE) {
                writeUtf8(WR.STR_MIN_LONG_ABS)
                return
            }
            remaining = -remaining
        }
        val scratchBuf = scratch ?: acquireScratch()
        val pos = GhostWriterLongDigits.writePositiveDigitsBytes(absoluteValue = remaining, scratch = scratchBuf)
        writeBytes(scratchBuf, pos, scratchBuf.size - pos)
    }

    inline fun writeUnicodeHex(
        code: Int,
        writeByte: (Int) -> Unit,
    ) {
        val hexChars = WR.HEX_CHARS_ARR
        writeByte(hexChars[(code shr WR.SHIFT_12) and WR.HEX_MASK].toInt())
        writeByte(hexChars[(code shr WR.SHIFT_8) and WR.HEX_MASK].toInt())
        writeByte(hexChars[(code shr WR.SHIFT_4) and WR.HEX_MASK].toInt())
        writeByte(hexChars[code and WR.HEX_MASK].toInt())
    }
}
