package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Skipping an unknown field validates its strings like decoding them does, on every channel. */
class UnknownFieldSkipTest {

    private fun decodeAll(json: String): List<KeyDispatchModel> = listOf(
        Ghost.deserialize<KeyDispatchModel>(json = json),
        Ghost.deserialize<KeyDispatchModel>(bytes = json.encodeToByteArray()),
        Ghost.deserializeStreaming<KeyDispatchModel>(source = Buffer().writeUtf8(string = json)),
    )

    private fun assertRejectedEverywhere(json: String) {
        assertFailsWith<GhostJsonException>(message = json) { Ghost.deserialize<KeyDispatchModel>(json = json) }
        assertFailsWith<GhostJsonException>(message = json) {
            Ghost.deserialize<KeyDispatchModel>(bytes = json.encodeToByteArray())
        }
        assertFailsWith<GhostJsonException>(message = json) {
            Ghost.deserializeStreaming<KeyDispatchModel>(source = Buffer().writeUtf8(string = json))
        }
    }

    @Test
    fun invalidEscapeInSkippedValueIsRejected() {
        assertRejectedEverywhere(json = "{\"zz\":\"a\\qb\",\"a\":1}")
    }

    @Test
    fun invalidEscapeInSkippedKeyIsRejected() {
        assertRejectedEverywhere(json = "{\"zz\":{\"k\\7\":1},\"a\":1}")
    }

    @Test
    fun invalidEscapeInNestedSkippedArrayIsRejected() {
        assertRejectedEverywhere(json = "{\"zz\":[[\"x\",\"\\e\"]],\"a\":1}")
    }

    @Test
    fun everyValidEscapeInSkippedValueIsAccepted() {
        val json = "{\"zz\":\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u0041 \\ud83d\\ude00\",\"a\":1}"
        decodeAll(json).forEach { assertEquals(expected = 1, actual = it.p0) }
    }
}
