@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextFloat
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Float] type.
 */
object FloatSerializer : GhostSerializer<Float> {
    override val typeName: String get() = C.TYPE_NAME_FLOAT

    override fun serialize(writer: GhostJsonWriter, value: Float) {
        writer.value(value)
    }


    override fun serialize(writer: GhostJsonStringWriter, value: Float) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Float = reader.nextFloat()

    override fun deserialize(reader: GhostJsonFlatReader): Float = reader.nextFloat()

    override fun deserialize(reader: GhostJsonStringReader): Float = reader.nextFloat()
}
