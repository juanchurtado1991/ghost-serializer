@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
