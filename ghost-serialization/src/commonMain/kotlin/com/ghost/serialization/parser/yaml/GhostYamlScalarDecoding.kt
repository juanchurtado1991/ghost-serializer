@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Turns raw scalar bytes into Kotlin values: quoted-string unescaping, number parsing, and
 * null/bool/int/float type resolution (YAML 1.2 core schema priority). Pulled out of
 * `GhostYamlFlatReader` since this layer is only called *from* the core block/flow parser and
 * never calls back into it.
 */

/** Interprets raw bytes as the right Kotlin type; never allocates a String until needed. Type priority: null → bool → int → float → string. */
internal fun GhostYamlFlatReader.interpretScalar(data: ByteArray, start: Int, end: Int, expectedTag: Int): Any? {
    val length = end - start
    // A tag forcing string type still resolves to "" on empty content (e.g. "!!str,") — same
    // as at EOF in readValue — so this runs before the untagged "empty means null" default below.
    if (expectedTag == GhostYamlTags.TAG_STR) {
        return data.decodeToString(start, end)
    }
    if (length == 0) return null

    val firstByte = data[start]

    if (expectedTag == GhostYamlTags.TAG_NULL) {
        return null
    }
    if (expectedTag == GhostYamlTags.TAG_BOOL) {
        if (isTrueLiteral(data, start, length)) return true
        if (isFalseLiteral(data, start, length)) return false
        return false
    }
    if (expectedTag == GhostYamlTags.TAG_INT) {
        tryParseNumber(data, start, end)?.let {
            if (it is Long) return it
            if (it is Double) return it.toLong()
        }
        return data.decodeToString(start, end).toLongOrNull() ?: 0L
    }
    if (expectedTag == GhostYamlTags.TAG_FLOAT) {
        tryParseNumber(data, start, end)?.let {
            if (it is Double) return it
            if (it is Long) return it.toDouble()
        }
        return data.decodeToString(start, end).toDoubleOrNull() ?: 0.0
    }

    // null: ~, null, Null, NULL
    if (firstByte == C.TILDE_BYTE && length == 1) return null
    if (isNullLiteral(data, start, length)) return null

    // bool: true, True, TRUE, false, False, FALSE
    if (isTrueLiteral(data, start, length)) return true
    if (isFalseLiteral(data, start, length)) return false

    // number: starts with digit, '-', or '.' (for .inf/.nan)
    if (firstByte == C.DASH_BYTE || isDigit(firstByte) || firstByte == C.DOT_BYTE) {
        tryParseNumber(data, start, end)?.let { return it }
    }

    // Fallback: string
    return data.decodeToString(start, end)
}

// ── Quoted strings ─────────────────────────────────────────────────────────

