package com.ghost.serialization

@InternalGhostApi
expect object GhostStringUtil {
    fun extractLatin1Bytes(s: String): ByteArray?
}
