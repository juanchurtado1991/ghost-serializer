package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [GhostJsonFlatReader]-specific coverage for two scenario groups that were already tested
 * against sibling reader flavors (`GhostJsonStringReaderTest`, `GhostReaderSelectNameTest` via
 * `GhostJsonReader`) but never against the flat reader itself: strict-mode trailing-comma
 * rejection and `coerceBooleans`. Per-module Kover coverage only counts what this module's own
 * tests exercise on this exact class, so the sibling coverage doesn't carry over.
 */
class GhostFlatReaderTrailingCommaAndCoercionTest {

    private fun readerOf(json: String): GhostJsonFlatReader =
        GhostJsonFlatReader(json.encodeToByteArray())

    // ── Strict-mode trailing comma ──────────────────────────────────

    @Test
    fun strictModeRejectsTrailingCommaInObject() {
        val reader = readerOf("""{"a":1,}""")
        reader.strictMode = true
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.nextKey() // trailing comma before '}' must throw
        }
    }

    @Test
    fun strictModeRejectsTrailingCommaInArray() {
        // Real usage (see CollectionSerializers.kt): consumeArraySeparator() is only called
        // *before* an element once the list is non-empty -- never twice in a row.
        val reader = readerOf("""[1,2,]""")
        reader.strictMode = true
        reader.beginArray()
        val values = mutableListOf<Int>()
        assertFailsWith<GhostJsonException> {
            while (reader.hasNext()) {
                if (values.isNotEmpty()) reader.consumeArraySeparator()
                values.add(reader.nextInt())
            }
        }
    }

    @Test
    fun lenientModeRejectsTrailingCommaInObjectToo() {
        // Trailing-comma rejection isn't strict-mode-only -- nextKey()'s non-strict branch
        // (line ~377-382) throws ERR_TRAILING_COMMA unconditionally too.
        val reader = readerOf("""{"a":1,}""")
        reader.beginObject()
        assertFailsWith<GhostJsonException> {
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.nextInt()
            reader.nextKey()
        }
    }

    @Test
    fun lenientModeRejectsTrailingCommaInArrayToo() {
        val reader = readerOf("""[1,]""")
        reader.beginArray()
        assertFailsWith<GhostJsonException> {
            reader.nextInt()
            reader.consumeArraySeparator()
        }
    }

    @Test
    fun hasNextRejectsTrailingCommaInArray() {
        val reader = readerOf("""[1,]""")
        reader.beginArray()
        assertTrue(reader.hasNext())
        reader.nextInt()
        assertFailsWith<GhostJsonException> { reader.hasNext() }
    }

    @Test
    fun strictModeHasNextInteractsWithConsumeArraySeparator() {
        // hasNext() and consumeArraySeparator() share the same per-depth "comma already
        // consumed" bit. Real usage (CollectionSerializers.kt): hasNext() drives the loop and
        // consumes the separator itself when required=true from the prior iteration;
        // consumeArraySeparator() then just has to honor that without re-consuming or
        // re-requiring it.
        val reader = readerOf("""[1,2,3]""")
        reader.strictMode = true
        reader.beginArray()
        val values = mutableListOf<Int>()
        while (reader.hasNext()) {
            if (values.isNotEmpty()) reader.consumeArraySeparator()
            values.add(reader.nextInt())
        }
        reader.endArray()
        assertEquals(listOf(1, 2, 3), values)
    }

    // ── coerceBooleans ───────────────────────────────────────────────

    @Test
    fun coercesIntOneToBooleanTrue() {
        val reader = readerOf("""{"v":1}""")
        reader.coerceBooleans = true
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.nextBoolean())
    }

    @Test
    fun coercesIntZeroToBooleanFalse() {
        val reader = readerOf("""{"v":0}""")
        reader.coerceBooleans = true
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.nextBoolean())
    }

    @Test
    fun coercesQuotedTrueStringToBoolean() {
        val reader = readerOf("""{"v":"true"}""")
        reader.coerceBooleans = true
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.nextBoolean())
    }

    @Test
    fun coercesQuotedFalseStringToBoolean() {
        val reader = readerOf("""{"v":"false"}""")
        reader.coerceBooleans = true
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.nextBoolean())
    }

    @Test
    fun rejectsUnquotedIntBooleanWithoutCoercion() {
        val reader = readerOf("""{"v":1}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextBoolean() }
    }

    @Test
    fun coerceBooleansRejectsUnrecognizedQuotedString() {
        val reader = readerOf("""{"v":"maybe"}""")
        reader.coerceBooleans = true
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextBoolean() }
    }

    // ── Depth limit via beginObject (existing test only covers beginArray) ─────

    @Test
    fun beginObjectRespectsMaxDepthLimit() {
        val deepJson = "{\"a\":".repeat(300) + "1" + "}".repeat(300)
        val reader = readerOf(deepJson)
        assertFailsWith<GhostJsonException> {
            repeat(300) {
                reader.beginObject()
                reader.nextKey()
                reader.consumeKeySeparator()
            }
        }
    }
}