internal fun GhostYamlFlatReader.readDoubleQuotedString(): String {
    position++ // consume opening '"'
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    var hasEscape = false
    var scanPos = position
    while (scanPos < localLimit) {
        val byteVal = localRawData[scanPos]
        if (byteVal == C.DOUBLE_QUOTE_BYTE) {
            break
        }
        if (byteVal == C.BACKSLASH_BYTE || byteVal == C.NEWLINE_BYTE || byteVal == C.CR_BYTE) {
            // A line break needs folding, same as an escape sequence needs decoding — both force
            // the slow path.
            hasEscape = true
            break
        }
        scanPos++
    }

    if (!hasEscape && scanPos < localLimit) {
        position = scanPos + 1 // consume string and closing quote
        return localRawData.decodeToString(startPosition, scanPos)
    }

    var outBuffer = acquireScratchBuffer(C.SCRATCH_BUFFER_SIZE)
    var outPos = 0
    // Trailing whitespace before a fold is normally trimmed, but an *escaped* space/tab
    // ("\ "/"\t") is real content the author deliberately protected — trimFloor marks how far
    // back the trim loop may go, advanced to outPos after every escape write.
    var trimFloor = 0
    try {
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (currentByte == C.DOUBLE_QUOTE_BYTE) {
                position++
                return outBuffer.decodeToString(0, outPos)
            } else if (currentByte == C.BACKSLASH_BYTE) {
                position++
                if (position >= localLimit) break
                val nextByte = localRawData[position]
                if (nextByte == C.NEWLINE_BYTE || nextByte == C.CR_BYTE) {
                    if (nextByte == C.CR_BYTE && position + 1 < localLimit && localRawData[position + 1] == C.NEWLINE_BYTE) {
                        position++
                    }
                    position++
                    while (position < localLimit && (localRawData[position] == C.SPACE_BYTE || localRawData[position] == C.TAB_BYTE)) {
                        position++
                    }
                } else {
                    val code = processEscapeSequence()
                    if (code <= C.UTF8_1BYTE_MAX) {
                        if (outPos + 1 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] = code.toByte()
                    } else if (code <= C.UTF8_2BYTE_MAX) {
                        if (outPos + 2 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (C.UTF8_2BYTE_PREFIX or (code shr C.SHIFT_6_BITS)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                    } else if (code <= C.UTF8_3BYTE_MAX) {
                        if (outPos + 3 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (C.UTF8_3BYTE_PREFIX or (code shr C.SHIFT_12)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or ((code shr C.SHIFT_6_BITS) and C.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                    } else {
                        if (outPos + 4 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (C.UTF8_4BYTE_PREFIX or (code shr C.SHIFT_18_BITS)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or ((code shr C.SHIFT_12) and C.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or ((code shr C.SHIFT_6_BITS) and C.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (C.UTF8_CONT_PREFIX or (code and C.UTF8_CONT_MASK)).toByte()
                    }
                }
                trimFloor = outPos
            } else if (currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE) {
                // Trim trailing spaces/tabs before the fold, never past trimFloor — that would
                // eat an escaped space/tab the author deliberately protected.
                while (outPos > trimFloor && (outBuffer[outPos - 1] == C.SPACE_BYTE || outBuffer[outPos - 1] == C.TAB_BYTE)) {
                    outPos--
                }
                val breakCount = skipQuotedLineBreaks()
                val toAppend = if (breakCount == 1) 1 else breakCount - 1
                if (outPos + toAppend > outBuffer.size) {
                    var newSize = outBuffer.size * C.BUFFER_SCALE_FACTOR
                    while (outPos + toAppend > newSize) {
                        newSize *= C.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(outBuffer)
                    outBuffer = newBuffer
                }
                val fillByte = if (breakCount == 1) C.SPACE_BYTE else C.NEWLINE_BYTE
                repeat(toAppend) { outBuffer[outPos++] = fillByte }
            } else {
                val startPos = position
                while (position < localLimit &&
                    localRawData[position] != C.DOUBLE_QUOTE_BYTE &&
                    localRawData[position] != C.BACKSLASH_BYTE &&
                    localRawData[position] != C.NEWLINE_BYTE &&
                    localRawData[position] != C.CR_BYTE
                ) {
                    position++
                }
                val rangeLength = position - startPos
                if (outPos + rangeLength > outBuffer.size) {
                    var newSize = outBuffer.size * C.BUFFER_SCALE_FACTOR
                    while (outPos + rangeLength > newSize) {
                        newSize *= C.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(outBuffer)
                    outBuffer = newBuffer
                }
                localRawData.copyInto(outBuffer, outPos, startPos, position)
                outPos += rangeLength
            }
        }
    } finally {
        releaseScratchBuffer(outBuffer)
    }
    yamlError(C.ERR_UNTERMINATED_DOUBLE_QUOTED)
}

internal fun GhostYamlFlatReader.readSingleQuotedString(): String {
    position++ // consume opening '\''
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    var hasEscape = false
    var scanPos = position
    while (scanPos < localLimit) {
        val byteVal = localRawData[scanPos]
        if (byteVal == C.SINGLE_QUOTE_BYTE) {
            if (scanPos + 1 < localLimit && localRawData[scanPos + 1] == C.SINGLE_QUOTE_BYTE) {
                hasEscape = true
                scanPos += 2
                continue
            }
            break
        }
        if (byteVal == C.NEWLINE_BYTE || byteVal == C.CR_BYTE) {
            // A line break needs folding, same as a doubled '' needs unescaping — both force the
            // slow path.
            hasEscape = true
            break
        }
        scanPos++
    }

    if (!hasEscape && scanPos < localLimit) {
        position = scanPos + 1 // consume string and closing quote
        return localRawData.decodeToString(startPosition, scanPos)
    }

    var outBuffer = acquireScratchBuffer(C.SCRATCH_BUFFER_SIZE)
    var outPos = 0
    try {
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (currentByte == C.SINGLE_QUOTE_BYTE) {
                position++
                if (position < localLimit && localRawData[position] == C.SINGLE_QUOTE_BYTE) {
                    if (outPos + 1 > outBuffer.size) {
                        val newBuffer =
                            acquireScratchBuffer(outBuffer.size * C.BUFFER_SCALE_FACTOR)
                        outBuffer.copyInto(newBuffer, 0, 0, outPos)
                        releaseScratchBuffer(outBuffer)
                        outBuffer = newBuffer
                    }
                    outBuffer[outPos++] = C.SINGLE_QUOTE_BYTE
                    position++
                } else {
                    return outBuffer.decodeToString(0, outPos)
                }
            } else if (currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE) {
                // Single-quoted scalars have no backslash-escape mechanism, so unlike the
                // double-quoted reader there's never a protected trailing space/tab — always
                // trim trailing whitespace before a fold in full.
                while (outPos > 0 && (outBuffer[outPos - 1] == C.SPACE_BYTE || outBuffer[outPos - 1] == C.TAB_BYTE)) {
                    outPos--
                }
                val breakCount = skipQuotedLineBreaks()
                val toAppend = if (breakCount == 1) 1 else breakCount - 1
                if (outPos + toAppend > outBuffer.size) {
                    var newSize = outBuffer.size * C.BUFFER_SCALE_FACTOR
                    while (outPos + toAppend > newSize) {
                        newSize *= C.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(outBuffer)
                    outBuffer = newBuffer
                }
                val fillByte = if (breakCount == 1) C.SPACE_BYTE else C.NEWLINE_BYTE
                repeat(toAppend) { outBuffer[outPos++] = fillByte }
            } else {
                val startPos = position
                while (position < localLimit &&
                    localRawData[position] != C.SINGLE_QUOTE_BYTE &&
                    localRawData[position] != C.NEWLINE_BYTE &&
                    localRawData[position] != C.CR_BYTE
                ) {
                    position++
                }
                val rangeLength = position - startPos
                if (outPos + rangeLength > outBuffer.size) {
                    var newSize = outBuffer.size * C.BUFFER_SCALE_FACTOR
                    while (outPos + rangeLength > newSize) {
                        newSize *= C.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(outBuffer)
                    outBuffer = newBuffer
                }
                localRawData.copyInto(outBuffer, outPos, startPos, position)
                outPos += rangeLength
            }
        }
    } finally {
        releaseScratchBuffer(outBuffer)
    }
    yamlError(C.ERR_UNTERMINATED_SINGLE_QUOTED)
}

/**
 * Called with [GhostYamlFlatReader.position] at a line-break inside a quoted scalar. Folds it
 * like plain/block-folded scalars: one line break becomes a space, N consecutive breaks (N-1
 * blank lines) become N-1 newlines. Each line's leading whitespace is fully skipped — quoted
 * scalars have no block-style indentation to preserve. Leaves position at the first non-blank
 * content (or closing quote); returns the number of line breaks folded.
 */
private fun GhostYamlFlatReader.skipQuotedLineBreaks(): Int {
    val localRawData = rawData
    val localLimit = limit
    var breakCount = 0
    while (position < localLimit) {
        val currentByte = localRawData[position]
        if (currentByte == C.CR_BYTE) {
            position++
            if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
        } else {
            position++ // NEWLINE_BYTE
        }
        breakCount++
        while (position < localLimit && (localRawData[position] == C.SPACE_BYTE || localRawData[position] == C.TAB_BYTE)) {
            position++
        }
        if (position >= localLimit) break
        val next = localRawData[position]
        if (next != C.NEWLINE_BYTE && next != C.CR_BYTE) break
    }
    // The quoted scalar is still "open" (closing quote not yet seen), but a line that looks like
    // a document marker is forbidden content inside it regardless (spec's c-forbidden production).
    if (position < localLimit && (isDocumentMarker() || isDocumentEndMarker())) {
        yamlError("${C.ERR_DOC_MARKER_IN_QUOTED_SCALAR_PREFIX}${if (localRawData[position] == C.DASH_BYTE) C.STR_DOC_START else C.STR_DOC_END}${C.ERR_DOC_MARKER_IN_QUOTED_SCALAR_SUFFIX}")
    }
    return breakCount
}

private fun GhostYamlFlatReader.processEscapeSequence(): Int {
    val currentByte = rawData[position++]
    val currentByteInt = currentByte.toInt()
    val localLimit = limit
    return when (currentByte) {
        C.DOUBLE_QUOTE_BYTE -> currentByteInt
        C.BACKSLASH_BYTE -> currentByteInt
        C.ESCAPE_SLASH_BYTE -> currentByteInt
        C.SPACE_BYTE -> currentByteInt
        C.TAB_BYTE -> currentByteInt
        C.LOWERCASE_B_BYTE -> C.CODE_BS
        C.LOWERCASE_F_BYTE -> C.CODE_FF
        C.LOWERCASE_N_BYTE -> C.CHAR_LF_INT
        C.LOWERCASE_R_BYTE -> C.CODE_CR
        C.LOWERCASE_T_BYTE -> C.CODE_TAB
        C.LOWERCASE_X_BYTE -> {        // \xXX
            if (position + C.HEX_ESCAPE_X_LEN > localLimit) yamlError(C.ERR_INCOMPLETE_X_ESCAPE)
            val hexVal = parseHex(rawData, position, C.HEX_ESCAPE_X_LEN)
            position += C.HEX_ESCAPE_X_LEN
            hexVal
        }

        C.LOWERCASE_U_BYTE -> {        // \uXXXX
            if (position + C.HEX_ESCAPE_U_LEN > localLimit) yamlError(C.ERR_INCOMPLETE_U_ESCAPE)
            val hexVal = parseHex(rawData, position, C.HEX_ESCAPE_U_LEN)
            position += C.HEX_ESCAPE_U_LEN
            hexVal
        }

        C.UPPERCASE_U_BYTE -> {        // \UXXXXXXXX
            if (position + C.HEX_ESCAPE_U32_LEN > localLimit) yamlError(C.ERR_INCOMPLETE_U32_ESCAPE)
            val hexVal = parseHex(rawData, position, C.HEX_ESCAPE_U32_LEN)
            position += C.HEX_ESCAPE_U32_LEN
            hexVal
        }

        C.ZERO_BYTE -> C.CODE_ZERO
        C.LOWERCASE_A_BYTE -> C.CODE_BEL
        C.LOWERCASE_V_BYTE -> C.CODE_VTAB
        C.LOWERCASE_E_BYTE -> C.CODE_ESC
        C.UPPERCASE_N_BYTE -> C.CODE_NEXT_LINE
        C.UNDERSCORE_BYTE -> C.CODE_NBSP
        C.UPPERCASE_L_BYTE -> C.CODE_LINE_SEP
        C.UPPERCASE_P_BYTE -> C.CODE_PARA_SEP
        else -> yamlError("${C.ERR_UNKNOWN_ESCAPE_PREFIX}${currentByteInt.toChar()}")
    }
}

private fun GhostYamlFlatReader.parseHex(data: ByteArray, start: Int, length: Int): Int {
    var value = 0
    var index = 0
    while (index < length) {
        val byteVal = data[start + index]
        val digit = when {
            byteVal in C.ZERO_BYTE..C.NINE_BYTE -> byteVal - C.ZERO_BYTE
            byteVal in C.LOWERCASE_A_BYTE..C.LOWERCASE_F_BYTE -> byteVal - C.LOWERCASE_A_BYTE + C.HEX_RADIX_10
            byteVal in C.UPPERCASE_A_BYTE..C.UPPERCASE_F_BYTE -> byteVal - C.UPPERCASE_A_BYTE + C.HEX_RADIX_10
            else -> yamlError(C.ERR_INVALID_HEX_IN_ESCAPE)
        }
        value = (value shl C.HEX_SHIFT_4) or digit
        index++
    }
    return value
}

// ── Number parsing ─────────────────────────────────────────────────────────

internal fun GhostYamlFlatReader.readNumber(): Any {
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Negative hex/octal/binary ("-0x10", "-0o17", "-0b101") aren't plain decimal digits, so the
    // digit-only loop below would stop right after the leading "-0". Scan their digit classes
    // explicitly and hand the full token to tryParseNumber, which parses (and negates) these bases.
    var prefixPosition = position
    if (prefixPosition < localLimit && localRawData[prefixPosition] == C.DASH_BYTE) {
        prefixPosition++
    }
    if (prefixPosition + 1 < localLimit && localRawData[prefixPosition] == C.ZERO_BYTE) {
        val baseByte = localRawData[prefixPosition + 1]
        val isHex = baseByte == C.LOWERCASE_X_BYTE || baseByte == C.UPPERCASE_X_BYTE
        val isOctal = baseByte == C.LOWERCASE_O_BYTE || baseByte == C.UPPERCASE_O_BYTE
        val isBinary = baseByte == C.LOWERCASE_B_BYTE || baseByte == C.UPPERCASE_B_BYTE
        if (isHex || isOctal || isBinary) {
            position = prefixPosition + 2
            while (position < localLimit) {
                val currentByte = localRawData[position]
                val isBaseDigit = when {
                    isHex -> isDigit(currentByte) ||
                            currentByte in C.LOWERCASE_A_BYTE..C.LOWERCASE_F_BYTE ||
                            currentByte in C.UPPERCASE_A_BYTE..C.UPPERCASE_F_BYTE

                    isOctal -> currentByte in C.ZERO_BYTE..C.SEVEN_BYTE
                    else -> currentByte == C.ZERO_BYTE || currentByte == C.ONE_BYTE
                }
                if (!isBaseDigit) break
                position++
            }
            return tryParseNumber(localRawData, startPosition, position)
                ?: localRawData.decodeToString(startPosition, position)
        }
    }

    while (position < localLimit) {
        val currentByte = localRawData[position]
        if (!isDigit(currentByte) && currentByte != C.DASH_BYTE && currentByte != C.PLUS_BYTE && currentByte != C.DOT_BYTE &&
            currentByte != C.LOWERCASE_E_BYTE && currentByte != C.UPPERCASE_E_BYTE
        ) break  // e, E for scientific
        position++
    }
    return tryParseNumber(localRawData, startPosition, position)
        ?: localRawData.decodeToString(startPosition, position)
}

// ── Bitwise scalar type checks ─────────────────────────────────────────────
// (isDigit itself stays on GhostYamlFlatReader — it's already internal and shared with
// GhostYamlBlockScalarSubsystem.kt, no benefit to relocating a one-line leaf function.)

/** Checks if bytes[start..start+len) match 'null', 'Null', or 'NULL'. */
private fun isNullLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 4) return false
    val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == C.LOWERCASE_N_BYTE && byte1 == C.LOWERCASE_U_BYTE && byte2 == C.LOWERCASE_L_BYTE && byte3 == C.LOWERCASE_L_BYTE
}

/** Checks if bytes[start..start+len) match 'true', 'True', or 'TRUE'. */
private fun isTrueLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 4) return false
    val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == C.LOWERCASE_T_BYTE && byte1 == C.LOWERCASE_R_BYTE && byte2 == C.LOWERCASE_U_BYTE && byte3 == C.LOWERCASE_E_BYTE
}

/** Checks if bytes[start..start+len) match 'false', 'False', or 'FALSE'. */
private fun isFalseLiteral(data: ByteArray, start: Int, length: Int): Boolean {
    if (length != 5) return false
    val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    val byte4 = (data[start + 4].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
    return byte0 == C.LOWERCASE_F_BYTE && byte1 == C.LOWERCASE_A_BYTE && byte2 == C.LOWERCASE_L_BYTE && byte3 == C.LOWERCASE_S_BYTE && byte4 == C.LOWERCASE_E_BYTE
}

/**
 * Attempts to parse [data] between [start] and [end] as a [Long] or [Double].
 *
 * Returns `null` when the slice is not a valid number. Parsing is performed incrementally
 * over bytes; callers must not decode the entire range with [String.toInt] or [String.toDouble].
 */
private fun GhostYamlFlatReader.tryParseNumber(data: ByteArray, start: Int, end: Int): Any? {
    val length = end - start
    if (length == 0) return null

    var currentPosition = start
    var isNegative = false

    if (data[currentPosition] == C.DASH_BYTE) {
        isNegative = true; currentPosition++
    }
    if (currentPosition >= end) return null

    // Check for hex (0x), octal (0o), binary (0b)
    if (end - currentPosition >= 3 && data[currentPosition] == C.ZERO_BYTE) {
        val nextByte = data[currentPosition + 1]
        if (nextByte == C.LOWERCASE_X_BYTE || nextByte == C.UPPERCASE_X_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                val digit = when {
                    isDigit(currentByte) -> (currentByte - C.ZERO_BYTE).toLong()
                    currentByte in C.LOWERCASE_A_BYTE..C.LOWERCASE_F_BYTE -> (currentByte - C.LOWERCASE_A_BYTE + 10).toLong()
                    currentByte in C.UPPERCASE_A_BYTE..C.UPPERCASE_F_BYTE -> (currentByte - C.UPPERCASE_A_BYTE + 10).toLong()
                    else -> return null
                }
                value = (value shl C.HEX_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
        if (nextByte == C.LOWERCASE_O_BYTE || nextByte == C.UPPERCASE_O_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                if (currentByte < C.ZERO_BYTE || currentByte > C.SEVEN_BYTE) return null
                val digit = (currentByte - C.ZERO_BYTE).toLong()
                value = (value shl C.OCTAL_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
        if (nextByte == C.LOWERCASE_B_BYTE || nextByte == C.UPPERCASE_B_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                if (currentByte != C.ZERO_BYTE && currentByte != C.ONE_BYTE) return null
                val digit = (currentByte - C.ZERO_BYTE).toLong()
                value = (value shl C.BINARY_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
    }

    // Check for .inf / .nan
    if (data[currentPosition] == C.DOT_BYTE) {
        val stringRepresentation = data.decodeToString(start, end)
        return when (stringRepresentation.lowercase()) {
            C.STR_DOT_INF, C.STR_PLUS_DOT_INF -> Double.POSITIVE_INFINITY
            C.STR_MINUS_DOT_INF -> Double.NEGATIVE_INFINITY
            C.STR_DOT_NAN -> Double.NaN
            else -> null
        }
    }

    // Parse integer part byte by byte
    var accumulatedLongValue = 0L
    var hasDigit = false
    var isFloatingPoint = false

    while (currentPosition < end) {
        val currentByte = data[currentPosition]
        when {
            isDigit(currentByte) -> {
                hasDigit = true
                val digit = (currentByte - C.ZERO_BYTE).toLong()
                // Overflow check
                if (accumulatedLongValue > (Long.MAX_VALUE - digit) / 10) {
                    isFloatingPoint = true
                    break
                }
                accumulatedLongValue = accumulatedLongValue * 10 + digit
                currentPosition++
            }

            currentByte == C.DOT_BYTE || currentByte == C.LOWERCASE_E_BYTE || currentByte == C.UPPERCASE_E_BYTE -> {
                isFloatingPoint = true
                break
            }

            else -> return null
        }
    }

    if (!hasDigit) return null

    if (!isFloatingPoint && currentPosition == end) {
        return if (isNegative) -accumulatedLongValue else accumulatedLongValue
    }

    val stringRepresentation = data.decodeToString(start, end)
    return stringRepresentation.toDoubleOrNull()
}
