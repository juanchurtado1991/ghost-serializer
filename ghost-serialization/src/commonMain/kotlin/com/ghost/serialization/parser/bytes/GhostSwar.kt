package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_1
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_2
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_3
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_4
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_5
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_6
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTE_OFFSET_7
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.LONG_BYTES
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_16
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_24
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_32
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_40
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_48
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_56
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants.SHIFT_8
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.LONG_BYTE_MASK
import com.ghost.serialization.parser.common.constants.GhostJsonTokens.BYTE_MASK

/**
 * Reads [LONG_BYTES] bytes at [index] into one [Long]. Byte order is
 * platform-defined/unspecified — only use for order-independent comparisons (byte-symmetric
 * constants). Caller must guarantee `index + LONG_BYTES <= data.size`. Used by SWAR hot paths.
 */
internal expect fun ghostReadLong8(data: ByteArray, index: Int): Long

/**
 * Portable scalar assembly shared by the Android, Kotlin/Native and Kotlin/Wasm actuals of
 * [ghostReadLong8] (the JVM actual uses a `VarHandle` view instead). `inline` so each actual's
 * body is byte-for-byte what it was when the loop was copied into every platform file — no extra
 * call on the SWAR hot path.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun ghostReadLong8Scalar(
    data: ByteArray,
    index: Int
): Long = (data[index].toLong() and LONG_BYTE_MASK) or
    ((data[index + LONG_BYTE_OFFSET_1].toLong() and LONG_BYTE_MASK) shl SHIFT_8) or
    ((data[index + LONG_BYTE_OFFSET_2].toLong() and LONG_BYTE_MASK) shl SHIFT_16) or
    ((data[index + LONG_BYTE_OFFSET_3].toLong() and LONG_BYTE_MASK) shl SHIFT_24) or
    ((data[index + LONG_BYTE_OFFSET_4].toLong() and LONG_BYTE_MASK) shl SHIFT_32) or
    ((data[index + LONG_BYTE_OFFSET_5].toLong() and LONG_BYTE_MASK) shl SHIFT_40) or
    ((data[index + LONG_BYTE_OFFSET_6].toLong() and LONG_BYTE_MASK) shl SHIFT_48) or
    ((data[index + LONG_BYTE_OFFSET_7].toLong() and LONG_BYTE_MASK) shl SHIFT_56)

/**
 * `ghostSWARLengthMasks[]` keeps exactly the bits [ghostReadLong8] would assign to byte
 * positions `0 until n`, zeroing the rest — masking `ghostReadLong8(data, i) and
 * ghostSWARLengthMasks[]` compares only the first `n` bytes at `i`, regardless of this
 * platform's (unspecified) byte order, since the mask is built by calling the same
 * [ghostReadLong8] on an `n`-byte `0xFF` prefix rather than assuming a layout.
 *
 * Computed once (plain initializer, not a `get()` accessor) — this is indexed on every
 * predicted-key compare in the `internalSelect` hot path, so recomputing the array (and its
 * per-element scratch [ByteArray]) on every access would be a per-call allocation storm.
 */
internal val ghostSWARLengthMasks: LongArray = LongArray(LONG_BYTES + 1) { number ->
    if (number == 0) {
        0L
    } else {
        ghostReadLong8(
            data = ByteArray(LONG_BYTES) { index ->
                if (index < number) BYTE_MASK.toByte() else 0
            },
            index = 0
        )
    }
}
