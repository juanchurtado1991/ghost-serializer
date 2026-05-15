package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

// External class that WE CANNOT ANNOTATE (e.g. Joda-Time)
data class ExternalDate(val timestamp: Long)

// Manual serializer for the external class
object ExternalDateSerializer : GhostSerializer<ExternalDate> {
    override val typeName: String = "ExternalDate"

    override fun serialize(writer: GhostJsonWriter, value: ExternalDate) {
        writer.value(value.timestamp.toString())
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ExternalDate) {
        writer.value(value.timestamp.toString())
    }

    override fun deserialize(reader: GhostJsonReader): ExternalDate {
        return ExternalDate(reader.nextString().toLong())
    }
}

@GhostSerialization
data class ModelWithExternal(
    val id: Int,
    val date: ExternalDate
)
