@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.consumeNull
import com.ghost.serialization.parser.strings.isNextNullValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Serializer for a nullable `T?` built on [delegate]: JSON `null` reads as `null` and `null` writes
 * as JSON `null`, anything else goes to [delegate]. Used for nullable type arguments resolved from a
 * `KType` (`List<Int?>`, `Map<String, User?>`).
 */
open class NullableSerializer<T : Any>(
    private val delegate: GhostSerializer<T>
) : AbstractGhostSerializer<T?>() {

    override val typeName: String
        get() = delegate.typeName + TOK.TYPE_NAME_NULLABLE_SUFFIX

    override fun deserialize(reader: GhostJsonReader): T? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return null
        }
        return delegate.deserialize(reader = reader)
    }

    override fun deserialize(reader: GhostJsonFlatReader): T? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return null
        }
        return delegate.deserialize(reader = reader)
    }

    override fun deserialize(reader: GhostJsonStringReader): T? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return null
        }
        return delegate.deserialize(reader = reader)
    }

    override fun serialize(writer: GhostJsonWriter, value: T?) {
        if (value == null) writer.nullValue() else delegate.serialize(writer = writer, value = value)
    }

    override fun serialize(writer: GhostJsonStringWriter, value: T?) {
        if (value == null) writer.nullValue() else delegate.serialize(writer = writer, value = value)
    }
}
