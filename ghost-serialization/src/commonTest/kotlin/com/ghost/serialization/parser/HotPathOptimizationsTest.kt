@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.StreamingGhostSource
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.selectNameAndConsume
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.selectNameAndConsume
import com.ghost.serialization.parser.strings.skipValue
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue


/**
 * Correctness guards for the hot-path shortcuts shared by every reader: optimistic
 * in-order field prediction, SWAR whitespace skipping, and SWAR string scanning with a
 * deferred pool hash. These are pure speed optimizations that fail silently (wrong field
 * index, truncated string) instead of loudly, so each test drives all four reader
 * flavours through the same payload and checks the results match.
 */
class HotPathOptimizationsTest {

    /** Reads a flat object of string values, keyed by the matched option name. */
    private class Driver(
        val name: String,
        val read: (json: String, options: Array<String>) -> Map<String, String>
    )

    private val drivers = listOf(
        Driver("flat") { json, names ->
            val reader = GhostJsonReader(json.encodeToByteArray())
            val options = JsonReaderOptions.of(*names)
            val result = LinkedHashMap<String, String>()
            reader.beginObject()
            while (true) {
                val index = reader.selectNameAndConsume(options)
                if (index == -1) break
                if (index == GhostJsonConstants.MATCH_NONE) {
                    reader.skipValue()
                    continue
                }
                result[names[index]] = reader.nextString()
            }
            reader.endObject()
            result
        },
        Driver("bytes") { json, names ->
            val reader = GhostJsonReader(json.encodeToByteArray())
            val options = JsonReaderOptions.of(*names)
            val result = LinkedHashMap<String, String>()
            reader.beginObject()
            while (true) {
                val index = reader.selectNameAndConsume(options)
                if (index == -1) break
                if (index == GhostJsonConstants.MATCH_NONE) {
                    reader.skipValue()
                    continue
                }
                result[names[index]] = reader.nextString()
            }
            reader.endObject()
            result
        },
        Driver("streaming") { json, names ->
            val reader = GhostJsonReader(Buffer().writeUtf8(json))
            val options = JsonReaderOptions.of(*names)
            val result = LinkedHashMap<String, String>()
            reader.beginObject()
            while (true) {
                val index = reader.selectNameAndConsume(options)
                if (index == -1) break
                if (index == GhostJsonConstants.MATCH_NONE) {
                    reader.skipValue()
                    continue
                }
                result[names[index]] = reader.nextString()
            }
            reader.endObject()
            result
        },
        Driver("string") { json, names ->
            val reader = GhostJsonStringReader(json)
            val options = JsonReaderOptions.of(*names)
            val result = LinkedHashMap<String, String>()
            reader.beginObject()
            while (true) {
                val index = reader.selectNameAndConsume(options)
                if (index == -1) break
                if (index == GhostJsonConstants.MATCH_NONE) {
                    reader.skipValue()
                    continue
                }
                result[names[index]] = reader.nextString()
            }
            reader.endObject()
            result
        },
    )

    private fun assertReadsAll(
        json: String,
        options: Array<String>,
        expected: Map<String, String>
    ) {
        for (driver in drivers) {
            assertEquals(expected, driver.read(json, options), "reader=${driver.name}")
        }
    }

    // ── In-order field prediction ─────────────────────────────────────────────────────

    @Test
    fun prediction_matchesFieldsListedInDeclarationOrder() {
        assertReadsAll(
            json = """{"id":"1","name":"rick","tag":"c137"}""",
            options = arrayOf("id", "name", "tag"),
            expected = mapOf("id" to "1", "name" to "rick", "tag" to "c137")
        )
    }

    @Test
    fun prediction_fallsBackToHashedDispatchWhenFieldsAreOutOfOrder() {
        assertReadsAll(
            json = """{"tag":"c137","id":"1","name":"rick"}""",
            options = arrayOf("id", "name", "tag"),
            expected = mapOf("tag" to "c137", "id" to "1", "name" to "rick")
        )
    }

