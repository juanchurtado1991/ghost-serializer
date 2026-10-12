@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.peekStringField
import com.ghost.serialization.parser.streaming.selectNameAndConsume
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.peekStringField
import com.ghost.serialization.parser.strings.selectNameAndConsume
import com.ghost.serialization.parser.strings.skipValue
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/**
 * Object keys and discriminator values containing escapes (`\"`, `\\`, `A`) on the bytes,
 * streaming and string channels: an escaped key is matched by its decoded name instead of failing
 * as an unterminated string, and the discriminator peek decodes escapes, never mistakes a value
 * equal to the key name for the key, and skips nested objects.
 */
class EscapedKeyTriChannelTest {

    @Test
    fun discriminatorPeekReadsTheMemberNotAValueWithTheSameText() {
        PEEK_CASES.forEach { (json, expected) ->
            assertEquals(
                expected = expected,
                actual = GhostJsonFlatReader(rawData = json.encodeToByteArray()).peekStringField(name = TYPE_KEY),
                message = "bytes: $json"
            )
            assertEquals(
                expected = expected,
                actual = GhostJsonReader(okioSource = Buffer().writeUtf8(string = json)).peekStringField(name = TYPE_KEY),
                message = "streaming: $json"
            )
            assertEquals(
                expected = expected,
                actual = GhostJsonStringReader(rawData = json).peekStringField(name = TYPE_KEY),
                message = "string: $json"
            )
        }
    }

    @Test
    fun discriminatorPeekNeverThrowsOnMalformedStrings() {
        MALFORMED_PEEK_INPUTS.forEach { json ->
            GhostJsonFlatReader(rawData = json.encodeToByteArray()).peekStringField(name = TYPE_KEY)
            GhostJsonReader(okioSource = Buffer().writeUtf8(string = json)).peekStringField(name = TYPE_KEY)
            GhostJsonStringReader(rawData = json).peekStringField(name = TYPE_KEY)
        }
    }

    @Test
    fun escapedKeysAreMatchedByTheirDecodedName() {
        val options = JsonReaderOptions.of(BRIGHTNESS_KEY)

        val flat = GhostJsonFlatReader(rawData = ESCAPED_KEYS_JSON.encodeToByteArray())
        flat.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = flat.selectNameAndConsume(options = options)
        )
        flat.skipValue()
        assertEquals(
            expected = 0,
            actual = flat.selectNameAndConsume(options = options)
        )
        assertEquals(
            expected = BRIGHTNESS,
            actual = flat.nextInt()
        )

        val streaming = GhostJsonReader(okioSource = Buffer().writeUtf8(string = ESCAPED_KEYS_JSON))
        streaming.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = streaming.selectNameAndConsume(options = options)
        )
        streaming.skipValue()
        assertEquals(
            expected = 0,
            actual = streaming.selectNameAndConsume(options = options)
        )
        assertEquals(
            expected = BRIGHTNESS,
            actual = streaming.nextInt()
        )

        val string = GhostJsonStringReader(rawData = ESCAPED_KEYS_JSON)
        string.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = string.selectNameAndConsume(options = options)
        )
        string.skipValue()
        assertEquals(
            expected = 0,
            actual = string.selectNameAndConsume(options = options)
        )
        assertEquals(
            expected = BRIGHTNESS,
            actual = string.nextInt()
        )
    }

    companion object {
        private const val BRIGHTNESS = 80
        private const val BRIGHTNESS_KEY = "brightness"
        private const val ESCAPED_KEYS_JSON = """{"a\"b\\c":[1],"brightness":80}"""
        private const val TYPE_KEY = "type"
        private val PEEK_CASES = listOf(
            """{"label":"type","type":"Light"}""" to "Light",
            """{"type":"Light"}""" to "Light",
            """{"type":"Light"}""" to "Light",
            """{"meta":{"type":"Inner"},"type":"Outer"}""" to "Outer",
            """{"a\"":"type","type":"Light"}""" to "Light",
            """{"brightness":5}""" to null,
            """{"type":5}""" to null,
            """{"type":"""" to null
        )
        private val MALFORMED_PEEK_INPUTS = listOf(
            """{"type":"Li\uZZZZ"}""",
            """{"t\u00":"x"}""",
            """{"type":"\""""
        )
    }
}
