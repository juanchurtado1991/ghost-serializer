package com.ghost.benchmark

import okio.Buffer
import java.io.ByteArrayInputStream

/** Reusable Okio / byte streams for streaming decode (payload fixed per suite). */
internal class StreamingDecodeSinks(private val rawBytes: ByteArray) {

    private val okioBuffer = Buffer()

    fun freshByteStream(): ByteArrayInputStream = ByteArrayInputStream(rawBytes)

    fun freshOkioSource(): Buffer {
        okioBuffer.clear()
        okioBuffer.write(rawBytes)
        return okioBuffer
    }
}
