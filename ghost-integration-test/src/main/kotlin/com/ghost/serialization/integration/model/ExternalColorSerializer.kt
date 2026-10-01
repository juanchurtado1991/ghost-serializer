@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration.model

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter


@InternalGhostApi
object ExternalColorSerializer : AbstractGhostSerializer<ExternalColor>() {
    override val typeName: String = "ExternalColor"

    override fun deserialize(reader: GhostJsonReader): ExternalColor {
        val hex = reader.nextString().removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return ExternalColor(r = r, g = g, b = b)
    }

    override fun serialize(writer: GhostJsonWriter, value: ExternalColor) {
        val hex = "#%02x%02x%02x".format(value.r, value.g, value.b)
        writer.value(hex)
    }
}
