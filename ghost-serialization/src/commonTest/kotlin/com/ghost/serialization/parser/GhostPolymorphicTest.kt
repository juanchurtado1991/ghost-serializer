@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class GhostPolymorphicTest {

    @Test
    fun testPeekDiscriminatorSimple() {
        val json = """{"type":"circle","radius":10}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "circle",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun testPeekDiscriminatorWithWhitespace() {
        val json = """  {  "type"  :  "rect"  , "width": 10 } """
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "rect",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun testPeekDiscriminatorNotFirst() {
        val json = """{"id":1, "type":"square", "size":5}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "square",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun testPeekDiscriminatorCustomKey() {
        val json = """{"klass":"admin","name":"Juan"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "admin",
            actual = reader.peekDiscriminator(key = "klass")
        )
    }

    @Test
    fun testPeekDiscriminatorNotFound() {
        val json = """{"id":1, "name":"Ghost"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertNull(actual = reader.peekDiscriminator())
    }

    @Test
    fun testPeekDiscriminatorAfterNestedObject() {
        val json = """{"meta":{"version":1}, "type":"complex"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "complex",
            actual = reader.peekDiscriminator()
        )
    }

    @Test
    fun testPeekDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"d1"}], "pageType":"loggedIn"}"""
        val reader = GhostJsonReader(json.encodeToByteArray())
        assertEquals(
            expected = "loggedIn",
            actual = reader.peekDiscriminator(key = "pageType")
        )
    }
}
