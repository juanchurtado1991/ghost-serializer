package com.ghost.serialization.core

import okio.BufferedSink
import okio.BufferedSource

interface GhostSerializer<T> {
    fun serialize(sink: BufferedSink, value: T) {
        val writer = GhostJsonWriter(sink)
        serialize(writer, value)
    }

    fun serialize(writer: GhostJsonWriter, value: T)
    
    fun deserialize(source: BufferedSource): T = deserialize(GhostJsonReader(source))

    fun deserialize(reader: GhostJsonReader): T
}