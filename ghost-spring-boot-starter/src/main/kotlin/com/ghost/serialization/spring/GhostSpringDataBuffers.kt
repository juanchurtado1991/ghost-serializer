package com.ghost.serialization.spring

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalNeedsTranscoding
import com.ghost.serialization.ghostInternalTranscodeToUtf8
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import java.nio.charset.Charset

/** Copies the readable bytes out of this buffer and releases it, as every reactive decoder must. */
internal fun DataBuffer.consumeToByteArray(): ByteArray {
    try {
        val bytes = ByteArray(readableByteCount())
        read(bytes)
        return bytes
    } finally {
        DataBufferUtils.release(this)
    }
}

/** These bytes as UTF-8: re-encoded when the message declared another [charset], untouched otherwise. */
@OptIn(InternalGhostApi::class)
internal fun ByteArray.asUtf8From(
    charset: Charset?
): ByteArray = if (charset != null && ghostInternalNeedsTranscoding(charset = charset)) {
    ghostInternalTranscodeToUtf8(
        bytes = this,
        length = size,
        charset = charset
    )
} else {
    this
}
