package com.ghost.serialization

@InternalGhostApi
internal actual fun ghostUtf8BytesToString(bytes: ByteArray, offset: Int, length: Int): String =
    bytes.decodeToString(offset, offset + length)
