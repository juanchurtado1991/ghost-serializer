package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A JSON text is a single root value (RFC 8259): non-whitespace content after it is rejected on
 * every channel, matching kotlinx.serialization's "Expected EOF" behavior. Trailing whitespace is
 * still accepted.
 */
class GhostTrailingContentTest {

    @Test
    fun bufferedSourceRejectsTrailingContent() {
        TRAILING_CONTENT_INPUTS.forEach { input ->
            assertFailsWith<GhostJsonException>(message = "Accepted trailing content: $input") {
                Ghost.deserialize<Map<String, Int>>(Buffer().writeUtf8(string = input))
            }
        }
    }

    @Test
    fun bytesRejectTrailingContent() {
        TRAILING_CONTENT_INPUTS.forEach { input ->
            assertFailsWith<GhostJsonException>(message = "Accepted trailing content: $input") {
                Ghost.deserialize<Map<String, Int>>(input.encodeToByteArray())
            }
        }
    }

    @Test
    fun streamingRejectsTrailingContent() {
        TRAILING_CONTENT_INPUTS.forEach { input ->
            assertFailsWith<GhostJsonException>(message = "Accepted trailing content: $input") {
                Ghost.deserializeStreaming<Map<String, Int>>(Buffer().writeUtf8(string = input))
            }
        }
    }

    @Test
    fun stringRejectsTrailingContent() {
        TRAILING_CONTENT_INPUTS.forEach { input ->
            assertFailsWith<GhostJsonException>(message = "Accepted trailing content: $input") {
                Ghost.deserialize<Map<String, Int>>(input)
            }
        }
    }

    @Test
    fun trailingWhitespaceIsAcceptedOnEveryChannel() {
        val expected = mapOf(EXPECTED_KEY to EXPECTED_VALUE)

        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<Map<String, Int>>(TRAILING_WHITESPACE_INPUT)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<Map<String, Int>>(TRAILING_WHITESPACE_INPUT.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<Map<String, Int>>(Buffer().writeUtf8(string = TRAILING_WHITESPACE_INPUT))
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<Map<String, Int>>(Buffer().writeUtf8(string = TRAILING_WHITESPACE_INPUT))
        )
    }

    companion object {
        private const val EXPECTED_KEY = "k"
        private const val EXPECTED_VALUE = 1
        private const val TRAILING_WHITESPACE_INPUT = "{\"k\":1} \n\t\r "
        private val TRAILING_CONTENT_INPUTS = listOf(
            "{\"k\":1} extra",
            "{\"k\":1}{}",
            "{\"k\":1}}",
            "{\"k\":1},"
        )
    }
}
