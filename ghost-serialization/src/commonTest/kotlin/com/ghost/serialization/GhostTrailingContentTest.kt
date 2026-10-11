package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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

    @Test
    fun streamingErrorAfterDiscardedPrefixHasReadableMessage() {
        val json = LARGE_ARRAY_PREFIX + MALFORMED_LAST_ELEMENT

        val error = assertFailsWith<GhostJsonException> {
            Ghost.deserializeStreaming<List<Map<String, Int>>>(Buffer().writeUtf8(string = json))
        }

        assertEquals(
            expected = LARGE_ARRAY_ERROR_PATH,
            actual = error.path
        )
        assertNotNull(actual = error.message)
    }

    @Test
    fun streamingTrailingContentIsDetectedAcrossSegmentBoundaries() {
        SEGMENT_BOUNDARY_PADDINGS.forEach { padding ->
            val json = "{\"k\":\"" + "a".repeat(n = padding) + "\"}"

            assertEquals(
                expected = padding,
                actual = Ghost.deserializeStreaming<Map<String, String>>(
                    Buffer().writeUtf8(string = json + LONG_WHITESPACE_TAIL)
                ).getValue(key = EXPECTED_KEY).length
            )
            assertFailsWith<GhostJsonException>(message = "Accepted trailing content at padding $padding") {
                Ghost.deserializeStreaming<Map<String, String>>(
                    Buffer().writeUtf8(string = json + LONG_WHITESPACE_TAIL + TRAILING_GARBAGE)
                )
            }
        }
    }

    @Test
    fun truncatedStreamingInputHasReadableMessage() {
        TRUNCATED_INPUTS.forEach { input ->
            val error = assertFailsWith<GhostJsonException>(message = "Accepted truncated input: $input") {
                Ghost.deserializeStreaming<Map<String, Int>>(Buffer().writeUtf8(string = input))
            }
            assertNotNull(actual = error.message)
        }
    }

    companion object {
        private const val EXPECTED_KEY = "k"
        private const val EXPECTED_VALUE = 1
        private const val LARGE_ARRAY_ELEMENTS = 3000
        private const val LARGE_ARRAY_ERROR_PATH = "$[3000].k"
        private const val MALFORMED_LAST_ELEMENT = ",{\"k\":}]"
        private const val TRAILING_GARBAGE = "x"
        private const val TRAILING_WHITESPACE_INPUT = "{\"k\":1} \n\t\r "
        private val LARGE_ARRAY_PREFIX = "[" + (0 until LARGE_ARRAY_ELEMENTS).joinToString(separator = ",") { "{\"k\":$it}" }
        private val LONG_WHITESPACE_TAIL = " ".repeat(n = 9000)
        private val SEGMENT_BOUNDARY_PADDINGS = listOf(8180, 8184, 8186, 8188, 8190, 8191, 8192, 16378)
        private val TRAILING_CONTENT_INPUTS = listOf(
            "{\"k\":1} extra",
            "{\"k\":1}{}",
            "{\"k\":1}}",
            "{\"k\":1},"
        )
        private val TRUNCATED_INPUTS = listOf(
            "",
            "  ",
            "{",
            "{\"k\":1 ",
            "{\"k\":1,",
            "{\"k\": "
        )
    }
}
