@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke coverage for documented Ghost entry points used by frameworks.
 */
class GhostTest {

    @Test
    fun encodeAndDiscardDoesNotThrow() {
        Ghost.encodeAndDiscard(value = 42)
    }

    @Test
    fun decodeFromBytesWithKClass() {
        val bytes = "123".encodeToByteArray()
        assertEquals(
            expected = 123,
            actual = Ghost.decodeFromBytes(bytes = bytes, clazz = Int::class)
        )
    }

    @Test
    fun encodeToSinkWithKClass() {
        val sink = Buffer()
        Ghost.encodeToSink(sink = sink, value = 7, clazz = Int::class)
        assertEquals(
            expected = "7",
            actual = sink.readUtf8()
        )
    }
}
