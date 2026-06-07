package com.ghost.serialization.writer

/**
 * Copies characters from [this] string in the range [startIndex, endIndex) into [dest],
 * starting at [destOffset].
 *
 * On JVM and Android this delegates to the native [String.toCharArray] overload that writes
 * directly into an existing CharArray — no temporary array is allocated.
 * On other targets a manual loop is used (also zero-allocation).
 */
internal expect fun String.copyRangeToCharArray(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
)
