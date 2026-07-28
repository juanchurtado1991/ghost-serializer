package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants

/**
 * Reads [GhostJsonConstants.LONG_BYTES] consecutive bytes starting at [index] into a single [Long].
 *
 * The byte order is platform-defined and intentionally unspecified: callers must only use
 * this for order-independent comparisons (e.g. detecting a run of identical bytes, where the
 * comparison constant is byte-symmetric). The caller must guarantee
 * `index + LONG_BYTES <= data.size`.
 *
 * JVM/Android may implement this as a single wide load; other targets fall back to scalar
 * assembly. This exists to accelerate whitespace skipping over pretty-printed JSON, where
 * long runs of ASCII spaces ([GhostJsonConstants.SPACE_INT]) dominate the byte volume.
 */
internal expect fun ghostReadLong8(data: ByteArray, index: Int): Long
