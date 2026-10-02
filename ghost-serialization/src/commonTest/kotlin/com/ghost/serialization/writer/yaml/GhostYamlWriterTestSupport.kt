package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter

@OptIn(InternalGhostApi::class)
internal fun yamlWriterToString(
    block: (GhostYamlWriter) -> Any?
): String {
    val byteWriter = FlatByteArrayWriter()
    val writer = GhostYamlWriter(byteWriter)
    block(writer)
    return byteWriter.toStringUtf8()
}

@OptIn(InternalGhostApi::class)
internal fun yamlRoundTripScalar(
    key: String,
    write: (GhostYamlWriter) -> Unit
): Any? {
    val yaml = yamlWriterToString { w ->
        w.beginObject()
        w.name(key = key)
        write(w)
        w.endObject()
    }
    val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
    return (reader.readDocument() as Map<*, *>)[key]
}