    @Test
    fun prediction_rejectsKeyLongerThanPredictedCandidate() {
        // "id" is predicted first; the incoming "identity" shares its prefix but the byte at
        // the candidate's end is 'e', not the closing quote, so the shortcut must decline.
        assertReadsAll(
            json = """{"identity":"x","id":"1"}""",
            options = arrayOf("id", "identity"),
            expected = mapOf("identity" to "x", "id" to "1")
        )
    }

    @Test
    fun prediction_rejectsKeyShorterThanPredictedCandidate() {
        // "identity" is predicted first and is exactly LONG_BYTES long, so the wide-load
        // compare runs over payload bytes that reach past the real key.
        assertReadsAll(
            json = """{"id":"1","identity":"x"}""",
            options = arrayOf("identity", "id"),
            expected = mapOf("id" to "1", "identity" to "x")
        )
    }

    @Test
    fun prediction_distinguishesKeysSharingAWideLoadPrefix() {
        // Both names share their first 8 bytes ("descript"), so the mismatch can only be
        // found by the second wide load or the trailing byte loop.
        val options = arrayOf("descriptionAlpha", "descriptionBeta")
        assertReadsAll(
            json = """{"descriptionAlpha":"a","descriptionBeta":"b"}""",
            options = options,
            expected = mapOf("descriptionAlpha" to "a", "descriptionBeta" to "b")
        )
        assertReadsAll(
            json = """{"descriptionBeta":"b","descriptionAlpha":"a"}""",
            options = options,
            expected = mapOf("descriptionBeta" to "b", "descriptionAlpha" to "a")
        )
    }

    @Test
    fun prediction_handlesKeyOfExactlyOneWideLoad() {
        assertReadsAll(
            json = """{"exactly8":"a","exactly9x":"b"}""",
            options = arrayOf("exactly8", "exactly9x"),
            expected = mapOf("exactly8" to "a", "exactly9x" to "b")
        )
    }

    @Test
    fun prediction_skipsCandidateThatWouldReadPastTheLimit() {
        // The predicted candidate is longer than the whole remaining document; the bounds
        // guard must suppress the shortcut instead of reading out of range.
        assertReadsAll(
            json = """{"a":"1"}""",
            options = arrayOf("aFieldNameLongerThanTheDocument", "a"),
            expected = mapOf("a" to "1")
        )
    }

    @Test
    fun prediction_fallsBackToSeparatorScanWhenColonIsNotAdjacent() {
        // On a prediction hit the colon is normally the very next byte; whitespace forces
        // the slower consumeKeySeparator path.
        assertReadsAll(
            json = """{"id"   :   "1","name"  :  "rick"}""",
            options = arrayOf("id", "name"),
            expected = mapOf("id" to "1", "name" to "rick")
        )
    }

    @Test
    fun prediction_survivesUnknownFieldsBetweenKnownOnes() {
        assertReadsAll(
            json = """{"id":"1","unknown":"skip","name":"rick"}""",
            options = arrayOf("id", "name"),
            expected = mapOf("id" to "1", "name" to "rick")
        )
    }

    @Test
    fun prediction_resetsBetweenSiblingObjects() {
        val options = arrayOf("id", "name")
        val json = """{"id":"1","name":"rick"}"""
        for (driver in drivers) {
            val first = driver.read(json, options)
            val second = driver.read(json, options)
            assertEquals(first, second, "reader=${driver.name}")
        }
    }

    // ── SWAR whitespace skipping ──────────────────────────────────────────────────────

    @Test
    fun whitespace_skipsRunsLongerThanOneWideLoad() {
        // 24 spaces per gap: three full wide loads, no remainder.
        val pad = " ".repeat(24)
        assertReadsAll(
            json = "{$pad\"id\"$pad:$pad\"1\"$pad,$pad\"name\"$pad:$pad\"rick\"$pad}",
            options = arrayOf("id", "name"),
            expected = mapOf("id" to "1", "name" to "rick")
        )
    }

