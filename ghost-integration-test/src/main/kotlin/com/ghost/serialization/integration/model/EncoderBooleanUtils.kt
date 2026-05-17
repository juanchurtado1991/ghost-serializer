package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

object EncoderBooleanUtils {

    const val DECODE_BOOLEAN_FUNCTION_NAME = "decodeLegacyBool"
    const val ENCODE_BOOLEAN_FUNCTION_NAME = "encodeLegacyBool"

    fun decodeLegacyBool(reader: GhostJsonReader): Boolean {
        // Reads "Y" or "N"
        return reader.readQuotedString() == "Y"
    }

    // Support for GhostJsonFlatWriter (Flat path)
    fun encodeLegacyBool(writer: GhostJsonFlatWriter, value: Boolean) {
        writer.value(if (value) "Y" else "N")
    }

    // Support for GhostJsonWriter (Streaming path)
    fun encodeLegacyBool(writer: GhostJsonWriter, value: Boolean) {
        writer.value(if (value) "Y" else "N")
    }
}