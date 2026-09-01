@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.parser.common.GhostJsonConstants as C
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Built-in serializer for Kotlin [Int] type.
 */
object IntSerializer : GhostSerializer<Int> {
    override val typeName: String get() = C.TYPE_NAME_INT

    override fun serialize(writer: GhostJsonWriter, value: Int) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Int) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Int {
        return reader.nextInt()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Int {
        return reader.nextInt()
    }

    override fun deserialize(reader: GhostJsonStringReader): Int {
        return reader.nextInt()
    }
}
