package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.LargeFlattenedModel
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `@GhostFlatten` and `@GhostWrap` in classes above 40 properties (fragmented codegen) read and write the
 * same nested JSON as in small classes, on every reader and writer channel.
 */
class GhostLargeFlattenTest {

    @Test
    fun decodesNestedPathsAndFieldsAfterThem() {
        assertDecodedOnEveryChannel(
            json = FULL_JSON,
            expected = FULL_MODEL
        )
    }

    @Test
    fun absentAndNullParentsKeepDefaults() {
        assertDecodedOnEveryChannel(
            json = """{"id":"x","extra":null,"meta":{"author":null,"info":{"level":3}}}""",
            expected = LargeFlattenedModel(level = LEVEL, id = ID)
        )
    }

    @Test
    fun requiredFieldUnderNullParentIsMissing() {
        val json = """{"id":"x","meta":{"info":null}}"""

        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<LargeFlattenedModel>(json = json)
        }
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<LargeFlattenedModel>(bytes = json.encodeToByteArray())
        }
        assertFailsWith<GhostJsonException> {
            Ghost.deserializeStreaming<LargeFlattenedModel>(source = Buffer().writeUtf8(json))
        }
    }

    @Test
    fun encodesNestedPathsOnEveryChannel() {
        val sink = Buffer()
        Ghost.encodeToSink(sink = sink, value = FULL_MODEL)

        assertEquals(
            expected = FULL_JSON,
            actual = Ghost.encodeToString(value = FULL_MODEL)
        )
        assertEquals(
            expected = FULL_JSON,
            actual = Ghost.encodeToBytes(value = FULL_MODEL).decodeToString()
        )
        assertEquals(
            expected = FULL_JSON,
            actual = sink.readUtf8()
        )
    }

    private fun assertDecodedOnEveryChannel(
        json: String,
        expected: LargeFlattenedModel
    ) {
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<LargeFlattenedModel>(json = json)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<LargeFlattenedModel>(bytes = json.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<LargeFlattenedModel>(source = Buffer().writeUtf8(json))
        )
    }

    private companion object {
        const val ID = "x"
        const val LEVEL = 3

        val FULL_MODEL = LargeFlattenedModel(
            f1 = 1,
            f38 = 38,
            author = "ghost",
            level = LEVEL,
            tier = "pro",
            note = "n",
            f42 = 42,
            id = ID
        )

        val FULL_JSON = buildString {
            append("""{"f1":1,""")
            (2..37).forEach { index -> append(""""f$index":0,""") }
            append(""""f38":38,""")
            append(""""meta":{"author":"ghost","info":{"level":3,"tier":"pro"}},""")
            append(""""extra":{"note":"n"},"f42":42,"id":"x"}""")
        }
    }
}
