@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostJsonConstants as C

internal fun GhostProtoJsonFlatReader.readProtoBytes(): ByteArray {
    val decodedString = readQuotedString()
    val lut = C.BASE64_LUT
    val length = decodedString.length
    
    val outputBuffer = acquireScratchBuffer(length)
    var outputPosition = 0
    val chunk = IntArray(4)
    var chunkIndex = 0
    
    try {
        for (charIndex in 0 until length) {
            val byteCode = decodedString[charIndex].code
            if (byteCode <= C.SPACE_INT) continue
            chunk[chunkIndex++] = byteCode
            if (chunkIndex == 4) {
                // Chars outside the LUT's range (any non-Latin-1 code unit) are never valid
                // base64 alphabet members — bounds-check rather than indexing lut[] directly,
                // since a raw index would throw ArrayIndexOutOfBoundsException instead of the
                // documented parse error.
                val value0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
                val value1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
                val value2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
                val value3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1

                if (value0 < 0 || value1 < 0 || value2 == -1 || value3 == -1) {
                    throwError("Invalid base64 character")
                }
                outputBuffer[outputPosition++] = ((value0 shl 2) or (value1 ushr 4)).toByte()
                if (value2 != -2) {
                    outputBuffer[outputPosition++] = ((value1 shl 4) or (value2 ushr 2)).toByte()
                    if (value3 != -2) {
                        outputBuffer[outputPosition++] = ((value2 shl 6) or value3).toByte()
                    }
                }
                chunkIndex = 0
            }
        }
        
        if (chunkIndex > 0) {
            while (chunkIndex < 4) {
                chunk[chunkIndex++] = '='.code
            }
            val value0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
            val value1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
            val value2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
            val value3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1

            if (value0 >= 0 && value1 >= 0) {
                outputBuffer[outputPosition++] = ((value0 shl 2) or (value1 ushr 4)).toByte()
                if (value2 != -2 && value2 >= 0) {
                    outputBuffer[outputPosition++] = ((value1 shl 4) or (value2 ushr 2)).toByte()
                }
            }
        }
        
        return outputBuffer.copyOf(outputPosition)
    } finally {
        releaseScratchBuffer(outputBuffer)
    }
}
