@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * A generic empty message that you can re-use to avoid defining duplicated
 * empty messages in your APIs.
 */
object ProtoEmpty

/**
 * Serializer for [ProtoEmpty].
 */
object ProtoEmptySerializer : GhostSerializer<ProtoEmpty> {
    override val typeName: String get() = C.WKT_EMPTY_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoEmpty) {
        writer.beginObject().endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoEmpty {
        reader.beginObject()
        while (reader.peekNextToken() != C.CLOSE_OBJ_INT) {
            reader.nextString()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()
        return ProtoEmpty
    }
}
