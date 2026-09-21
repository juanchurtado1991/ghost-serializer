package com.ghost.serialization.writer.strings


/** Delegates to [String.toCharArray], a single System.arraycopy with no extra allocation. */
internal actual fun String.copyRangeToCharArray(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
) {
    toCharArray(
        destination = dest,
        destinationOffset = destOffset,
        startIndex = startIndex,
        endIndex = endIndex
    )
}
