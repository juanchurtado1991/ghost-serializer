@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Serializer for primitive [ShortArray], delegating to the [ListSerializer] of its element type on every channel. */
object ShortArraySerializer : AbstractGhostSerializer<ShortArray>() {

    private val elements = ListSerializer(itemSerializer = ShortSerializer)

    override val typeName: String = TOK.TYPE_NAME_SHORT_ARRAY

    override fun deserialize(
        reader: GhostJsonReader
    ): ShortArray = elements.deserialize(reader = reader).toShortArray()

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): ShortArray = elements.deserialize(reader = reader).toShortArray()

    override fun deserialize(
        reader: GhostJsonStringReader
    ): ShortArray = elements.deserialize(reader = reader).toShortArray()

    override fun serialize(
        writer: GhostJsonWriter,
        value: ShortArray
    ) {
        elements.serialize(
            writer = writer,
            value = value.asList()
        )
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: ShortArray
    ) {
        elements.serialize(
            writer = writer,
            value = value.asList()
        )
    }
}
