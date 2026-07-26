package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for [findClosingQuoteWithKeyHashImpl]: packed layout and hash must match
 * the former separate findClosingQuote + computeKeyHash pipeline.
 */
class FusedClosingQuoteKeyHashTest {

    @Test
    fun packsEndIndexAndHashSeparately() {
        val packed = packClosingQuoteWithKeyHash(endIndex = 42, keyHash = -1)
        assertEquals(42, unpackClosingQuoteIndex(packed))
        assertEquals(-1, unpackKeyHash(packed))
        assertEquals(C.PACKED_KEY_HASH_MASK, packed and C.PACKED_KEY_HASH_MASK)
    }

    @Test
    fun shortKeyWithoutCollisionsMatchesLegacyHash() {
        val bytes = """{"id":1}""".encodeToByteArray()
        // key "id" starts at index 2
        val packed = findClosingQuoteWithKeyHashImpl(2, bytes.size, hasCollisions = false) {
            bytes[it].toInt() and C.BYTE_MASK
        }
        assertEquals(4, unpackClosingQuoteIndex(packed)) // closing quote after "id"
        assertEquals(legacyKeyHash(bytes, 2, 2, hasCollisions = false), unpackKeyHash(packed))
    }

    @Test
    fun longKeyWithoutCollisionsUsesOnlyFirstFourBytes() {
        val bytes = """{"created_at":1}""".encodeToByteArray()
        val start = 2
        val packed = findClosingQuoteWithKeyHashImpl(start, bytes.size, hasCollisions = false) {
            bytes[it].toInt() and C.BYTE_MASK
        }
        val end = unpackClosingQuoteIndex(packed)
        assertEquals(10, end - start) // "created_at".length
        assertEquals(
            legacyKeyHash(bytes, start, end - start, hasCollisions = false),
            unpackKeyHash(packed),
        )
    }

    @Test
    fun collisionHashContinuesPastFourBytes() {
        val bytes = """{"modelCode":1}""".encodeToByteArray()
        val start = 2
        val packed = findClosingQuoteWithKeyHashImpl(start, bytes.size, hasCollisions = true) {
            bytes[it].toInt() and C.BYTE_MASK
        }
        val end = unpackClosingQuoteIndex(packed)
        assertEquals(
            legacyKeyHash(bytes, start, end - start, hasCollisions = true),
            unpackKeyHash(packed),
        )
    }

    @Test
    fun escapeForcesMatchEnd() {
        val bytes = """{"a\"b":1}""".encodeToByteArray()
        val packed = findClosingQuoteWithKeyHashImpl(2, bytes.size, hasCollisions = false) {
            bytes[it].toInt() and C.BYTE_MASK
        }
        assertEquals(C.MATCH_END.toLong(), packed)
    }

    /** Mirror of the removed reader computeKeyHash for golden comparison. */
    private fun legacyKeyHash(
        data: ByteArray,
        start: Int,
        length: Int,
        hasCollisions: Boolean,
    ): Int {
        var key = 0
        if (length >= 4) {
            val b0 = data[start].toInt() and C.BYTE_MASK
            val b1 = data[start + 1].toInt() and C.BYTE_MASK
            val b2 = data[start + 2].toInt() and C.BYTE_MASK
            val b3 = data[start + 3].toInt() and C.BYTE_MASK
            key = b0 or (b1 shl C.SHIFT_8) or (b2 shl C.SHIFT_16) or (b3 shl C.SHIFT_24)
            if (hasCollisions) {
                var ci = C.UNICODE_HEX_LENGTH
                while (ci < length) {
                    key = key * C.COLLISION_HASH_MULTIPLIER + (data[start + ci].toInt() and C.BYTE_MASK)
                    ci++
                }
            }
        } else {
            if (length >= 1) key = key or (data[start].toInt() and C.BYTE_MASK)
            if (length >= 2) key = key or ((data[start + 1].toInt() and C.BYTE_MASK) shl C.SHIFT_8)
            if (length >= 3) key = key or ((data[start + 2].toInt() and C.BYTE_MASK) shl C.SHIFT_16)
        }
        return key
    }
}
