package com.ghost.serialization.ktor

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import okio.BufferedSource
import okio.buffer
import okio.source

internal actual suspend fun ByteReadChannel.toBufferedSource(): BufferedSource =
    toInputStream().source().buffer()
