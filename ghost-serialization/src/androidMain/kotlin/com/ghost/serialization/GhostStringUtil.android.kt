package com.ghost.serialization

@InternalGhostApi
actual object GhostStringUtil {
    actual fun extractLatin1Bytes(s: String): ByteArray? {
        // Android does not use the same internal string representation or exposes it safely
        return null
    }
}
