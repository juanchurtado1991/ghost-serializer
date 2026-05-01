package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.ByteString

/**
 * Internal utility to peek at a JSON discriminator value without full parsing.
 * Optimized for speed and zero allocations.
 */
@InternalGhostApi
object GhostDiscriminatorPeeker {

    /**
     * Attempts to find the value of [key] in the JSON object starting at [start].
     * Returns the value as a string, or null if not found or if the object is too complex.
     */
    fun peek(
        source: GhostSource,
        rawData: ByteArray?,
        start: Int, 
        limit: Int, 
        key: ByteString
    ): String? {
        var pos = start
        val mask = GhostJsonConstants.WHITESPACE_MASK
        
        if (rawData != null) {
            // FAST PATH: Raw Byte Access
            while (pos < limit) {
                val b = rawData[pos].toInt() and GhostJsonConstants.BYTE_MASK
                if (b > GhostJsonConstants.SPACE_INT || (mask and (1L shl b)) == 0L) {
                    if (b != GhostJsonConstants.OPEN_OBJ_INT) return null
                    pos++
                    break
                }
                pos++
            }
            if (pos >= limit) return null

            val scanLimit = (pos + GhostHeuristics.maxDiscriminatorPeekDistance).coerceAtMost(limit)
            val keySize = key.size

            while (pos < scanLimit) {
                val b = rawData[pos].toInt() and GhostJsonConstants.BYTE_MASK
                if (b == GhostJsonConstants.QUOTE_INT) {
                    val keyStart = pos + 1
                    if (source.contentEquals(keyStart, key)) {
                        pos = keyStart + keySize
                        if (pos < scanLimit && (rawData[pos].toInt() and GhostJsonConstants.BYTE_MASK) == GhostJsonConstants.QUOTE_INT) {
                            pos++
                            while (pos < scanLimit) {
                                val sep = rawData[pos].toInt() and GhostJsonConstants.BYTE_MASK
                                if (sep == GhostJsonConstants.COLON_INT) {
                                    pos++
                                    while (pos < scanLimit) {
                                        val vStart = rawData[pos].toInt() and GhostJsonConstants.BYTE_MASK
                                        if (vStart == GhostJsonConstants.QUOTE_INT) {
                                            val valueStart = pos + 1
                                            var valueEnd = valueStart
                                            while (valueEnd < scanLimit) {
                                                val vB = rawData[valueEnd].toInt() and GhostJsonConstants.BYTE_MASK
                                                if (vB == GhostJsonConstants.QUOTE_INT) {
                                                    return source.decodeToString(valueStart, valueEnd)
                                                }
                                                if (vB == GhostJsonConstants.BACKSLASH_INT) break 
                                                valueEnd++
                                            }
                                            break
                                        } else if (vStart > GhostJsonConstants.SPACE_INT || (mask and (1L shl vStart)) == 0L) break
                                        pos++
                                    }
                                    break
                                } else if (sep > GhostJsonConstants.SPACE_INT || (mask and (1L shl sep)) == 0L) break
                                pos++
                            }
                        }
                    }
                } else if (b == GhostJsonConstants.OPEN_OBJ_INT || b == GhostJsonConstants.OPEN_ARR_INT) return null
                pos++
            }
        } else {
            // SLOW PATH: Source Access
            while (pos < limit) {
                val b = source.get(pos)
                if (b > GhostJsonConstants.SPACE_INT || (mask and (1L shl b)) == 0L) {
                    if (b != GhostJsonConstants.OPEN_OBJ_INT) return null
                    pos++
                    break
                }
                pos++
            }
            if (pos >= limit) return null

            val scanLimit = (pos + GhostHeuristics.maxDiscriminatorPeekDistance).coerceAtMost(limit)
            val keySize = key.size

            while (pos < scanLimit) {
                val b = source.get(pos)
                if (b == GhostJsonConstants.QUOTE_INT) {
                    val keyStart = pos + 1
                    if (source.contentEquals(keyStart, key)) {
                        pos = keyStart + keySize
                        if (pos < scanLimit && source.get(pos) == GhostJsonConstants.QUOTE_INT) {
                            pos++
                            while (pos < scanLimit) {
                                val sep = source.get(pos)
                                if (sep == GhostJsonConstants.COLON_INT) {
                                    pos++
                                    while (pos < scanLimit) {
                                        val vStart = source.get(pos)
                                        if (vStart == GhostJsonConstants.QUOTE_INT) {
                                            val valueStart = pos + 1
                                            var valueEnd = valueStart
                                            while (valueEnd < scanLimit) {
                                                val vB = source.get(valueEnd)
                                                if (vB == GhostJsonConstants.QUOTE_INT) {
                                                    return source.decodeToString(valueStart, valueEnd)
                                                }
                                                if (vB == GhostJsonConstants.BACKSLASH_INT) break
                                                valueEnd++
                                            }
                                            break
                                        } else if (vStart > GhostJsonConstants.SPACE_INT || (mask and (1L shl vStart)) == 0L) break
                                        pos++
                                    }
                                    break
                                } else if (sep > GhostJsonConstants.SPACE_INT || (mask and (1L shl sep)) == 0L) break
                                pos++
                            }
                        }
                    }
                } else if (b == GhostJsonConstants.OPEN_OBJ_INT || b == GhostJsonConstants.OPEN_ARR_INT) return null
                pos++
            }
        }
        return null
    }
}
