@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.*
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

object StringSerializer : GhostSerializer<String> {
    override val typeName: String get() = "String"

    override fun serialize(writer: GhostJsonWriter, value: String) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: String) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): String =
        reader.readQuotedString()

    override fun deserialize(reader: GhostJsonFlatReader): String =
        reader.readQuotedString()
}

object IntSerializer : GhostSerializer<Int> {
    override val typeName: String get() = "Int"

    override fun serialize(writer: GhostJsonWriter, value: Int) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Int) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Int =
        reader.nextInt()

    override fun deserialize(reader: GhostJsonFlatReader): Int =
        reader.nextInt()
}

object LongSerializer : GhostSerializer<Long> {
    override val typeName: String get() = "Long"

    override fun serialize(writer: GhostJsonWriter, value: Long) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Long =
        reader.nextLong()

    override fun deserialize(reader: GhostJsonFlatReader): Long =
        reader.nextLong()
}

object DoubleSerializer : GhostSerializer<Double> {
    override val typeName: String get() = "Double"

    override fun serialize(writer: GhostJsonWriter, value: Double) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Double) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Double =
        reader.nextDouble()

    override fun deserialize(reader: GhostJsonFlatReader): Double =
        reader.nextDouble()
}

object BooleanSerializer : GhostSerializer<Boolean> {
    override val typeName: String get() = "Boolean"

    override fun serialize(writer: GhostJsonWriter, value: Boolean) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Boolean) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Boolean =
        reader.nextBoolean()

    override fun deserialize(reader: GhostJsonFlatReader): Boolean =
        reader.nextBoolean()
}
