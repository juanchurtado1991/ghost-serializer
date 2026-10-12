package com.ghost.serialization

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Root `ShortArray` / `CharArray` resolve to built-in serializers on every channel. */
class GhostShortCharArrayTest {

    private val shorts = shortArrayOf(-32768, 0, 7, 32767)
    private val shortsJson = "[-32768,0,7,32767]"
    private val chars = charArrayOf('a', 'Z', '"', 'é')
    private val charsJson = "[\"a\",\"Z\",\"\\\"\",\"é\"]"

    @Test
    fun shortArrayRoundTripsOnAllChannels() {
        assertEquals(
            expected = shortsJson,
            actual = Ghost.serialize(value = shorts)
        )
        assertContentEquals(
            expected = shorts,
            actual = Ghost.deserialize<ShortArray>(json = shortsJson)
        )
        assertContentEquals(
            expected = shorts,
            actual = Ghost.deserialize<ShortArray>(bytes = shortsJson.encodeToByteArray())
        )
        assertContentEquals(
            expected = shorts,
            actual = Ghost.deserializeStreaming<ShortArray>(source = Buffer().writeUtf8(shortsJson))
        )
    }

    @Test
    fun charArrayRoundTripsOnAllChannels() {
        assertEquals(
            expected = charsJson,
            actual = Ghost.serialize(value = chars)
        )
        assertContentEquals(
            expected = chars,
            actual = Ghost.deserialize<CharArray>(json = charsJson)
        )
        assertContentEquals(
            expected = chars,
            actual = Ghost.deserialize<CharArray>(bytes = charsJson.encodeToByteArray())
        )
        assertContentEquals(
            expected = chars,
            actual = Ghost.deserializeStreaming<CharArray>(source = Buffer().writeUtf8(charsJson))
        )
    }

    @Test
    fun emptyArraysRoundTrip() {
        assertContentEquals(
            expected = ShortArray(0),
            actual = Ghost.deserialize<ShortArray>(json = "[]")
        )
        assertContentEquals(
            expected = CharArray(0),
            actual = Ghost.deserialize<CharArray>(bytes = "[]".encodeToByteArray())
        )
    }
}
