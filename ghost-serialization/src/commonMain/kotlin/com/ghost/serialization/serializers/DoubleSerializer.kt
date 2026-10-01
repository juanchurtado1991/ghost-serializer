@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

object DoubleSerializer : AbstractGhostSerializer<Double>() {
    override val typeName: String get() = TOK.TYPE_NAME_DOUBLE

    override fun deserialize(reader: GhostJsonReader): Double {
        return reader.nextDouble()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Double {
        return reader.nextDouble()
    }

    override fun deserialize(reader: GhostJsonStringReader): Double {
        return reader.nextDouble()
    }

    override fun serialize(writer: GhostJsonWriter, value: Double) {
        writer.value(number = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Double) {
        writer.value(number = value)
    }
}
