package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `@GhostName` values that are not identifiers round-trip on every channel; names containing
 * characters JSON must escape (`"`, `\`, control characters) are written escaped, so the output
 * stays valid JSON.
 */
class GhostWireNameEdgeCaseTest {

    @Test
    fun escapedWireNamesProduceValidJsonOnEveryChannel() {
        val model = EscapedWireNamesModel(
            quoted = 1,
            backslash = 2,
            newline = 3,
            control = 4,
            dollar = 5,
            percent = 6
        )
        val json = Ghost.encodeToString(value = model)

        assertEquals(
            expected = ESCAPED_EXPECTED_JSON,
            actual = json
        )
        assertEquals(
            expected = json,
            actual = Ghost.encodeToBytes(value = model).decodeToString()
        )
        assertEquals(
            expected = ESCAPED_WIRE_NAMES,
            actual = Json.parseToJsonElement(string = json).jsonObject.keys.toList()
        )
        listOf(
            Ghost.deserialize<EscapedWireNamesModel>(json = json),
            Ghost.deserialize<EscapedWireNamesModel>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<EscapedWireNamesModel>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = model,
                actual = decoded
            )
        }
    }

    @Test
    fun oddWireNamesRoundTripOnEveryChannel() {
        val model = OddWireNamesModel(
            dashed = "d",
            spaced = 1,
            accented = true,
            lowerId = 2,
            upperId = 3,
            dotted = 4,
            underscored = 5
        )
        val json = Ghost.encodeToString(value = model)

        assertEquals(
            expected = EXPECTED_JSON,
            actual = json
        )
        assertEquals(
            expected = json,
            actual = Ghost.encodeToBytes(value = model).decodeToString()
        )
        listOf(
            Ghost.deserialize<OddWireNamesModel>(json = json),
            Ghost.deserialize<OddWireNamesModel>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<OddWireNamesModel>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = model,
                actual = decoded
            )
        }
    }

    companion object {
        private const val ESCAPED_EXPECTED_JSON = "{\"a\\\"b\":1,\"back\\\\slash\":2,\"new\\nline\":3," +
            "\"\\u0001control\":4,\"dollar\${sign}\":5,\"percent%s\":6}"
        private val ESCAPED_WIRE_NAMES = listOf(
            "a\"b",
            "back\\slash",
            "new\nline",
            "\u0001control",
            "dollar\${sign}",
            "percent%s"
        )
        private const val EXPECTED_JSON = "{\"renamed-key\":\"d\",\"with space\":1,\"café\":true," +
            "\"id\":2,\"ID\":3,\"a.b\":4,\"a_b\":5}"
    }
}
