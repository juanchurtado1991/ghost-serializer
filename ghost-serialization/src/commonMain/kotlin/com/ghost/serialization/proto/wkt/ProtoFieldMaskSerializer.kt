@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Serializer for [ProtoFieldMask].
 */
object ProtoFieldMaskSerializer : GhostSerializer<ProtoFieldMask> {
    override val typeName: String get() = C.WKT_FIELDMASK_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun deserialize(reader: GhostJsonReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }
}
