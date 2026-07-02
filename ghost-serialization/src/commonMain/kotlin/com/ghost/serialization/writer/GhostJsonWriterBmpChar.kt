@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonConstants as C
import okio.Buffer

/**
 * Writes a JSON string containing a single BMP code point into an Okio [Buffer].
 */
internal fun Buffer.writeQuotedBmpCodeUnit(codePoint: Int) {
    writeByte(C.QUOTE_INT)
    when {
        codePoint < C.UTF8_1BYTE_LIMIT -> writeByte(codePoint)
        codePoint < C.UTF8_2BYTE_LIMIT -> {
            writeByte(C.UTF8_2BYTE_PREFIX or (codePoint shr C.UTF8_SHIFT_6))
            writeByte(C.UTF8_CONT_PREFIX or (codePoint and C.UTF8_CONT_MASK))
        }
        else -> {
            writeByte(C.UTF8_3BYTE_PREFIX or (codePoint shr C.SHIFT_12))
            writeByte(C.UTF8_CONT_PREFIX or ((codePoint shr C.UTF8_SHIFT_6) and C.UTF8_CONT_MASK))
            writeByte(C.UTF8_CONT_PREFIX or (codePoint and C.UTF8_CONT_MASK))
        }
    }
    writeByte(C.QUOTE_INT)
}
