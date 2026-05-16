@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration.model

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostDecoder
import com.ghost.serialization.annotations.GhostEncoder
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

@GhostSerialization
data class LegacyUser(
    val id: Long,
    @GhostDecoder(MyUtils::class, "decodeLegacyBool")
    @GhostEncoder(MyUtils::class, "encodeLegacyBool")
    val isActive: Boolean
)

@GhostSerialization
data class CustomDateUser(
    val id: Long,
    @GhostDecoder(MyUtils::class, "decodeLegacyDate")
    @GhostEncoder(MyUtils::class, "encodeLegacyDate")
    val createdAt: Long
)

object MyUtils {
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
    
    fun decodeLegacyDate(reader: GhostJsonReader): Long {
        // Reads date in format "YYYY-MM-DD" (simplified for test)
        val s = reader.readQuotedString()
        return s.replace("-", "").toLong()
    }
    
    // Support for GhostJsonFlatWriter (Flat path)
    fun encodeLegacyDate(writer: GhostJsonFlatWriter, value: Long) {
        val s = value.toString()
        val formatted = "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
        writer.value(formatted)
    }

    // Support for GhostJsonWriter (Streaming path)
    fun encodeLegacyDate(writer: GhostJsonWriter, value: Long) {
        val s = value.toString()
        val formatted = "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
        writer.value(formatted)
    }
}
