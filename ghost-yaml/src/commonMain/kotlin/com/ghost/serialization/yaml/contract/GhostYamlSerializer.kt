package com.ghost.serialization.yaml.contract

import com.ghost.serialization.yaml.writer.GhostYamlWriter
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import okio.BufferedSink
import okio.BufferedSource

interface GhostYamlSerializer<T> {
    fun serialize(sink: BufferedSink, value: T) {
        val writer = GhostYamlWriter(sink)
        serialize(writer, value)
        writer.flush()
    }
    fun serialize(writer: GhostYamlWriter, value: T)
    fun serialize(writer: GhostYamlFlatWriter, value: T)
    fun deserialize(source: BufferedSource): T {
        val reader = GhostYamlFlatReader(source.readByteArray())
        return deserialize(reader)
    }
    fun deserialize(reader: GhostYamlFlatReader): T
}
