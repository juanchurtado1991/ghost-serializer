package com.ghost.serialization.serializers

import okio.BufferedSink
import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import okio.BufferedSource
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.nextDouble

object StringSerializer : GhostSerializer<String> {
    override fun serialize(writer: GhostJsonWriter, value: String) {
        writer.value(value)
    }
    override fun deserialize(reader: GhostJsonReader): String = reader.nextString()
}

object IntSerializer : GhostSerializer<Int> {
    override fun serialize(writer: GhostJsonWriter, value: Int) {
        writer.value(value.toLong())
    }
    override fun deserialize(reader: GhostJsonReader): Int = reader.nextInt()
}

object LongSerializer : GhostSerializer<Long> {
    override fun serialize(writer: GhostJsonWriter, value: Long) {
        writer.value(value)
    }
    override fun deserialize(reader: GhostJsonReader): Long = reader.nextLong()
}

object BooleanSerializer : GhostSerializer<Boolean> {
    override fun serialize(writer: GhostJsonWriter, value: Boolean) {
        writer.value(value)
    }
    override fun deserialize(reader: GhostJsonReader): Boolean = reader.nextBoolean()
}

object DoubleSerializer : GhostSerializer<Double> {
    override fun serialize(writer: GhostJsonWriter, value: Double) {
        writer.value(value)
    }
    override fun deserialize(reader: GhostJsonReader): Double = reader.nextDouble()
}
