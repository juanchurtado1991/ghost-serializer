@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Decodes double- and single-quoted scalars: escape sequences, `''`-doubling, and the
 * line-fold/trim rules quoted scalars share with plain/block-folded ones. Split out of
 * `GhostYamlScalarDecoding.kt` since this layer never needs the type-resolution logic there.
 */

internal fun GhostYamlFlatReader.readDoubleQuotedString(): String {
    position++ // consume opening '"'
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    var hasEscape = false
    var scanPos = position
    while (scanPos < localLimit) {
        val byteVal = localRawData[scanPos]
        if (byteVal == TOK.DOUBLE_QUOTE_BYTE) {
            break
        }
        if (byteVal == TOK.BACKSLASH_BYTE || isLineBreakByte(byte = byteVal)) {
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

    var outBuffer = acquireScratchBuffer(minSize = SC.SCRATCH_BUFFER_SIZE)
    var outPos = 0
    // Trailing whitespace before a fold is normally trimmed, but an *escaped* space/tab
    // ("\ "/"\t") is real content the author deliberately protected — trimFloor marks how far
    // back the trim loop may go, advanced to outPos after every escape write.
    var trimFloor = 0
    try {
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (currentByte == TOK.DOUBLE_QUOTE_BYTE) {
                position++
                return outBuffer.decodeToString(0, outPos)
            } else if (currentByte == TOK.BACKSLASH_BYTE) {
                position++
                if (position >= localLimit) break
                val nextByte = localRawData[position]
                if (isLineBreakByte(byte = nextByte)) {
                    val isCrLf = nextByte == TOK.CR_BYTE &&
                        position + 1 < localLimit && localRawData[position + 1] == TOK.NEWLINE_BYTE
                    if (isCrLf) {
                        position++
                    }
                    position++
                    while (position < localLimit && isInlineWhitespaceByte(byte = localRawData[position])) {
                        position++
                    }
                } else {
                    val code = processEscapeSequence()
                    if (code <= SC.UTF8_1BYTE_MAX) {
                        if (outPos + 1 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(minSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(buffer = outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] = code.toByte()
                    } else if (code <= SC.UTF8_2BYTE_MAX) {
                        if (outPos + 2 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(minSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(buffer = outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (SC.UTF8_2BYTE_PREFIX or (code shr SC.SHIFT_6_BITS)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or (code and SC.UTF8_CONT_MASK)).toByte()
                    } else if (code <= SC.UTF8_3BYTE_MAX) {
                        if (outPos + 3 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(minSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(buffer = outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (SC.UTF8_3BYTE_PREFIX or (code shr SC.SHIFT_12)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or ((code shr SC.SHIFT_6_BITS) and SC.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or (code and SC.UTF8_CONT_MASK)).toByte()
                    } else {
                        if (outPos + 4 > outBuffer.size) {
                            val newBuffer =
                                acquireScratchBuffer(minSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR)
                            outBuffer.copyInto(newBuffer, 0, 0, outPos)
                            releaseScratchBuffer(buffer = outBuffer)
                            outBuffer = newBuffer
                        }
                        outBuffer[outPos++] =
                            (SC.UTF8_4BYTE_PREFIX or (code shr SC.SHIFT_18_BITS)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or ((code shr SC.SHIFT_12) and SC.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or ((code shr SC.SHIFT_6_BITS) and SC.UTF8_CONT_MASK)).toByte()
                        outBuffer[outPos++] =
                            (SC.UTF8_CONT_PREFIX or (code and SC.UTF8_CONT_MASK)).toByte()
                    }
                }
                trimFloor = outPos
            } else if (isLineBreakByte(byte = currentByte)) {
                // Trim trailing spaces/tabs before the fold, never past trimFloor — that would
                // eat an escaped space/tab the author deliberately protected.
                while (outPos > trimFloor && isInlineWhitespaceByte(byte = outBuffer[outPos - 1])) {
                    outPos--
                }
                val breakCount = skipQuotedLineBreaks()
                val toAppend = if (breakCount == 1) 1 else breakCount - 1
                if (outPos + toAppend > outBuffer.size) {
                    var newSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR
                    while (outPos + toAppend > newSize) {
                        newSize *= SC.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(minSize = newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(buffer = outBuffer)
                    outBuffer = newBuffer
                }
                val fillByte = if (breakCount == 1) TOK.SPACE_BYTE else TOK.NEWLINE_BYTE
                repeat(toAppend) { outBuffer[outPos++] = fillByte }
            } else {
                val startPos = position
                while (position < localLimit &&
                    localRawData[position] != TOK.DOUBLE_QUOTE_BYTE &&
                    localRawData[position] != TOK.BACKSLASH_BYTE &&
                    localRawData[position] != TOK.NEWLINE_BYTE &&
                    localRawData[position] != TOK.CR_BYTE
                ) {
                    position++
                }
                val rangeLength = position - startPos
                if (outPos + rangeLength > outBuffer.size) {
                    var newSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR
                    while (outPos + rangeLength > newSize) {
                        newSize *= SC.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(minSize = newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(buffer = outBuffer)
                    outBuffer = newBuffer
                }
                localRawData.copyInto(outBuffer, outPos, startPos, position)
                outPos += rangeLength
            }
        }
    } finally {
        releaseScratchBuffer(buffer = outBuffer)
    }
    yamlError(message = EM.ERR_UNTERMINATED_DOUBLE_QUOTED)
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
        if (byteVal == TOK.SINGLE_QUOTE_BYTE) {
            if (scanPos + 1 < localLimit && localRawData[scanPos + 1] == TOK.SINGLE_QUOTE_BYTE) {
                hasEscape = true
                scanPos += 2
                continue
            }
            break
        }
        if (isLineBreakByte(byte = byteVal)) {
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

    var outBuffer = acquireScratchBuffer(minSize = SC.SCRATCH_BUFFER_SIZE)
    var outPos = 0
    try {
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (currentByte == TOK.SINGLE_QUOTE_BYTE) {
                position++
                if (position < localLimit && localRawData[position] == TOK.SINGLE_QUOTE_BYTE) {
                    if (outPos + 1 > outBuffer.size) {
                        val newBuffer =
                            acquireScratchBuffer(minSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR)
                        outBuffer.copyInto(newBuffer, 0, 0, outPos)
                        releaseScratchBuffer(buffer = outBuffer)
                        outBuffer = newBuffer
                    }
                    outBuffer[outPos++] = TOK.SINGLE_QUOTE_BYTE
                    position++
                } else {
                    return outBuffer.decodeToString(0, outPos)
                }
            } else if (isLineBreakByte(byte = currentByte)) {
                // Single-quoted scalars have no backslash-escape mechanism, so unlike the
                // double-quoted reader there's never a protected trailing space/tab — always
                // trim trailing whitespace before a fold in full.
                while (outPos > 0 && isInlineWhitespaceByte(byte = outBuffer[outPos - 1])) {
                    outPos--
                }
                val breakCount = skipQuotedLineBreaks()
                val toAppend = if (breakCount == 1) 1 else breakCount - 1
                if (outPos + toAppend > outBuffer.size) {
                    var newSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR
                    while (outPos + toAppend > newSize) {
                        newSize *= SC.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(minSize = newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(buffer = outBuffer)
                    outBuffer = newBuffer
                }
                val fillByte = if (breakCount == 1) TOK.SPACE_BYTE else TOK.NEWLINE_BYTE
                repeat(toAppend) { outBuffer[outPos++] = fillByte }
            } else {
                val startPos = position
                while (position < localLimit &&
                    localRawData[position] != TOK.SINGLE_QUOTE_BYTE &&
                    localRawData[position] != TOK.NEWLINE_BYTE &&
                    localRawData[position] != TOK.CR_BYTE
                ) {
                    position++
                }
                val rangeLength = position - startPos
                if (outPos + rangeLength > outBuffer.size) {
                    var newSize = outBuffer.size * SC.BUFFER_SCALE_FACTOR
                    while (outPos + rangeLength > newSize) {
                        newSize *= SC.BUFFER_SCALE_FACTOR
                    }
                    val newBuffer = acquireScratchBuffer(minSize = newSize)
                    outBuffer.copyInto(newBuffer, 0, 0, outPos)
                    releaseScratchBuffer(buffer = outBuffer)
                    outBuffer = newBuffer
                }
                localRawData.copyInto(outBuffer, outPos, startPos, position)
                outPos += rangeLength
            }
        }
    } finally {
        releaseScratchBuffer(buffer = outBuffer)
    }
    yamlError(message = EM.ERR_UNTERMINATED_SINGLE_QUOTED)
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
        if (currentByte == TOK.CR_BYTE) {
            position++
            if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) position++
        } else {
            position++ // NEWLINE_BYTE
        }
        breakCount++
        while (position < localLimit && isInlineWhitespaceByte(byte = localRawData[position])) {
            position++
        }
        if (position >= localLimit) break
        if (!isLineBreakByte(byte = localRawData[position])) break
    }
    // The quoted scalar is still "open" (closing quote not yet seen), but a line that looks like
    // a document marker is forbidden content inside it regardless (spec's c-forbidden production).
    val endsAtDocumentMarker = position < localLimit && (isDocumentMarker() || isDocumentEndMarker())
    if (endsAtDocumentMarker) {
        val markerName = if (localRawData[position] == TOK.DASH_BYTE) TOK.STR_DOC_START else TOK.STR_DOC_END
        yamlError(
            message = EM.ERR_DOC_MARKER_IN_QUOTED_SCALAR_PREFIX + markerName + EM.ERR_DOC_MARKER_IN_QUOTED_SCALAR_SUFFIX
        )
    }
    return breakCount
}

private fun GhostYamlFlatReader.processEscapeSequence(): Int {
    val currentByte = rawData[position++]
    val currentByteInt = currentByte.toInt()
    val localLimit = limit
    return when (currentByte) {
        TOK.DOUBLE_QUOTE_BYTE -> currentByteInt
        TOK.BACKSLASH_BYTE -> currentByteInt
        TOK.ESCAPE_SLASH_BYTE -> currentByteInt
        TOK.SPACE_BYTE -> currentByteInt
        TOK.TAB_BYTE -> currentByteInt
        TOK.LOWERCASE_B_BYTE -> SC.CODE_BS
        TOK.LOWERCASE_F_BYTE -> SC.CODE_FF
        TOK.LOWERCASE_N_BYTE -> TOK.NEWLINE_INT
        TOK.LOWERCASE_R_BYTE -> SC.CODE_CR
        TOK.LOWERCASE_T_BYTE -> SC.CODE_TAB
        TOK.LOWERCASE_X_BYTE -> {        // \xXX
            if (position + SC.HEX_ESCAPE_X_LEN > localLimit) yamlError(message = EM.ERR_INCOMPLETE_X_ESCAPE)
            val hexVal = parseHex(data = rawData, start = position, length = SC.HEX_ESCAPE_X_LEN)
            position += SC.HEX_ESCAPE_X_LEN
            hexVal
        }

        TOK.LOWERCASE_U_BYTE -> {        // \uXXXX
            if (position + SC.HEX_ESCAPE_U_LEN > localLimit) yamlError(message = EM.ERR_INCOMPLETE_U_ESCAPE)
            val hexVal = parseHex(data = rawData, start = position, length = SC.HEX_ESCAPE_U_LEN)
            position += SC.HEX_ESCAPE_U_LEN
            hexVal
        }

        TOK.UPPERCASE_U_BYTE -> {        // \UXXXXXXXX
            if (position + SC.HEX_ESCAPE_U32_LEN > localLimit) yamlError(message = EM.ERR_INCOMPLETE_U32_ESCAPE)
            val hexVal = parseHex(data = rawData, start = position, length = SC.HEX_ESCAPE_U32_LEN)
            position += SC.HEX_ESCAPE_U32_LEN
            hexVal
        }

        TOK.ZERO_BYTE -> SC.CODE_ZERO
        TOK.LOWERCASE_A_BYTE -> SC.CODE_BEL
        TOK.LOWERCASE_V_BYTE -> SC.CODE_VTAB
        TOK.LOWERCASE_E_BYTE -> SC.CODE_ESC
        TOK.UPPERCASE_N_BYTE -> SC.CODE_NEXT_LINE
        TOK.UNDERSCORE_BYTE -> SC.CODE_NBSP
        TOK.UPPERCASE_L_BYTE -> SC.CODE_LINE_SEP
        TOK.UPPERCASE_P_BYTE -> SC.CODE_PARA_SEP
        else -> yamlError(message = "${EM.ERR_UNKNOWN_ESCAPE_PREFIX}${currentByteInt.toChar()}")
    }
}

private fun GhostYamlFlatReader.parseHex(data: ByteArray, start: Int, length: Int): Int {
    var value = 0
    var index = 0
    while (index < length) {
        val byteVal = data[start + index]
        val digit = when {
            byteVal in TOK.ZERO_BYTE..TOK.NINE_BYTE -> byteVal - TOK.ZERO_BYTE
            byteVal in TOK.LOWERCASE_A_BYTE..TOK.LOWERCASE_F_BYTE -> byteVal - TOK.LOWERCASE_A_BYTE + SC.HEX_RADIX_10
            byteVal in TOK.UPPERCASE_A_BYTE..TOK.UPPERCASE_F_BYTE -> byteVal - TOK.UPPERCASE_A_BYTE + SC.HEX_RADIX_10
            else -> yamlError(message = EM.ERR_INVALID_HEX_IN_ESCAPE)
        }
        value = (value shl SC.HEX_SHIFT_4) or digit
        index++
    }
    return value
}
