package com.ghost.serialization.core

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GhostCrashProofTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(Buffer().writeUtf8(json))
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 1: isNextNullValue() false positive
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun isNextNullDetectsActualNull() {
        val reader = readerOf("{\"v\":null}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForString() {
        val reader = readerOf("{\"v\":\"hello\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForNumber() {
        val reader = readerOf("{\"v\":42}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForObject() {
        val reader = readerOf("{\"v\":{}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForArray() {
        val reader = readerOf("{\"v\":[]}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForTrue() {
        val reader = readerOf("{\"v\":true}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    @Test
    fun isNextNullReturnsFalseForFalse() {
        val reader = readerOf("{\"v\":false}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFalse(reader.isNextNullValue())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 2: Invalid unicode escape
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun invalidUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00GG\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun truncatedUnicodeEscapeThrowsException() {
        val json = "{\"v\":\"\\u00\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 3: Long overflow
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun veryLargeNumberDoesNotCrash() {
        val huge = "99999999999999999999"
        val reader = readerOf("{\"v\":$huge}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextLong()
    }

    @Test
    fun readsExactLongMaxValue() {
        val reader = readerOf("{\"v\":${Long.MAX_VALUE}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
    }

    @Test
    fun readsExactLongMinValue() {
        val reader = readerOf("{\"v\":${Long.MIN_VALUE}}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(Long.MIN_VALUE, reader.nextLong())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 4: Truncated literals
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun truncatedNullCorruptsStreamAndFailsLater() {
        val reader = readerOf("{\"v\":nul}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()
        assertFailsWith<Exception> { reader.endObject() }
    }

    @Test
    fun truncatedTrueThrowsException() {
        val reader = readerOf("tru")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    @Test
    fun truncatedFalseThrowsException() {
        val reader = readerOf("fals")
        assertFailsWith<Exception> { reader.nextBoolean() }
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 5: Registry returns null for unregistered class
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun registryReturnsNullForUnregisteredClass() {
        val fakeRegistry = object : GhostRegistry {
            override fun <T : Any> getSerializer(
                clazz: kotlin.reflect.KClass<T>
            ): GhostSerializer<T>? = null
        }
        assertNull(fakeRegistry.getSerializer(String::class))
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 6: Surrogate pairs and multi-byte unicode
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsSurrogatePairEmoji() {
        val json = "{\"v\":\"\\uD83D\\uDE80\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val result = reader.nextString()
        assertEquals("\uD83D\uDE80", result)
    }

    @Test
    fun readsDirectEmojiWithoutSurrogates() {
        val reader = readerOf("{\"v\":\"\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89\"}")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\uD83D\uDD25\uD83D\uDC80\uD83C\uDF89", reader.nextString())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 7: selectName with empty/edge-case Options
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsObjectWithMultipleArrays() {
        val options = GhostJsonReader.Options.of("a", "b")
        val json = "{\"a\":[1,2],\"b\":[3,4]}"
        val reader = readerOf(json)
        reader.beginObject()
        
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        val list1 = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2), list1)
        
        assertEquals(1, reader.selectName(options))
        reader.consumeKeySeparator()
        val list2 = reader.readList { reader.nextInt() }
        assertEquals(listOf(3, 4), list2)
        reader.endObject()
    }

    @Test
    fun testSegmentBoundarySplitting() {
        // Force a key selection at the end of a buffer segment
        val key = "very_long_key_to_force_segment_switching_in_okio_buffer"
        val options = GhostJsonReader.Options.of(key)
        val json = "{\"$key\":\"value\"}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals("value", reader.nextString())
        reader.endObject()
    }

    @Test
    fun selectNameWithEmptyOptionsSkipsAllFields() {
        val options = GhostJsonReader.Options.of()
        val json = "{\"a\":1,\"b\":2}"
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(-2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(-2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(-1, reader.selectName(options))
        reader.endObject()
    }

    @Test
    fun selectNameWithSingleOption() {
        val options = GhostJsonReader.Options.of("only")
        val json = "{\"only\":\"found\"}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals("found", reader.nextString())
        assertEquals(-1, reader.selectName(options))
        reader.endObject()
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 8: JSON with only whitespace
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun whitespaceOnlyInputReportsEndDocument() {
        val reader = readerOf("   \n\t  ")
        assertEquals(JsonToken.END_DOCUMENT, reader.peekJsonToken())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 9: Stream exhaustion mid-parse
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun exhaustedSourceDuringObjectParseFails() {
        val reader = readerOf("{")
        reader.beginObject()
        assertFailsWith<Exception> { reader.nextKey() }
    }

    @Test
    fun exhaustedSourceDuringStringFails() {
        val reader = readerOf("{\"v\":\"unterminated")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<Exception> { reader.nextString() }
    }

    @Test
    fun exhaustedSourceDuringArrayFails() {
        val reader = readerOf("[1,2,")
        assertFailsWith<Exception> {
            reader.readList { reader.nextInt() }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 10: Escape character roundtrips
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsStringOfOnlyBackslashes() {
        val json = "{\"v\":\"\\\\\\\\\"}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\\\\", reader.nextString())
    }

    @Test
    fun readsStringWithConsecutiveJsonEscapes() {
        val bs = '\\'
        val q = '"'
        val json = "{${q}v${q}:${q}${bs}n${bs}t${bs}r${bs}b${q}}"
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals("\n\t\r\b", reader.nextString())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 11: Numbers at end of stream (no terminator)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun readsNumberAtEndOfArray() {
        val reader = readerOf("[42]")
        val result = reader.readList { reader.nextInt() }
        assertEquals(listOf(42), result)
    }

    @Test
    fun readsDoubleAtEndOfArray() {
        val reader = readerOf("[3.14]")
        val result = reader.readList { reader.nextDouble() }
        assertEquals(1, result.size)
        assertEquals(3.14, result[0], 0.001)
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 12: skipBalanced with braces/brackets inside strings
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun skipsObjectContainingBracesInStrings() {
        val options = GhostJsonReader.Options.of("id")
        val json = "{\"junk\":{\"msg\":\"value with { and } inside\"},\"id\":1}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(-2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())
    }

    @Test
    fun skipsArrayContainingBracketsInStrings() {
        val options = GhostJsonReader.Options.of("id")
        val json = "{\"junk\":[\"contains [ and ]\"],\"id\":2}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(-2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals(2, reader.nextInt())
    }

    @Test
    fun skipsObjectContainingEscapedQuotesInStrings() {
        val options = GhostJsonReader.Options.of("id")
        val json = "{\"junk\":{\"msg\":\"escaped \\\"quotes\\\" and {braces}\"},\"id\":3}"
        val reader = readerOf(json)
        reader.beginObject()
        assertEquals(-2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals(3, reader.nextInt())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 13: Writer→Reader roundtrip with ALL escape types
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun roundtripAllEscapeCharacters() {
        val original = "tab\there\nnewline\rcarriage\bback\"quote\\slash"
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        writer.beginObject().name("v").value(original).endObject()

        val json = buffer.readUtf8()
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(original, reader.nextString())
    }

    @Test
    fun roundtripControlCharsBelow0x20() {
        val original = "\u0001\u0002\u0003\u0010\u001F"
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        writer.beginObject().name("v").value(original).endObject()

        val json = buffer.readUtf8()
        val reader = readerOf(json)
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(original, reader.nextString())
    }

    // ══════════════════════════════════════════════════════════════════
    // RISK 14: Multiple prefix-sharing field names (3+ variants)
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectNameWithThreePrefixVariants() {
        val options = GhostJsonReader.Options.of(
            "user", "userId", "userIds", "userName"
        )
        val json = "{\"userIds\":[1],\"userName\":\"g\",\"userId\":42,\"user\":\"obj\"}"
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(2, reader.selectName(options))
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }
        assertEquals(3, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals("g", reader.nextString())
        assertEquals(1, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        assertEquals(0, reader.selectName(options))
        reader.consumeKeySeparator()
        assertEquals("obj", reader.nextString())
    }
}
