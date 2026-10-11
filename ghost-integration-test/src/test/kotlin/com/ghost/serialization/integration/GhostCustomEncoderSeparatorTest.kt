package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.CustomEncoderMiddleModel
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/** A custom encoder's value is followed by a comma on every writer channel, so the output stays valid JSON. */
class GhostCustomEncoderSeparatorTest {

    @Test
    fun encodeToStringSeparatesCustomValueFromNextField() {
        assertEquals(
            expected = EXPECTED_JSON,
            actual = Ghost.encodeToString(value = MODEL)
        )
    }

    @Test
    fun encodeToBytesSeparatesCustomValueFromNextField() {
        assertEquals(
            expected = EXPECTED_JSON,
            actual = Ghost.encodeToBytes(value = MODEL).decodeToString()
        )
    }

    @Test
    fun encodeToSinkSeparatesCustomValueFromNextField() {
        val sink = Buffer()

        Ghost.encodeToSink(sink = sink, value = MODEL)

        assertEquals(
            expected = EXPECTED_JSON,
            actual = sink.readUtf8()
        )
    }

    @Test
    fun stringOutputRoundTrips() {
        assertEquals(
            expected = MODEL,
            actual = Ghost.deserialize<CustomEncoderMiddleModel>(json = Ghost.encodeToString(value = MODEL))
        )
    }

    private companion object {
        val MODEL = CustomEncoderMiddleModel(id = 1L, createdAt = 20231015L, name = "ghost")
        const val EXPECTED_JSON = """{"id":1,"createdAt":"2023-10-15","name":"ghost"}"""
    }
}
