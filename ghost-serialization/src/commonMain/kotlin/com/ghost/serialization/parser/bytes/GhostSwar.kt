package com.ghost.serialization.parser.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants

/**
 * When `true`, hot paths may pack 8 bytes into a [Long] and run SWAR bit tricks
 * (`scanStringSwarNoHash`, space-run skipping, predicted-key wide compare).
 *
 * Default `true` on all targets (JVM/Android/Native/Wasm). Safari/JSC performance
 * is validated on Mac — see docs/SAFARI_WASM_MAC_HANDOFF.md and
 * https://github.com/juanchurtado1991/ghost-serializer/issues/16
 */
internal expect val ghostUseSwarScans: Boolean

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
 *
 * Prefer gating call sites with [ghostUseSwarScans] on Wasm.
 */
internal expect fun ghostReadLong8(data: ByteArray, index: Int): Long
