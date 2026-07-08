@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.parser.GhostJsonConstants as C

internal fun GhostProtoJsonFlatReader.readProtoBytes(): ByteArray {
    val decodedStr = readQuotedString()
    val lut = C.BASE64_LUT
    val len = decodedStr.length
    
    val outBuf = acquireScratchBuffer(len)
    var outPos = 0
    val chunk = IntArray(4)
    var chunkIdx = 0
    
    try {
        for (j in 0 until len) {
            val b = decodedStr[j].code
            if (b <= C.SPACE_INT) continue
            chunk[chunkIdx++] = b
            if (chunkIdx == 4) {
                // Chars outside the LUT's range (any non-Latin-1 code unit) are never valid
                // base64 alphabet members — bounds-check rather than indexing lut[] directly,
                // since a raw index would throw ArrayIndexOutOfBoundsException instead of the
                // documented parse error.
                val val0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
                val val1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
                val val2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
                val val3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1

                if (val0 < 0 || val1 < 0 || val2 == -1 || val3 == -1) {
                    throwError("Invalid base64 character")
                }
                outBuf[outPos++] = ((val0 shl 2) or (val1 ushr 4)).toByte()
                if (val2 != -2) {
                    outBuf[outPos++] = ((val1 shl 4) or (val2 ushr 2)).toByte()
                    if (val3 != -2) {
                        outBuf[outPos++] = ((val2 shl 6) or val3).toByte()
                    }
                }
                chunkIdx = 0
            }
        }
        
        if (chunkIdx > 0) {
            while (chunkIdx < 4) {
                chunk[chunkIdx++] = '='.code
            }
            val val0 = if (chunk[0] < lut.size) lut[chunk[0]] else -1
            val val1 = if (chunk[1] < lut.size) lut[chunk[1]] else -1
            val val2 = if (chunk[2] < lut.size) lut[chunk[2]] else -1
            val val3 = if (chunk[3] < lut.size) lut[chunk[3]] else -1

            if (val0 >= 0 && val1 >= 0) {
                outBuf[outPos++] = ((val0 shl 2) or (val1 ushr 4)).toByte()
                if (val2 != -2 && val2 >= 0) {
                    outBuf[outPos++] = ((val1 shl 4) or (val2 ushr 2)).toByte()
                }
            }
        }
        
        return outBuf.copyOf(outPos)
    } finally {
        releaseScratchBuffer(outBuf)
    }
}


