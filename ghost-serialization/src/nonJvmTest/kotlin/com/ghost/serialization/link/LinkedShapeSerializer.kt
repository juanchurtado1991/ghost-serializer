package com.ghost.serialization.link

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter

object LinkedShapeSerializer : AbstractGhostSerializer<LinkedShape>() {
    override val typeName: String = "LinkedShape"

    override fun deserialize(
        reader: GhostJsonReader
    ): LinkedShape {
        reader.skipValue()
        return LinkedShape.Circle(radius = 0)
    }

    override fun deserialize(
        reader: GhostJsonStringReader
    ): LinkedShape {
        reader.skipValue()
        return LinkedShape.Circle(radius = 0)
    }

    override fun serialize(
        writer: GhostJsonWriter,
        value: LinkedShape
    ) = Unit
}
