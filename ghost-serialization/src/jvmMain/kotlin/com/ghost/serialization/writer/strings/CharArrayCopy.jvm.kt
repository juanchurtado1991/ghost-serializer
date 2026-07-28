package com.ghost.serialization.writer.strings

import com.ghost.serialization.writer.common.*
/**
 * JVM actual: delegates to [String.toCharArray] with a destination array — zero-allocation,
 * backed by a single native array copy (System.arraycopy internally).
 */
internal actual fun String.copyRangeToCharArray(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
) {
    toCharArray(dest, destOffset, startIndex, endIndex)
}
