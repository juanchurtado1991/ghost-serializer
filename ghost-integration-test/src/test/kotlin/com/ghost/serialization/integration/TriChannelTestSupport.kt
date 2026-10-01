package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.parser.streaming.GhostJsonReader
import okio.Buffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals


/**
 * Streaming encode is checked separately from decode because Buffer read positions
 * aren't rewound after Ghost drains the sink.
 */
internal inline fun <reified T : Any> assertTriChannelRoundTrip(expected: T, value: T = expected) {
    val bytes = Ghost.encodeToBytes(value)
    assertEquals(expected = expected, actual = Ghost.deserialize<T>(bytes), message = "bytes channel round-trip failed")

    val json = Ghost.encodeToString(value)
    assertEquals(expected = expected, actual = Ghost.deserialize<T>(json), message = "string channel round-trip failed")

    val viaStreaming = Ghost.deserializeStreaming<T>(Buffer().write(bytes))
    assertEquals(expected = expected, actual = viaStreaming, message = "streaming channel round-trip failed")

    val streamingSink = Buffer()
    Ghost.serialize(streamingSink, value)
    assertContentEquals(
        expected = bytes,
        actual = streamingSink.readByteArray(),
        message = "streaming encode channel failed"
    )
}
