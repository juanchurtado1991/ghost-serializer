package com.ghost.serialization.integration.model

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter


object ExternalDateSerializer : AbstractGhostSerializer<ExternalDate>() {
    const val TYPE_NAME = "ExternalDate"

    override val typeName: String = TYPE_NAME

    override fun deserialize(reader: GhostJsonReader): ExternalDate {
        return ExternalDate(timestamp = reader.nextString().toLong())
    }

    override fun serialize(writer: GhostJsonWriter, value: ExternalDate) {
        writer.value(value.timestamp.toString())
    }
}
