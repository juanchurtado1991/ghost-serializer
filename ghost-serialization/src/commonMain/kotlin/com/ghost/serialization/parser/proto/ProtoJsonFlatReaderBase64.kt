@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.parser.bytes.extensions.readQuotedString
import com.ghost.serialization.parser.common.combineFirstByte
import com.ghost.serialization.parser.common.combineSecondByte
import com.ghost.serialization.parser.common.combineThirdByte
import com.ghost.serialization.parser.common.lookupBase64Code
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Decodes a proto3 JSON base64 string into raw bytes, scratch-buffer-pooled for this reader.
 *
 * Same algorithm as [com.ghost.serialization.parser.common.decodeBase64String] (byte-for-byte; the
 * `inline` lookup/combine helpers are shared from `GhostBase64.kt`) — the loop itself is kept as a
 * separate copy per the hot-path DRY exception:
 * this one decodes straight from the scratch buffer without materializing an output [ByteArray]
 * up front, and unifying the two was already measured to cost throughput.
 */
internal fun GhostProtoJsonFlatReader.readProtoBytes(): ByteArray {
    val decodedString = readQuotedString()
    val lut = PC.BASE64_LUT
    val length = decodedString.length

    val outputBuffer = acquireScratchBuffer(minSize = length)
    var outputPosition = 0
    val chunk = IntArray(PC.B64_PAD_MULTIPLIER)
    var chunkIndex = 0

    try {
        for (charIndex in 0 until length) {
            val byteCode = decodedString[charIndex].code
            if (byteCode <= TOK.SPACE_INT) continue
            chunk[chunkIndex++] = byteCode
            if (chunkIndex == PC.B64_PAD_MULTIPLIER) {
                val value0 = lookupBase64Code(lut = lut, code = chunk[0])
                val value1 = lookupBase64Code(lut = lut, code = chunk[1])
                val value2 = lookupBase64Code(lut = lut, code = chunk[2])
                val value3 = lookupBase64Code(lut = lut, code = chunk[3])

                val hasInvalidChar = value0 < 0 || value1 < 0 ||
                    value2 == PC.B64_INVALID_CODE || value3 == PC.B64_INVALID_CODE
                if (hasInvalidChar) {
                    throwError(PC.ERR_INVALID_BASE64)
                }
                outputBuffer[outputPosition++] = combineFirstByte(value0 = value0, value1 = value1)
                if (value2 != PC.B64_PADDING_CODE) {
                    outputBuffer[outputPosition++] = combineSecondByte(value1 = value1, value2 = value2)
                    if (value3 != PC.B64_PADDING_CODE) {
                        outputBuffer[outputPosition++] = combineThirdByte(value2 = value2, value3 = value3)
                    }
                }
                chunkIndex = 0
            }
        }

        if (chunkIndex > 0) {
            while (chunkIndex < PC.B64_PAD_MULTIPLIER) {
                chunk[chunkIndex++] = TOK.EQUALS_INT
            }
            val value0 = lookupBase64Code(lut = lut, code = chunk[0])
            val value1 = lookupBase64Code(lut = lut, code = chunk[1])
            val value2 = lookupBase64Code(lut = lut, code = chunk[2])

            if (value0 >= 0 && value1 >= 0) {
                outputBuffer[outputPosition++] = combineFirstByte(value0 = value0, value1 = value1)
                if (value2 != PC.B64_PADDING_CODE && value2 >= 0) {
                    outputBuffer[outputPosition++] = combineSecondByte(value1 = value1, value2 = value2)
                }
            }
        }

        return outputBuffer.copyOf(outputPosition)
    } finally {
        releaseScratchBuffer(buffer = outputBuffer)
    }
}
