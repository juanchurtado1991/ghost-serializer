@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.types

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.captureRawJson
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.captureRawJson
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter


/** Built-in serializer for [RawJson] opaque JSON passthrough. */
object RawJsonSerializer : GhostSerializer<RawJson> {
    override val typeName: String = C.TYPE_NAME_RAW_JSON

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
        val sample = C.WARM_RAW_JSON_PAYLOAD.encodeToByteArray()
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
