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

object ProtoDurationSerializer : AbstractGhostSerializer<ProtoDuration>() {
    override val typeName: String get() = PC.WKT_DURATION_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoDuration {
        return parseDuration(durationString = reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoDuration {
        return parseDuration(durationString = reader.nextString())
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoDuration {
        return parseDuration(durationString = reader.nextString())
    }

    override fun serialize(writer: GhostJsonWriter, value: ProtoDuration) {
        writer.value(text = formatDuration(duration = value))
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoDuration) {
        writer.value(text = formatDuration(duration = value))
    }
}
