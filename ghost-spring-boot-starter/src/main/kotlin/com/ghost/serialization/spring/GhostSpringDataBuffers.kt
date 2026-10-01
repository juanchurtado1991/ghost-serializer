package com.ghost.serialization.spring

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils

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
