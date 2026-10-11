package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * String escapes follow RFC 8259 on every channel: only `\" \\ \/ \b \f \n \r \t \uXXXX` are
 * valid (an unknown escape used to drop the backslash silently), a lone surrogate escape of either
 * kind is rejected, and a string cut off by the end of the input fails with [GhostJsonException]
 * (the streaming channel used to throw `IndexOutOfBoundsException`).
 */
class GhostStringEscapeDecodingTest {

    @Test
    fun invalidEscapesAreRejected() {
        INVALID_ESCAPES.forEach { json ->
            assertEveryChannelFails(json = json)
        }
    }

    @Test
    fun loneSurrogateEscapesAreRejected() {
        LONE_SURROGATES.forEach { json ->
            assertEveryChannelFails(json = json)
        }
    }

    @Test
    fun truncatedStringsAreRejected() {
        TRUNCATED_STRINGS.forEach { json ->
            assertEveryChannelFails(json = json)
        }
    }

    @Test
    fun validEscapesDecodeOnEveryChannel() {
        val expected = listOf(VALID_ESCAPES_DECODED)

        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<List<String>>(VALID_ESCAPES_JSON)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<List<String>>(VALID_ESCAPES_JSON.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<List<String>>(Buffer().writeUtf8(string = VALID_ESCAPES_JSON))
        )
    }

    private fun assertEveryChannelFails(
        json: String
    ) {
        assertFailsWith<GhostJsonException>(message = "String channel accepted $json") {
            Ghost.deserialize<List<String>>(json)
        }
        assertFailsWith<GhostJsonException>(message = "Bytes channel accepted $json") {
            Ghost.deserialize<List<String>>(json.encodeToByteArray())
        }
        assertFailsWith<GhostJsonException>(message = "Streaming channel accepted $json") {
            Ghost.deserializeStreaming<List<String>>(Buffer().writeUtf8(string = json))
        }
    }

    companion object {
        private const val VALID_ESCAPES_DECODED = "\" \\ / \b \u000c \n \r \t é 😀"
        private const val VALID_ESCAPES_JSON = """["\" \\ \/ \b \f \n \r \t é 😀"]"""
        private val INVALID_ESCAPES = listOf("""["b\,n"]""", """["\+"]""", """["a\c"]""", """["\x41"]""", """["\'"]""")
        private val LONE_SURROGATES = listOf("""["\uD83D"]""", """["a\uD83Dx"]""", """["\uD83DA"]""", """["\uDE00"]""")
        private val TRUNCATED_STRINGS = listOf("""["abc""", """["a","b","]""", """["a\""", """["a\u12""")
    }
}
