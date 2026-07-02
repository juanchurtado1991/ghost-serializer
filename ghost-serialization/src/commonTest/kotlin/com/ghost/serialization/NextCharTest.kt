package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.nextChar
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
