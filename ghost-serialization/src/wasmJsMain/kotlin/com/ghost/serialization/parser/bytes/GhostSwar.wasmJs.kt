package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_1
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_2
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_3
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_4
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_5
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_6
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTE_OFFSET_7
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_32
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_40
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_48
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_56
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_8


// Scalar assembly for Kotlin/Wasm. Byte order is irrelevant for the symmetric
// comparisons this feeds.
internal actual fun ghostReadLong8(data: ByteArray, index: Int): Long =
    (data[index].toLong() and LONG_BYTE_MASK) or
            ((data[index + LONG_BYTE_OFFSET_1].toLong() and LONG_BYTE_MASK) shl SHIFT_8) or
            ((data[index + LONG_BYTE_OFFSET_2].toLong() and LONG_BYTE_MASK) shl SHIFT_16) or
            ((data[index + LONG_BYTE_OFFSET_3].toLong() and LONG_BYTE_MASK) shl SHIFT_24) or
            ((data[index + LONG_BYTE_OFFSET_4].toLong() and LONG_BYTE_MASK) shl SHIFT_32) or
            ((data[index + LONG_BYTE_OFFSET_5].toLong() and LONG_BYTE_MASK) shl SHIFT_40) or
            ((data[index + LONG_BYTE_OFFSET_6].toLong() and LONG_BYTE_MASK) shl SHIFT_48) or
            ((data[index + LONG_BYTE_OFFSET_7].toLong() and LONG_BYTE_MASK) shl SHIFT_56)
