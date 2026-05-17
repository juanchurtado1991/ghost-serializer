package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import java.nio.charset.StandardCharsets

/**
 * JVM-optimized [GhostSource] that overrides [decodeJsonStringRange]
 * to use [StandardCharsets.US_ASCII] for known 7-bit content,
 * bypassing full UTF-8 validation.
 */
@InternalGhostApi
class JvmByteArraySource(
    data: ByteArray
) : ByteArrayGhostSource(data) {

    override fun decodeJsonStringRange(
        start: Int,
        end: Int,
        isKnown7BitContent: Boolean
    ): String {
        if (!isKnown7BitContent) {
            return decodeToString(start, end)
        }
        return String(
            data,
            start,
            end - start,
            StandardCharsets.US_ASCII
        )
    }
}

@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = JvmByteArraySource(data)
