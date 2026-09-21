package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.ByteArrayGhostSource
import java.nio.charset.StandardCharsets


/**
 * Overrides [decodeJsonStringRange] to use [StandardCharsets.ISO_8859_1] for known 7-bit
 * content: a direct byte copy with no ASCII validation, safe since isKnown7BitContent
 * already guarantees all bytes are < 128.
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
            StandardCharsets.ISO_8859_1
        )
    }
}

@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = JvmByteArraySource(data)
