@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.types

import com.ghost.serialization.writer.common.*
import com.ghost.serialization.writer.strings.*
import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.writer.bytes.*
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter

/** Built-in serializer for [RawJson] opaque JSON passthrough. */
object RawJsonSerializer : GhostSerializer<RawJson> {
    override val typeName: String = "RawJson"

    override fun deserialize(reader: GhostJsonReader): RawJson =
        reader.captureRawJson()

    override fun deserialize(reader: GhostJsonFlatReader): RawJson =
        reader.captureRawJson()

    override fun deserialize(reader: GhostJsonStringReader): RawJson =
        reader.captureRawJson()

    override fun serialize(writer: GhostJsonWriter, value: RawJson) {
        writer.rawValue(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: RawJson) {
        writer.rawValue(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: RawJson) {
        writer.rawValue(value)
    }

    override fun warmUp() {
        val sample = """{"warm":true}""".encodeToByteArray()
        try {
            deserialize(GhostJsonReader(sample))
        } catch (_: Exception) {
        }
        try {
            deserialize(GhostJsonFlatReader(sample))
        } catch (_: Exception) {
        }
    }
}
