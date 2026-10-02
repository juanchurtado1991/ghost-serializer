@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.types

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.captureRawJson
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.captureRawJson
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.captureRawJson
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Built-in serializer for [RawJson] opaque JSON passthrough. */
object RawJsonSerializer : AbstractGhostSerializer<RawJson>() {
    override val typeName: String = TOK.TYPE_NAME_RAW_JSON

    override fun deserialize(reader: GhostJsonReader): RawJson =
        reader.captureRawJson()

    override fun deserialize(reader: GhostJsonFlatReader): RawJson =
        reader.captureRawJson()

    override fun deserialize(reader: GhostJsonStringReader): RawJson =
        reader.captureRawJson()

    override fun serialize(writer: GhostJsonWriter, value: RawJson) {
        writer.rawValue(raw = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: RawJson) {
        writer.rawValue(raw = value)
    }

    override fun warmUp() {
        val sample = TOK.WARM_RAW_JSON_PAYLOAD.encodeToByteArray()
        try {
            deserialize(reader = GhostJsonReader(bytes = sample))
        } catch (_: Exception) {
        }
        try {
            deserialize(reader = GhostJsonFlatReader(rawData = sample))
        } catch (_: Exception) {
        }
    }
}
