package com.ghost.serialization.writer.strings

internal actual fun String.copyRangeToCharArray(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
) = copyRangeToCharArrayByLoop(
    dest = dest,
    destOffset = destOffset,
    startIndex = startIndex,
    endIndex = endIndex
)
