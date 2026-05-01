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
        
        if (rawData != null) {
            // FAST PATH: Raw Byte Access
            // 1. Skip whitespace and find '{'
            while (pos < limit) {
                val b = rawData[pos]
                if (b > GhostJsonConstants.SPACE) {
                    if (b != GhostJsonConstants.OPEN_OBJ) return null
                    pos++
                    break
                }
                pos++
            }
            if (pos >= limit) return null

            val scanLimit = (pos + GhostHeuristics.maxDiscriminatorPeekDistance).coerceAtMost(limit)
            val keySize = key.size

            // 2. Scan for "key"
            while (pos < scanLimit) {
                val b = rawData[pos]
                if (b == GhostJsonConstants.QUOTE_BYTE) {
                    val keyStart = pos + 1
                    if (source.contentEquals(keyStart, key)) {
                        pos = keyStart + keySize
                        if (pos < scanLimit && rawData[pos] == GhostJsonConstants.QUOTE_BYTE) {
                            pos++
                            // 3. Find ':'
                            while (pos < scanLimit) {
                                val sep = rawData[pos]
                                if (sep == GhostJsonConstants.COLON) {
                                    pos++
                                    // 4. Find value quote
                                    while (pos < scanLimit) {
                                        val vStart = rawData[pos]
                                        if (vStart == GhostJsonConstants.QUOTE_BYTE) {
                                            val valueStart = pos + 1
                                            var valueEnd = valueStart
                                            while (valueEnd < scanLimit) {
                                                val vB = rawData[valueEnd]
                                                if (vB == GhostJsonConstants.QUOTE_BYTE) {
                                                    return source.decodeToString(valueStart, valueEnd)
                                                }
                                                if (vB == GhostJsonConstants.BACKSLASH_BYTE) break 
                                                valueEnd++
                                            }
                                            break
                                        } else if (vStart > GhostJsonConstants.SPACE) break
                                        pos++
                                    }
                                    break
                                } else if (sep > GhostJsonConstants.SPACE) break
                                pos++
                            }
                        }
                    }
                } else if (b == GhostJsonConstants.OPEN_OBJ || b == GhostJsonConstants.OPEN_ARR) return null
                pos++
            }
        } else {
            // SLOW PATH: Source Access
            while (pos < limit) {
                val b = source.get(pos)
                if (b > GhostJsonConstants.SPACE_INT) {
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
                                        } else if (vStart > GhostJsonConstants.SPACE_INT) break
                                        pos++
                                    }
                                    break
                                } else if (sep > GhostJsonConstants.SPACE_INT) break
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
