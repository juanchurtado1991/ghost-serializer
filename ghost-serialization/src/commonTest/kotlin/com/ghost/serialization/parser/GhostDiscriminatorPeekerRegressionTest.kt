@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.peekStringField
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.peekStringField
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
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "complex",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun flatReaderPeekDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "loggedIn",
            actual = reader.peekDiscriminator(key = "pageType")
        )
    }

    @Test
    fun streamingReaderPeekDiscriminatorAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"complex"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "complex",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun streamingReaderPeekDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "loggedIn",
            actual = reader.peekDiscriminator(key = "pageType")
        )
    }

    @Test
    fun stringReaderPeekStringFieldAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"complex"}"""
        val reader = GhostJsonStringReader(rawData = json)
        assertEquals(
            expected = "complex",
            actual = reader.peekStringField(name = "type")
        )
    }

    @Test
    fun stringReaderPeekStringFieldAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = GhostJsonStringReader(rawData = json)
        assertEquals(
            expected = "loggedIn",
            actual = reader.peekStringField(name = "pageType")
        )
    }

    @Test
    fun peekDiscriminatorStillReturnsNullWhenKeyMissing() {
        val json = """{"devices":[{"id":"hub-1"}],"name":"Living"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertNull(actual = reader.peekDiscriminator(key = "pageType"))
    }

    @Test
    fun stringReaderPeekStringFieldAfterBeginObject() {
        val json = """{"type":"USER","id":1}"""
        val reader = GhostJsonStringReader(rawData = json)
        reader.beginObject()
        assertEquals(
            expected = "USER",
            actual = reader.peekStringField(name = "type")
        )
    }
}
