package com.ghost.serialization.ktor

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import okio.Buffer
import okio.BufferedSource

// WasmJS has no InputStream/source() bridge to Okio.
// Read the channel once and wrap in a Buffer (single allocation, no double-copy).
internal actual suspend fun ByteReadChannel.toBufferedSource(): BufferedSource {
    val bytes = readRemaining().readByteArray()
    return Buffer().also { it.write(bytes) }
}
