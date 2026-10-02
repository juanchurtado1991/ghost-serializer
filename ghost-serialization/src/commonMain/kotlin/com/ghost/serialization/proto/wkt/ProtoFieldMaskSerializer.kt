@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoFieldMaskSerializer : AbstractGhostSerializer<ProtoFieldMask>() {
    override val typeName: String get() = PC.WKT_FIELDMASK_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoFieldMask {
        return parseFieldMask(pathsText = reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoFieldMask {
        return parseFieldMask(pathsText = reader.nextString())
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoFieldMask {
        return parseFieldMask(pathsText = reader.nextString())
    }

    override fun serialize(writer: GhostJsonWriter, value: ProtoFieldMask) {
        writer.value(text = formatFieldMask(mask = value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoFieldMask) {
        writer.value(text = formatFieldMask(mask = value))
    }
}
