@file:Suppress("unused")

package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.consumeNull
import com.ghost.serialization.parser.isNextNullValue
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

object EncoderHexUtils {

    const val DECODE_NULLABLE_INT_FUNCTION = "decodeNullableInt"
    const val DECODE_HEX_FUNCTION = "decodeHex"
    const val ENCODE_HEX_FUNCTION = "encodeHex"

    fun decodeHex(reader: GhostJsonReader): String {
        val hex = reader.nextString()
        return "HEX:$hex"
    }

    fun encodeHex(writer: GhostJsonWriter, value: String) {
        writer.value(value.removePrefix("HEX:"))
    }

    fun encodeHex(writer: GhostJsonFlatWriter, value: String) {
        writer.value(value.removePrefix("HEX:"))
    }

    fun decodeNullableInt(reader: GhostJsonReader): Int? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            // Return a magic number instead
            // of null to test decoder logic
            return -1
        }
        return reader.nextInt()
    }
}