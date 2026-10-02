package com.ghost.serialization.parser.bytes

// Kotlin/Native (iOS) and Kotlin/Wasm: scalar assembly (Safari byte-channel Speed Test is competitive — #16). Byte order is irrelevant for the symmetric
// comparisons this feeds.
internal actual fun ghostReadLong8(
    data: ByteArray,
    index: Int
): Long = ghostReadLong8Scalar(
    data = data,
    index = index
)
