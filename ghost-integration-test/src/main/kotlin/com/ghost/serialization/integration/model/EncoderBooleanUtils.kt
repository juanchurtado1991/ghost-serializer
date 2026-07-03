package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

object EncoderBooleanUtils {

    private const val LEGACY_TRUE = "Y"
    private const val LEGACY_FALSE = "N"

    const val DECODE_BOOLEAN_FUNCTION_NAME = "decodeLegacyBool"
    const val ENCODE_BOOLEAN_FUNCTION_NAME = "encodeLegacyBool"

    fun decodeLegacyBool(reader: GhostJsonReader): Boolean {
        return reader.readQuotedString() == LEGACY_TRUE
    }

    fun decodeLegacyBool(reader: GhostJsonStringReader): Boolean {
        return reader.nextString() == LEGACY_TRUE
    }

    fun encodeLegacyBool(writer: GhostJsonFlatWriter, value: Boolean) {
        writer.value(if (value) LEGACY_TRUE else LEGACY_FALSE)
    }

    fun encodeLegacyBool(writer: GhostJsonWriter, value: Boolean) {
        writer.value(if (value) LEGACY_TRUE else LEGACY_FALSE)
    }
}
