@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.types.RawJson

/**
 * Stack-scoped accumulator for [@GhostWrappedKeys][com.ghost.serialization.annotations.GhostWrappedKeys]
 * deserialization.
 *
 * Each slot stores a zero-copy [RawJson] slice for one wire key. [materializeWrappedObject] builds
 * a synthetic `{ "k1": v1, "k2": v2, ... }` object in a pooled scratch buffer (amortized zero
 * allocation on the hot path).
 */
class GhostWrappedKeysCapture(
  private val slotCount: Int,
) {
    private val values = arrayOfNulls<RawJson>(slotCount)
    private var presentMask = 0

    /**
     * Records a captured JSON value for [index] (position in the annotation `keys` array).
     */
    fun put(index: Int, value: RawJson) {
        values[index] = value
        presentMask = presentMask or (1 shl index)
    }

    /**
     * Builds UTF-8 bytes for a synthetic wrapper object, or `null` when [omitIfEmpty] /
     * [omitIfAbsentIndices] rules reject assembly.
     *
     * @param keyUtf8Literals Pre-encoded `"key":` prefixes (including quotes and colon) in key order.
     * @param omitIfEmpty Skip assembly when every slot is absent or JSON `null`.
     * @param omitIfAbsentIndices Slot indices that must be present and non-null.
     */
    fun materializeWrappedObject(
        keyUtf8Literals: Array<ByteArray>,
        omitIfEmpty: Boolean,
        omitIfAbsentIndices: IntArray,
    ): ByteArray? {
        if (omitIfEmpty && !hasNonNullValue()) {
            return null
        }
        for (index in omitIfAbsentIndices) {
            val captured = values[index]
            if (captured == null || captured.isJsonNull) {
                return null
            }
        }

        var estimatedSize = OPEN_CLOSE_BRACE_SIZE
        for (index in 0 until slotCount) {
            estimatedSize += keyUtf8Literals[index].size
            val captured = values[index]
            estimatedSize += if (captured != null) {
                captured.storageLength
            } else {
                JSON_NULL_LENGTH
            }
            if (index < slotCount - 1) {
                estimatedSize++
            }
        }

        val scratch = acquireScratchBuffer(estimatedSize)
        var writePos = 0
        scratch[writePos++] = OPEN_BRACE
        for (index in 0 until slotCount) {
            if (index > 0) {
                scratch[writePos++] = COMMA
            }
            val keyPrefix = keyUtf8Literals[index]
            keyPrefix.copyInto(scratch, writePos)
            writePos += keyPrefix.size
            val captured = values[index]
            if (captured != null) {
                captured.storage.copyInto(
                    scratch,
                    writePos,
                    captured.storageOffset,
                    captured.endExclusive,
                )
                writePos += captured.storageLength
            } else {
                JSON_NULL_BYTES.copyInto(scratch, writePos)
                writePos += JSON_NULL_LENGTH
            }
        }
        scratch[writePos++] = CLOSE_BRACE
        return scratch.copyOf(writePos)
    }

    private fun hasNonNullValue(): Boolean {
        for (index in 0 until slotCount) {
            val captured = values[index]
            if (captured != null && !captured.isJsonNull) {
                return true
            }
        }
        return false
    }

    private companion object {
        private const val OPEN_BRACE: Byte = '{'.code.toByte()
        private const val CLOSE_BRACE: Byte = '}'.code.toByte()
        private const val COMMA: Byte = ','.code.toByte()
        private const val OPEN_CLOSE_BRACE_SIZE = 2
        private const val JSON_NULL_LENGTH = 4
        private val JSON_NULL_BYTES = byteArrayOf(
            'n'.code.toByte(),
            'u'.code.toByte(),
            'l'.code.toByte(),
            'l'.code.toByte(),
        )
    }
}

/**
 * Captures the next JSON value into [capture] at [slotIndex] using a zero-copy [RawJson] slice.
 */
fun GhostJsonFlatReader.captureWrappedKey(capture: GhostWrappedKeysCapture, slotIndex: Int) {
    capture.put(slotIndex, captureRawJson())
}

/**
 * @see captureWrappedKey
 */
fun GhostJsonReader.captureWrappedKey(capture: GhostWrappedKeysCapture, slotIndex: Int) {
    capture.put(slotIndex, captureRawJson())
}

/**
 * @see captureWrappedKey
 */
fun GhostJsonStringReader.captureWrappedKey(capture: GhostWrappedKeysCapture, slotIndex: Int) {
    capture.put(slotIndex, captureRawJson())
}
