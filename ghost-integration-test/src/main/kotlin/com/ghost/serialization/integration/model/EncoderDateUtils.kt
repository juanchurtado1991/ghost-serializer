@file:Suppress("unused")

package com.ghost.serialization.integration.model

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter


object EncoderDateUtils {

    const val DECODE_DATE_FUNCTION_NAME = "decodeLegacyDate"
    const val ENCODE_DATE_FUNCTION_NAME = "encodeLegacyDate"

    fun decodeLegacyDate(reader: GhostJsonReader): Long {
        // Reads date in format "YYYY-MM-DD" (simplified for test)
        val s = reader.readQuotedString()
        return s.replace("-", "").toLong()
    }

    fun decodeLegacyDate(reader: GhostJsonStringReader): Long {
        return reader.nextString().replace("-", "").toLong()
    }

    fun encodeLegacyDate(writer: GhostJsonWriter, value: Long) {
        val stringValue = value.toString()
        val formatted = "${
            stringValue.take(4)
        }-${
            stringValue.substring(4, 6)
        }-${
            stringValue.substring(6, 8)
        }"
        writer.value(formatted)
    }
}
