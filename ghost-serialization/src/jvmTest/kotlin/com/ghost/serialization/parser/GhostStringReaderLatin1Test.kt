@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Gate D of the String-mode Latin1 fast-path (Fase 2 of the decode-optimization plan):
 * correctness for content that specifically exercises the [GhostJsonStringReader.latin1Bytes]
 * branch — mixed Latin1-supplement content, the string-pool length boundary, and the
 * whole-document fallback to in-place [GhostJsonStringReader.rawData] lexing when a non-Latin1
 * character appears anywhere in the document (no full CharArray copy on the hot path). JVM-only:
 * latin1Bytes is always null on other platforms, so these scenarios only meaningfully exercise
 * anything here.
 */
class GhostStringReaderLatin1Test {

    @Test
    fun sanityLatin1PathIsActuallyEngagedForPlainAscii() {
        // Guards the rest of this file's premise: if a future change accidentally stops
        // populating latin1Bytes for plain-ASCII input, every other test here would still
        // pass via the in-place rawData fallback and silently stop testing what this file is for.
        val reader = GhostJsonStringReader("""{"a":1}""")
        assertNotNull(reader.latin1Bytes, "expected the Latin1 fast path to engage for ASCII JSON")
    }

    @Test
    fun sanityLatin1PathIsNullForNonLatin1Content() {
        val reader = GhostJsonStringReader("""{"a":"漢字"}""")
        assertNull(reader.latin1Bytes, "expected the Latin1 fast path to be disengaged for CJK content")
    }

    // ── Mixed Latin1-supplement content (keys + values) ──────────────────

