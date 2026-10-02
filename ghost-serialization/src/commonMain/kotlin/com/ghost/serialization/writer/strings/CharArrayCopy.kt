package com.ghost.serialization.writer.strings


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

/**
 * Zero-allocation manual loop behind the native and Wasm actuals of [copyRangeToCharArray].
 * `inline` so each actual keeps the loop in its own body, as when it was copied per platform.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun String.copyRangeToCharArrayByLoop(
    dest: CharArray,
    destOffset: Int,
    startIndex: Int,
    endIndex: Int
) {
    var index = startIndex
    var destIndex = destOffset
    while (index < endIndex) {
        dest[destIndex++] = this[index++]
    }
}
