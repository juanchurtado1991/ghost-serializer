package com.ghost.serialization.ktor

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import okio.Buffer
import okio.BufferedSource

// On iOS there is no InputStream bridge to Okio, so we read the
// channel bytes once and wrap them in an okio.Buffer (single allocation).
internal actual suspend fun ByteReadChannel.toBufferedSource(): BufferedSource {
    val bytes = readRemaining().readBytes()
    return Buffer().also { it.write(bytes) }
}
