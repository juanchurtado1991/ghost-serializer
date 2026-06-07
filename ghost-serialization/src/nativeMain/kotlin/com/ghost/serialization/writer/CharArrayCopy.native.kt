package com.ghost.serialization.writer

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
    var d = destOffset
    while (i < endIndex) {
        dest[d++] = this[i++]
    }
}
