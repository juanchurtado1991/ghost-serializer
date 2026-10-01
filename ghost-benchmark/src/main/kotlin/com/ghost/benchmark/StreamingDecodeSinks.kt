package com.ghost.benchmark

import okio.Buffer

/** Reusable Okio / byte streams for streaming decode (payload fixed per suite). */
internal class StreamingDecodeSinks(private val rawBytes: ByteArray) {

    private val okioBuffer = Buffer()

    fun freshOkioSource(): Buffer {
        okioBuffer.clear()
        okioBuffer.write(rawBytes)
        return okioBuffer
    }
}
