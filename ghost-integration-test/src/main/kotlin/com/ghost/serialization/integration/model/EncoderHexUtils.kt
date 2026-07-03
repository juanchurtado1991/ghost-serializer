@file:Suppress("unused")

package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.consumeNull
import com.ghost.serialization.parser.isNextNullValue
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

object EncoderHexUtils {

    private const val HEX_VALUE_PREFIX = "HEX:"
    private const val NULLABLE_INT_SENTINEL = -1

    const val DECODE_NULLABLE_INT_FUNCTION = "decodeNullableInt"
    const val DECODE_HEX_FUNCTION = "decodeHex"
    const val ENCODE_HEX_FUNCTION = "encodeHex"

    fun decodeHex(reader: GhostJsonReader): String {
        val hex = reader.nextString()
        return "$HEX_VALUE_PREFIX$hex"
    }

    fun decodeHex(reader: GhostJsonStringReader): String {
        val hex = reader.nextString()
        return "$HEX_VALUE_PREFIX$hex"
    }

    fun encodeHex(writer: GhostJsonWriter, value: String) {
        writer.value(value.removePrefix(HEX_VALUE_PREFIX))
    }

    fun encodeHex(writer: GhostJsonFlatWriter, value: String) {
        writer.value(value.removePrefix(HEX_VALUE_PREFIX))
    }

    fun decodeNullableInt(reader: GhostJsonReader): Int? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return NULLABLE_INT_SENTINEL
        }
        return reader.nextInt()
    }

    fun decodeNullableInt(reader: GhostJsonStringReader): Int? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return NULLABLE_INT_SENTINEL
        }
        return reader.nextInt()
    }
}
