package com.ghost.serialization

import com.ghost.serialization.writer.common.*
import com.ghost.serialization.writer.strings.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.writer.bytes.*
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class NextCharTest {

    private val quotedZ = "\"Z\""

    @Test
    fun nextCharFastPathSingleAsciiOnFlatReader() {
        val reader = GhostJsonFlatReader(quotedZ.encodeToByteArray())
        assertEquals('Z', reader.nextChar())
    }

    @Test
    fun nextCharFastPathSingleAsciiOnStreamingReader() {
        val reader = GhostJsonReader(Buffer().writeUtf8(quotedZ))
        assertEquals('Z', reader.nextChar())
    }

    @Test
    fun nextCharFastPathSingleAsciiOnStringReader() {
        val reader = GhostJsonStringReader(quotedZ)
        assertEquals('Z', reader.nextChar())
    }
}