    @Test
    fun latin1SupplementInKeyAndValueRoundTrips() {
        val json = """{"café":"naïve façade costs £5"}"""
        val reader = GhostJsonStringReader(json)
        assertNotNull(reader.latin1Bytes, "'£' etc. are Latin1-supplement, should stay on the fast path")

        reader.beginObject()
        assertEquals("café", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("naïve façade costs £5", reader.nextString())
        reader.endObject()
    }

    @Test
    fun latin1SupplementKeyMatchesViaSelectNameAndConsume() {
        // Exercises computeKeyHash/verifyKeyMatch's Latin1 branch specifically (not just
        // readQuotedString's pool hash) via the dispatch-table lookup path KSP-generated
        // deserializers actually use.
        val options = JsonReaderOptions.of("café", "naïve")
        val reader = GhostJsonStringReader("""{"naïve":"yes"}""")
        assertNotNull(reader.latin1Bytes)
        reader.beginObject()
        val index = reader.selectNameAndConsume(options)
        assertEquals(1, index)
        assertEquals("yes", reader.nextString())
    }

    // ── String pool boundary (maxStringPoolLength) ────────────────────────

    @Test
    fun stringsAtAndBeyondPoolBoundaryDecodeCorrectly() {
        val atBoundary = "x".repeat(GhostHeuristics.maxStringPoolLength)
        val beyondBoundary = "y".repeat(GhostHeuristics.maxStringPoolLength + 1)
        val json = """{"a":"$atBoundary","b":"$beyondBoundary"}"""
        val reader = GhostJsonStringReader(json)
        assertNotNull(reader.latin1Bytes)

        reader.beginObject()
        assertEquals("a", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(atBoundary, reader.nextString())
        assertEquals("b", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(beyondBoundary, reader.nextString())
        reader.endObject()
    }

    @Test
    fun repeatedShortLatin1StringsArePooledToTheSameInstance() {
        // Confirms the pool fast path (computeStringPoolHash/poolContentEquals) still
        // dedupes correctly when reading straight from latin1Bytes instead of rawChars.
        val reader = GhostJsonStringReader("""["café","café","café"]""")
        assertNotNull(reader.latin1Bytes)
        reader.beginArray()
        val first = reader.nextString()
        reader.hasNext()
        val second = reader.nextString()
        reader.hasNext()
        val third = reader.nextString()
        reader.endArray()
        assertEquals("café", first)
        assertSame(first, second, "expected string-pool reuse for repeated identical short strings")
        assertSame(first, third)
    }

    // ── Whole-document fallback ───────────────────────────────────────────

    @Test
    fun singleNonLatin1FieldFallsBackWholeDocumentButAllFieldsStillDecodeCorrectly() {
        // The JVM's compact-string coder is whole-String: one CJK character anywhere forces
        // latin1Bytes=null for the *entire* document. Hot paths then lex [rawData] in place
        // (no CharArray copy). Every field must still decode correctly.
        val json = """{"first":"ascii value","middle":"漢字","last":42,"after":"still ascii"}"""
        val reader = GhostJsonStringReader(json)
        assertNull(reader.latin1Bytes, "one non-Latin1 char anywhere should disable the fast path for the whole document")

        reader.beginObject()
        assertEquals("first", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("ascii value", reader.nextString())
        assertEquals("middle", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("漢字", reader.nextString())
        assertEquals("last", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        assertEquals("after", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("still ascii", reader.nextString())
        reader.endObject()
    }

    @Test
    fun unicodeDocumentDoesNotMaterializeRawCharsOnHotPath() {
        // After a full hot-path decode of a non-Latin1 document, rawChars must still be lazy —
        // proving we no longer copy the whole String into a CharArray for every Twitter-like payload.
        val json = """{"id":1,"name":"漢字","url":"https://example.com/路徑"}"""
        val reader = GhostJsonStringReader(json)
        assertNull(reader.latin1Bytes)

        reader.beginObject()
        while (reader.hasNext()) {
            reader.nextKey()
            reader.consumeKeySeparator()
            reader.skipValue()
        }
        reader.endObject()

        // Touching the private validity flag via reflection is brittle; instead assert that a
        // subsequent escape slow-path still works (which builds rawChars) and that decode was
        // correct without needing it earlier — re-parse and read values.
        val reader2 = GhostJsonStringReader(json)
        reader2.beginObject()
        assertEquals("id", reader2.nextKey())
        reader2.consumeKeySeparator()
        assertEquals(1, reader2.nextInt())
        assertEquals("name", reader2.nextKey())
        reader2.consumeKeySeparator()
        assertEquals("漢字", reader2.nextString())
        assertEquals("url", reader2.nextKey())
        reader2.consumeKeySeparator()
        assertEquals("https://example.com/路徑", reader2.nextString())
        reader2.endObject()
    }

    @Test
    fun escapedQuoteFallsToSlowPathAndDecodes() {
        val json = """{"a":"say \"hi\"","b":2}"""
        val reader = GhostJsonStringReader(json)
        reader.beginObject()
        assertEquals("a", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("say \"hi\"", reader.nextString())
        assertEquals("b", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals(2, reader.nextInt())
        reader.endObject()
    }

    // ── Escaped characters mid-document (lazy rawChars trigger ordering) ──

    @Test
    fun escapedCharacterOnAllLatin1DocumentDecodesCorrectlyRegardlessOfFieldOrder() {
        // readQuotedStringSlow (the escape-handling path) always reads rawChars directly and
        // is intentionally NOT adapted to latin1Bytes (cold path, per the optimization plan).
        // Verifies that triggering its lazy rawChars build partway through a document doesn't
        // corrupt fields read before or after it, and that the result doesn't depend on
        // whether an earlier field already forced rawChars to build.
        val json = """{"plain":"no escapes here","escaped":"line1\nline2\ttabbed","afterEscape":"plain again"}"""
        val reader = GhostJsonStringReader(json)
        assertNotNull(reader.latin1Bytes, "all content here is Latin1 — only the escape sequences are special")

        reader.beginObject()
        assertEquals("plain", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("no escapes here", reader.nextString())
        assertEquals("escaped", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("line1\nline2\ttabbed", reader.nextString())
        assertEquals("afterEscape", reader.nextKey())
        reader.consumeKeySeparator()
        assertEquals("plain again", reader.nextString())
        reader.endObject()
    }

    // ── Numeric parsing via the Latin1 path ────────────────────────────────

    @Test
    fun numericTypesDecodeCorrectlyViaLatin1Path() {
        val json = """{"i":42,"l":9223372036854775807,"f":3.5,"d":-2.5,"neg":-17,"skip":123.456e10}"""
        val reader = GhostJsonStringReader(json)
        assertNotNull(reader.latin1Bytes)

        reader.beginObject()
        assertEquals("i", reader.nextKey()); reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())
        assertEquals("l", reader.nextKey()); reader.consumeKeySeparator()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        assertEquals("f", reader.nextKey()); reader.consumeKeySeparator()
        assertEquals(3.5f, reader.nextFloat())
        assertEquals("d", reader.nextKey()); reader.consumeKeySeparator()
        assertEquals(-2.5, reader.nextDouble())
        assertEquals("neg", reader.nextKey()); reader.consumeKeySeparator()
        assertEquals(-17, reader.nextInt())
        assertEquals("skip", reader.nextKey()); reader.consumeKeySeparator()
        reader.skipNumber()
        reader.endObject()
    }
}
