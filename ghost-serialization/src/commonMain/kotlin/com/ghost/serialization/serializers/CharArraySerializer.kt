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

/** Serializer for primitive [CharArray], delegating to the [ListSerializer] of its element type on every channel. */
object CharArraySerializer : AbstractGhostSerializer<CharArray>() {

    private val elements = ListSerializer(itemSerializer = CharSerializer)

    override val typeName: String = TOK.TYPE_NAME_CHAR_ARRAY

    override fun deserialize(
        reader: GhostJsonReader
    ): CharArray = elements.deserialize(reader = reader).toCharArray()

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): CharArray = elements.deserialize(reader = reader).toCharArray()

    override fun deserialize(
        reader: GhostJsonStringReader
    ): CharArray = elements.deserialize(reader = reader).toCharArray()

    override fun serialize(
        writer: GhostJsonWriter,
        value: CharArray
    ) {
        elements.serialize(
            writer = writer,
            value = value.asList()
        )
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: CharArray
    ) {
        elements.serialize(
            writer = writer,
            value = value.asList()
        )
    }
}
