@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.serializers

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * Serializer implementation for standard Kotlin [List] collections.
 */
class ListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>
) : GhostSerializer<List<T>> {

    override val typeName: String
        get() = "${C.TYPE_NAME_LIST_PREFIX}${itemSerializer.typeName}${C.TYPE_NAME_GENERIC_SUFFIX}"

    override fun serialize(
        writer: GhostJsonWriter,
        value: List<T>
    ) {
        writer.beginArray()
        val size = value.size
        if (size == 0) {
            writer.endArray()
            return
        }
        if (value is RandomAccess) {
            for (i in 0 until size) {
                itemSerializer.serialize(writer, value[i])
            }
        } else {
            for (item in value) {
                itemSerializer.serialize(writer, item)
            }
        }
        writer.endArray()
    }

    override fun serialize(
        writer: GhostJsonStringWriter,
        value: List<T>
    ) {
        writer.beginArray()
        val size = value.size
        if (size == 0) {
            writer.endArray()
            return
        }
        if (value is RandomAccess) {
            for (i in 0 until size) {
                itemSerializer.serialize(writer, value[i])
            }
        } else {
            for (item in value) {
                itemSerializer.serialize(writer, item)
            }
        }
        writer.endArray()
    }

    override fun deserialize(
        reader: GhostJsonReader
    ): List<T> {
        return if (itemSerializer.isResilient) {
            reader.readList {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull()
        } else {
            reader.readList {
                itemSerializer.deserialize(reader)
            }
        }
    }

    override fun deserialize(
        reader: GhostJsonFlatReader
    ): List<T> {
        return if (itemSerializer.isResilient) {
            reader.readList {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull()
        } else {
            reader.readList {
                itemSerializer.deserialize(reader)
            }
        }
    }

    override fun deserialize(
        reader: GhostJsonStringReader
    ): List<T> {
        return if (itemSerializer.isResilient) {
            reader.readList {
                reader.decodeResilient {
                    itemSerializer.deserialize(reader)
                }
            }.filterNotNull()
        } else {
            reader.readList {
                itemSerializer.deserialize(reader)
            }
        }
    }
}
