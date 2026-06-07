package com.ghost.serialization.writer

/**
 * Android actual: delegates to [String.toCharArray] with a destination array — zero-allocation,
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
