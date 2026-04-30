@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ghostInternalUseSource
import com.ghost.serialization.parser.GhostJsonReader
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import okio.buffer
import okio.source

internal actual suspend fun createReader(channel: ByteReadChannel): GhostJsonReader {
    val source = channel.toInputStream().source().buffer()
    return ghostInternalUseSource(source) { it }
}
