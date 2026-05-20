package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import java.nio.charset.StandardCharsets

/**
 * Android-optimized [GhostSource] that overrides [decodeJsonStringRange]
 * to use [StandardCharsets.ISO_8859_1] for known 7-bit content.
 * ISO_8859_1 does a direct byte copy without ASCII validation — safe here
 * because [isKnown7BitContent] already guarantees all bytes are < 128.
 */
@InternalGhostApi
class AndroidByteArraySource(
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
            StandardCharsets.ISO_8859_1
        )
    }
}

@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = AndroidByteArraySource(data)