    @Test
    fun whitespace_skipsRunsThatAreNotAWholeNumberOfWideLoads() {
        // 11 spaces: one wide load plus a 3-byte tail handled by the scalar loop.
        val pad = " ".repeat(11)
        assertReadsAll(
            json = "{$pad\"id\"$pad:$pad\"1\"$pad}",
            options = arrayOf("id"),
            expected = mapOf("id" to "1")
        )
    }

    @Test
    fun whitespace_skipsTabsAndLineBreaksMixedWithSpaceRuns() {
        val pad = "        \t\n        \r\n  "
        assertReadsAll(
            json = "{$pad\"id\"$pad:$pad\"1\"$pad,$pad\"name\"$pad:$pad\"rick\"$pad}",
            options = arrayOf("id", "name"),
            expected = mapOf("id" to "1", "name" to "rick")
        )
    }

    @Test
    fun whitespace_rejectsControlByteThatIsNotJsonWhitespace() {
        // 0x01 sorts below the space byte like real whitespace does, so the SWAR gate must
        // hand it to the scalar path, which reports it as an unexpected token.
        val json = "{\u0001\"id\":\"1\"}"
        for (driver in drivers) {
            assertFails("reader=${driver.name}") { driver.read(json, arrayOf("id")) }
        }
    }

    // ── SWAR string scanning / deferred pool hash ─────────────────────────────────────

    @Test
    fun stringScan_readsEveryLengthAroundTheWideLoadAndPoolBoundaries() {
        val lengths = (0..20).toList() + listOf(31, 32, 33, 63, 64, 65, 127, 128)
        for (length in lengths) {
            val value = "v".repeat(length)
            assertReadsAll(
                json = """{"id":"$value"}""",
                options = arrayOf("id"),
                expected = mapOf("id" to value)
            )
        }
    }

    @Test
    fun stringScan_detectsNonAsciiAtEveryOffsetOfAWideLoad() {
        // The SWAR gate clears its pure-ASCII flag from the high bits of a whole word; a
        // masking bug only shows up for some offsets within the word.
        for (offset in 0..15) {
            val value = "a".repeat(offset) + "é" + "b".repeat(15 - offset)
            assertReadsAll(
                json = """{"id":"$value"}""",
                options = arrayOf("id"),
                expected = mapOf("id" to value)
            )
        }
    }

    @Test
    fun stringScan_stopsAtEscapesAtEveryOffsetOfAWideLoad() {
        for (offset in 0..15) {
            val prefix = "a".repeat(offset)
            val suffix = "b".repeat(15 - offset)
            assertReadsAll(
                json = """{"id":"$prefix\"$suffix"}""",
                options = arrayOf("id"),
                expected = mapOf("id" to "$prefix\"$suffix")
            )
            assertReadsAll(
                json = """{"id":"$prefix\n$suffix"}""",
                options = arrayOf("id"),
                expected = mapOf("id" to "$prefix\n$suffix")
            )
            assertReadsAll(
                json = """{"id":"$prefix\\$suffix"}""",
                options = arrayOf("id"),
                expected = mapOf("id" to "$prefix\\$suffix")
            )
        }
    }

    @Test
    fun stringScan_rejectsRawControlByteInsideAString() {
        for (driver in drivers) {
            assertFails("reader=${driver.name}") {
                driver.read("{\"id\":\"a\u0001b\"}", arrayOf("id"))
            }
        }
    }

    @Test
    fun stringScan_poolsRepeatedShortValuesWithoutMixingThemUp() {
        // The pool hash is computed only once the closing quote is found; a wrong hash
        // would return a previously pooled string for a different key.
        val json = """{"a":"alpha","b":"beta","c":"alpha","d":"beta"}"""
        assertReadsAll(
            json = json,
            options = arrayOf("a", "b", "c", "d"),
            expected = mapOf("a" to "alpha", "b" to "beta", "c" to "alpha", "d" to "beta")
        )
    }

