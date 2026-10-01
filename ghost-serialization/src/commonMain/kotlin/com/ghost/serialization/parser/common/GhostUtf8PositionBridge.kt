@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Bridges char-indexed [String] positions to/from UTF-8 byte offsets without allocating a
 * [ByteArray]. Used by [com.ghost.serialization.parser.strings.GhostJsonStringReader] to
 * translate KSP-generated custom decoders' byte-indexed positions to/from its own char-indexed
 * ones. Surrogate pairs count as two `Char`s but four UTF-8 bytes.
 */

/** Maps a char-based position in [s] to the corresponding UTF-8 byte offset. */
@InternalGhostApi
fun charToBytePosition(
    s: String,
    charPos: Int
): Int {
    var bytePos = 0
    var index = 0
    while (index < charPos && index < s.length) {
        bytePos += utf8ByteSizeAt(s = s, index = index)
        index += if (isSurrogatePairStart(s = s, index = index)) 2 else 1
    }
    return bytePos
}

/** Inverse of [charToBytePosition]: maps a UTF-8 byte offset back to a char-indexed position in [s]. */
@InternalGhostApi
fun byteToCharPosition(
    s: String,
    targetBytePos: Int
): Int {
    var bytePos = 0
    var index = 0
    while (bytePos < targetBytePos && index < s.length) {
        bytePos += utf8ByteSizeAt(s = s, index = index)
        index += if (isSurrogatePairStart(s = s, index = index)) 2 else 1
    }
    return index
}

/** Whether the code point starting at [index] is a valid high+low surrogate pair. */
internal inline fun isSurrogatePairStart(s: String, index: Int): Boolean {
    val code = s[index].code
    return code in TOK.HIGH_SURROGATE_START..TOK.HIGH_SURROGATE_END &&
        index + 1 < s.length &&
        s[index + 1].code in TOK.LOW_SURROGATE_START..TOK.LOW_SURROGATE_END
}

/** UTF-8 byte width of the code point at [index] (4 for a surrogate pair start). */
internal inline fun utf8ByteSizeAt(s: String, index: Int): Int {
    val code = s[index].code
    return when {
        code <= WR.UTF8_1BYTE_MAX -> WR.UTF8_1BYTE_SIZE
        code <= WR.UTF8_2BYTE_MAX -> WR.UTF8_2BYTE_SIZE
        isSurrogatePairStart(s = s, index = index) -> WR.UTF8_4BYTE_SIZE
        else -> WR.UTF8_3BYTE_SIZE
    }
}
