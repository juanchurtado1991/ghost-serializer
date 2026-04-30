@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalUseReader
import com.ghost.serialization.parser.GhostJsonReader
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

internal actual suspend fun createReader(channel: ByteReadChannel): GhostJsonReader {
    val bytes = channel.readRemaining().readByteArray()
    return ghostInternalUseReader(bytes) { it }
}
