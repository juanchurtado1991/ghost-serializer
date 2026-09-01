package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.LONG_BYTES

/**
 * Reads [LONG_BYTES] bytes at [index] into one [Long]. Byte order is
 * platform-defined/unspecified — only use for order-independent comparisons (byte-symmetric
 * constants). Caller must guarantee `index + LONG_BYTES <= data.size`. Used by SWAR hot paths.
 */
internal expect fun ghostReadLong8(data: ByteArray, index: Int): Long

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
internal val ghostSWARLengthMasks: LongArray = LongArray(LONG_BYTES + 1) { n ->
    if (n == 0) {
        0L
    } else {
        ghostReadLong8(
            data = ByteArray(LONG_BYTES) { index ->
                if (index < n) BYTE_MASK.toByte() else 0
            },
            index = 0
        )
    }
}
