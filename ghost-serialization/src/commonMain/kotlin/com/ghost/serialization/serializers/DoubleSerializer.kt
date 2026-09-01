@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Double] type.
 */
object DoubleSerializer : GhostSerializer<Double> {
    override val typeName: String get() = C.TYPE_NAME_DOUBLE

    override fun serialize(writer: GhostJsonWriter, value: Double) {
        writer.value(value)
    }


    override fun serialize(writer: GhostJsonStringWriter, value: Double) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Double {
        return reader.nextDouble()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Double {
        return reader.nextDouble()
    }

    override fun deserialize(reader: GhostJsonStringReader): Double {
        return reader.nextDouble()
    }
}
