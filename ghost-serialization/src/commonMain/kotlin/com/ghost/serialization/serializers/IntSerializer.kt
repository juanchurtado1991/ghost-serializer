@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object IntSerializer : AbstractGhostSerializer<Int>() {
    override val typeName: String get() = TOK.TYPE_NAME_INT

    override fun deserialize(reader: GhostJsonReader): Int {
        return reader.nextInt()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Int {
        return reader.nextInt()
    }

    override fun deserialize(reader: GhostJsonStringReader): Int {
        return reader.nextInt()
    }

    override fun serialize(writer: GhostJsonWriter, value: Int) {
        writer.value(number = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Int) {
        writer.value(number = value)
    }
}