    @Test
    fun byteOrEof_reportsEndOfInputInsteadOfThrowing() {
        val bytes = "{}".encodeToByteArray()
        val array = createByteArraySource(bytes)
        assertEquals('{'.code, array.byteOrEof(0))
        assertEquals(GhostJsonConstants.MATCH_END, array.byteOrEof(bytes.size))

        val stream = StreamingGhostSource(Buffer().writeUtf8("{}"))
        assertEquals('{'.code, stream.byteOrEof(0))
        assertEquals(GhostJsonConstants.MATCH_END, stream.byteOrEof(bytes.size))
        assertEquals(GhostJsonConstants.MATCH_END, stream.byteOrEof(1_000))
    }

    // ── Streaming window boundaries ───────────────────────────────────────────────────

    /**
     * Builds `{"pad":"…","<key>":"<value>"}` where the closing quote of [value] lands
     * [overhang] bytes past the streaming window boundary while the value starts before it,
     * forcing the scan to continue into a realigned window.
     */
    private fun payloadStraddlingWindow(
        key: String,
        value: String,
        overhang: Int
    ): String {
        val head = "{\"pad\":\""
        val mid = "\",\"$key\":\""
        val boundary = GhostJsonConstants.STREAMING_BUFFER_SIZE
        val padLength = boundary + overhang - head.length - mid.length - value.length
        assertTrue(padLength > 0, "padding must be positive")
        val json = head + "p".repeat(padLength) + mid + value + "\"}"
        val valueStart = head.length + padLength + mid.length
        assertTrue(valueStart < boundary, "value must start inside the first window")
        assertTrue(valueStart + value.length > boundary, "value must end past the boundary")
        return json
    }

    @Test
    fun streaming_readsPoolableValueStraddlingTheWindowBoundary() {
        // Short enough to be pool-eligible, so the deferred hash must be computed over a
        // span that is no longer contiguous in the buffered window.
        val value = "v".repeat(32)
        val json = payloadStraddlingWindow("k", value, overhang = 5)
        val options = arrayOf("pad", "k")
        for (driver in drivers) {
            assertEquals(value, driver.read(json, options)["k"], "reader=${driver.name}")
        }
    }

    @Test
    fun streaming_readsNonPoolableValueStraddlingTheWindowBoundary() {
        val value = "v".repeat(200)
        val json = payloadStraddlingWindow("k", value, overhang = 64)
        val options = arrayOf("pad", "k")
        for (driver in drivers) {
            assertEquals(value, driver.read(json, options)["k"], "reader=${driver.name}")
        }
    }

    @Test
    fun streaming_matchesPredictedKeyStraddlingTheWindowBoundary() {
        val key = "keyAcrossTheWindowBoundary"
        val head = "{\"pad\":\""
        val boundary = GhostJsonConstants.STREAMING_BUFFER_SIZE
        // Place the key so the boundary falls in its middle.
        val padLength = boundary - head.length - 3 - key.length / 2
        val json = head + "p".repeat(padLength) + "\",\"" + key + "\":\"v\"}"
        val options = arrayOf("pad", key)
        for (driver in drivers) {
            assertEquals("v", driver.read(json, options)[key], "reader=${driver.name}")
        }
    }

    @Test
    fun streaming_fallsBackWhenKeysStraddlingTheBoundaryAreOutOfOrder() {
        val key = "keyAcrossTheWindowBoundary"
        val head = "{\"pad\":\""
        val boundary = GhostJsonConstants.STREAMING_BUFFER_SIZE
        val padLength = boundary - head.length - 3 - key.length / 2
        val json = head + "p".repeat(padLength) + "\",\"" + key + "\":\"v\"}"
        // Options declared in the opposite order: every key mispredicts.
        val options = arrayOf(key, "pad")
        for (driver in drivers) {
            assertEquals("v", driver.read(json, options)[key], "reader=${driver.name}")
        }
    }
}
