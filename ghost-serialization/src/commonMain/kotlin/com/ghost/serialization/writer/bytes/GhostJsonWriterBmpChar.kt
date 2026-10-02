@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.writer.bytes

import com.ghost.serialization.InternalGhostApi
import okio.Buffer
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

internal fun Buffer.writeQuotedBmpCodeUnit(codePoint: Int) {
    writeByte(TOK.QUOTE_INT)
    when {
        codePoint < WR.UTF8_1BYTE_LIMIT -> writeByte(codePoint)
        codePoint < WR.UTF8_2BYTE_LIMIT -> {
            writeByte(WR.UTF8_2BYTE_PREFIX or (codePoint shr WR.UTF8_SHIFT_6))
            writeByte(WR.UTF8_CONT_PREFIX or (codePoint and WR.UTF8_CONT_MASK))
        }

        else -> {
            writeByte(WR.UTF8_3BYTE_PREFIX or (codePoint shr SCN.SHIFT_12))
            writeByte(WR.UTF8_CONT_PREFIX or ((codePoint shr WR.UTF8_SHIFT_6) and WR.UTF8_CONT_MASK))
            writeByte(WR.UTF8_CONT_PREFIX or (codePoint and WR.UTF8_CONT_MASK))
        }
    }
    writeByte(TOK.QUOTE_INT)
}
