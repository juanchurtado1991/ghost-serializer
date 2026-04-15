package com.ghost.serialization.core

import okio.BufferedSink
import okio.BufferedSource

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
