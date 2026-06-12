@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.readQuotedString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import com.ghost.serialization.writer.GhostJsonWriter

/**
 * Built-in serializer for Kotlin [String] type.
 */
object StringSerializer : GhostSerializer<String> {
    override val typeName: String get() = "String"

    override fun serialize(writer: GhostJsonWriter, value: String) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: String) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: String) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): String {
        return reader.readQuotedString()
    }

    override fun deserialize(reader: GhostJsonFlatReader): String {
        return reader.readQuotedString()
    }

    override fun deserialize(reader: GhostJsonStringReader): String {
        return reader.readQuotedString()
    }
}

/**
 * Built-in serializer for Kotlin [Int] type.
 */
object IntSerializer : GhostSerializer<Int> {
    override val typeName: String get() = "Int"

    override fun serialize(writer: GhostJsonWriter, value: Int) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Int) {
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

/**
 * Built-in serializer for Kotlin [Long] type.
 */
object LongSerializer : GhostSerializer<Long> {
    override val typeName: String get() = "Long"

    override fun serialize(writer: GhostJsonWriter, value: Long) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Long) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Long {
        return reader.nextLong()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Long {
        return reader.nextLong()
    }

    override fun deserialize(reader: GhostJsonStringReader): Long {
        return reader.nextLong()
    }
}

/**
 * Built-in serializer for Kotlin [Double] type.
 */
object DoubleSerializer : GhostSerializer<Double> {
    override val typeName: String get() = "Double"

    override fun serialize(writer: GhostJsonWriter, value: Double) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Double) {
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

/**
 * Built-in serializer for Kotlin [Boolean] type.
 */
object BooleanSerializer : GhostSerializer<Boolean> {
    override val typeName: String get() = "Boolean"

    override fun serialize(writer: GhostJsonWriter, value: Boolean) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: Boolean) {
        writer.value(value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: Boolean) {
        writer.value(value)
    }

    override fun deserialize(reader: GhostJsonReader): Boolean {
        return reader.nextBoolean()
    }

    override fun deserialize(reader: GhostJsonFlatReader): Boolean {
        return reader.nextBoolean()
    }

    override fun deserialize(reader: GhostJsonStringReader): Boolean {
        return reader.nextBoolean()
    }
}
