@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.proto.GhostProtoConstants as PC

object ProtoEmptySerializer : AbstractGhostSerializer<ProtoEmpty>() {
    override val typeName: String get() = PC.WKT_EMPTY_TYPE

    override fun deserialize(reader: GhostJsonReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }

    override fun deserialize(reader: GhostJsonStringReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != TOK.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }

    override fun serialize(writer: GhostJsonWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }
}
