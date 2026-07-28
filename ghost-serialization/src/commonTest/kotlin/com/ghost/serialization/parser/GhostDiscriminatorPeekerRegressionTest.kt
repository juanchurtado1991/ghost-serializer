@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.peekStringField
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.peekStringField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader


/**
 * Regression tests for [GhostDiscriminatorPeeker] and string-channel [peekStringField].
 *
 * Nested objects/arrays before the discriminator must not cause peek to return null
 * (SmartThings ViperPage: `devices` array before `pageType`).
 */
class GhostDiscriminatorPeekerRegressionTest {

    @Test
    fun flatReaderPeekDiscriminatorAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"complex"}"""
        val reader = GhostJsonFlatReader(json.encodeToByteArray())
        assertEquals("complex", reader.peekDiscriminator())
    }

    @Test
    fun flatReaderPeekDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonFlatReader(json.encodeToByteArray())
        assertEquals("loggedIn", reader.peekDiscriminator("pageType"))
    }

    @Test
    fun streamingReaderPeekDiscriminatorAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"complex"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals("complex", reader.peekDiscriminator())
    }

    @Test
    fun streamingReaderPeekDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals("loggedIn", reader.peekDiscriminator("pageType"))
    }

    @Test
    fun stringReaderPeekStringFieldAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"complex"}"""
        val reader = GhostJsonStringReader(json)
        assertEquals("complex", reader.peekStringField("type"))
    }

    @Test
    fun stringReaderPeekStringFieldAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonStringReader(json)
        assertEquals("loggedIn", reader.peekStringField("pageType"))
    }

    @Test
    fun peekDiscriminatorStillReturnsNullWhenKeyMissing() {
        val json = """{"devices":[{"id":"hub-1"}],"name":"Living"}"""
        val reader = GhostJsonFlatReader(json.encodeToByteArray())
        assertNull(reader.peekDiscriminator("pageType"))
    }

    @Test
    fun stringReaderPeekStringFieldAfterBeginObject() {
        val json = """{"type":"USER","id":1}"""
        val reader = GhostJsonStringReader(json)
        reader.beginObject()
        assertEquals("USER", reader.peekStringField("type"))
    }
}
