package com.ghost.serialization

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Decoded `Set`s keep the JSON array order on every channel, like kotlinx.serialization's `LinkedHashSet`. */
class GhostSetOrderTest {

    private val elements = (ELEMENT_COUNT downTo 1).map { "item$it" }
    private val json = elements.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

    @Test
    fun bytesChannelKeepsArrayOrder() {
        assertEquals(
            expected = elements,
            actual = Ghost.deserialize<Set<String>>(bytes = json.encodeToByteArray()).toList()
        )
    }

    @Test
    fun stringChannelKeepsArrayOrder() {
        assertEquals(
            expected = elements,
            actual = Ghost.deserialize<Set<String>>(json = json).toList()
        )
    }

    @Test
    fun streamingChannelKeepsArrayOrder() {
        assertEquals(
            expected = elements,
            actual = Ghost.deserializeStreaming<Set<String>>(source = Buffer().writeUtf8(json)).toList()
        )
    }

    private companion object {
        const val ELEMENT_COUNT = 40
    }
}
