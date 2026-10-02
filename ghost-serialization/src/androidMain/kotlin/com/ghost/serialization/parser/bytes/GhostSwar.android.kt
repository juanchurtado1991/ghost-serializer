package com.ghost.serialization.parser.bytes

// Android's minSdk (21) predates VarHandle byte[] views (API 26), so the bytes are combined
// by hand. Byte order is irrelevant for the symmetric comparisons this feeds.
internal actual fun ghostReadLong8(
    data: ByteArray,
    index: Int
): Long = ghostReadLong8Scalar(
    data = data,
    index = index
)
