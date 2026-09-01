package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants

/**
 * Reads [GhostJsonConstants.LONG_BYTES] bytes at [index] into one [Long]. Byte order is
 * platform-defined/unspecified — only use for order-independent comparisons (byte-symmetric
 * constants). Caller must guarantee `index + LONG_BYTES <= data.size`. Used by SWAR hot paths.
 */
internal expect fun ghostReadLong8(data: ByteArray, index: Int): Long
