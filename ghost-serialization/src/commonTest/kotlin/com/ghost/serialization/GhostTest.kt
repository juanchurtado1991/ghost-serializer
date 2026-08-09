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
        Ghost.encodeAndDiscard(42)
    }

    @Test
    fun decodeFromBytesWithKClass() {
        val bytes = "123".encodeToByteArray()
        assertEquals(123, Ghost.decodeFromBytes(bytes, Int::class))
    }

    @Test
    fun encodeToSinkWithKClass() {
        val sink = Buffer()
        Ghost.encodeToSink(sink, 7, Int::class)
        assertEquals("7", sink.readUtf8())
    }
}
