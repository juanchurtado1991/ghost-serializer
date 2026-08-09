package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.parser.streaming.GhostJsonReader
import okio.Buffer
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals


/**
 * Asserts semantic equality after serialize → deserialize on all three public channels.
 *
 * Streaming decode uses an Okio `Buffer` source ([GhostJsonReader] path). Streaming encode
 * is checked separately because Buffer read positions are not rewound after Ghost drains the sink.
 */
internal inline fun <reified T : Any> assertTriChannelRoundTrip(expected: T, value: T = expected) {
    val bytes = Ghost.encodeToBytes(value)
    assertEquals(expected, Ghost.deserialize<T>(bytes), "bytes channel round-trip failed")

    val json = Ghost.encodeToString(value)
    assertEquals(expected, Ghost.deserialize<T>(json), "string channel round-trip failed")

    val viaStreaming = Ghost.deserializeStreaming<T>(Buffer().write(bytes))
    assertEquals(expected, viaStreaming, "streaming channel round-trip failed")

    val streamingSink = Buffer()
    Ghost.serialize(streamingSink, value)
    assertContentEquals(bytes, streamingSink.readByteArray(), "streaming encode channel failed")
}
