package com.ghost.serialization.yaml.contract

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter

interface GhostYamlSerializer<T> {
    fun deserialize(reader: GhostYamlFlatReader): T

    fun serialize(writer: GhostYamlWriter, value: T)
}
