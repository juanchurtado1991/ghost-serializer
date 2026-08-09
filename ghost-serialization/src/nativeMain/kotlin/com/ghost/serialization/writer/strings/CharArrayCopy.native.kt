package com.ghost.serialization.writer.strings


/**
 * Native actual: manual loop copy — no temporary CharArray allocated.
 */
internal actual fun String.copyRangeToCharArray(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
) {
    var i = startIndex
    var destIndex = destOffset
    while (i < endIndex) {
        dest[destIndex++] = this[i++]
    }
}
