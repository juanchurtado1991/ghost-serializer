package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter

object ExternalDateSerializer : GhostSerializer<ExternalDate> {
    const val TYPE_NAME = "ExternalDate"

    override val typeName: String = TYPE_NAME

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