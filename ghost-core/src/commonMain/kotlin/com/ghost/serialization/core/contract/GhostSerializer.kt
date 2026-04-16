package com.ghost.serialization.core.contract

import okio.BufferedSink
import okio.BufferedSource
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter

interface GhostSerializer<T> {
    fun serialize(sink: BufferedSink, value: T) {
        val writer = GhostJsonWriter(sink)
        serialize(writer, value)
    }

    fun serialize(writer: GhostJsonWriter, value: T)
    
    fun deserialize(source: BufferedSource): T = deserialize(GhostJsonReader(source))

    fun deserialize(reader: GhostJsonReader): T
}