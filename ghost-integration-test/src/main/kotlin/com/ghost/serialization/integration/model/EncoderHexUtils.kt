@file:Suppress("unused")

package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.consumeNull
import com.ghost.serialization.parser.strings.isNextNullValue
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter


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
