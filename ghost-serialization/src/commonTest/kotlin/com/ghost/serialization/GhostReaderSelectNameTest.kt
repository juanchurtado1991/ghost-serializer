@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.consumeNull
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.isNextNullValue
import com.ghost.serialization.parser.nextBoolean
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.readList
import com.ghost.serialization.parser.selectString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostReaderSelectNameTest {

    private fun readerOf(json: String): GhostJsonReader {
        return GhostJsonReader(json.encodeToByteArray())
    }

    // ── H. PREFIX-MATCHING REGRESSION ─────────────────────────────────

    @Test
    fun selectStringDistinguishesPrefixFields() {
        val options = JsonReaderOptions.of("score", "scores", "name", "namespace")
        val json = "{\"scores\":10,\"score\":5,\"namespace\":\"ns\",\"name\":\"n\"}"
        val reader = readerOf(json)
        reader.beginObject()

        val firstIndex = reader.selectString(options)
        assertEquals(1, firstIndex)
        reader.consumeKeySeparator()
        assertEquals(10, reader.nextInt())

        val secondIndex = reader.selectString(options)
        assertEquals(0, secondIndex)
        reader.consumeKeySeparator()
        assertEquals(5, reader.nextInt())

        val thirdIndex = reader.selectString(options)
        assertEquals(3, thirdIndex)
        reader.consumeKeySeparator()
        assertEquals("ns", reader.nextString())

        val fourthIndex = reader.selectString(options)
        assertEquals(2, fourthIndex)
        reader.consumeKeySeparator()
        assertEquals("n", reader.nextString())

        assertEquals(-1, reader.selectString(options))
        reader.endObject()
    }

    // ── I. UNKNOWN FIELD SKIPPING ─────────────────────────────────────

    @Test
    fun selectStringReturnsMinusTwoForUnknownFields() {
        val options = JsonReaderOptions.of("id", "name")
        val json = "{\"unknown\":\"skip_me\",\"id\":1}"
        val reader = readerOf(json)
        reader.beginObject()

        val firstIndex = reader.selectString(options)
        assertEquals(-2, firstIndex)
        reader.consumeKeySeparator()
        reader.skipValue()

        val secondIndex = reader.selectString(options)
        assertEquals(0, secondIndex)
        reader.consumeKeySeparator()
        assertEquals(1, reader.nextInt())

        assertEquals(-1, reader.selectString(options))
        reader.endObject()
    }

    @Test
    fun skipsComplexUnknownValues() {
        val options = JsonReaderOptions.of("id")
        val json = "{\"nested\":{\"a\":\"b\",\"c\":\"d\"},\"id\":42}"
        val reader = readerOf(json)
        reader.beginObject()

        val firstIndex = reader.selectString(options)
        assertEquals(-2, firstIndex)
        reader.consumeKeySeparator()
        reader.skipValue()

        val secondIndex = reader.selectString(options)
        assertEquals(0, secondIndex)
        reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())

        assertEquals(-1, reader.selectString(options))
        reader.endObject()
    }

    // ── J. NEGATIVE & EXTREME DOUBLES ────────────────────────────────

    @Test
    fun readsNegativeDouble() {
        val reader = readerOf("{\"v\":-123.456}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(-123.456, reader.nextDouble(), 0.001)
    }

    @Test
    fun readsVerySmallDouble() {
        val reader = readerOf("{\"v\":0.000001}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(0.000001, reader.nextDouble(), 1e-10)
    }

    // ── K. WRITER→READER BYTE ROUNDTRIP ──────────────────────────────

    @Test
    fun writerOutputIsReadableByReader() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        writer.beginObject()
            .name("id").value(42)
            .name("msg").value("hello\nworld")
            .name("flag").value(true)
            .name("score").value(3.14)
            .name("nothing").nullValue()
            .name("items")
            .beginArray().value(1).value(2).value(3).endArray().unused()
        writer.endObject().unused()

        writer.flush()
        val json = buffer.readUtf8()
        val reader = readerOf(json)

        reader.beginObject()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(42, reader.nextInt())

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals("hello\nworld", reader.nextString())

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(true, reader.nextBoolean())

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(3.14, reader.nextDouble(), 0.001)

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertTrue(reader.isNextNullValue())
        reader.consumeNull()

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        val items = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2, 3), items)

        reader.endObject()
    }

    // ── L. MULTIPLE ADJACENT ARRAYS ──────────────────────────────────

    @Test
    fun readsObjectWithMultipleArrays() {
        val json = "{\"a\":[1,2],\"b\":[\"x\",\"y\"]}"
        val options = JsonReaderOptions.of("a", "b")
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(0, reader.selectString(options))
        reader.consumeKeySeparator()
        val ints = reader.readList { reader.nextInt() }
        assertEquals(listOf(1, 2), ints)

        assertEquals(1, reader.selectString(options))
        reader.consumeKeySeparator()
        val strings = reader.readList { reader.nextString() }
        assertEquals(listOf("x", "y"), strings)

        assertEquals(-1, reader.selectString(options))
        reader.endObject()
    }

    // ── M. DYNAMIC TABLE SIZES ───────────────────────────────────────

    @Test
    fun jsonReaderOptionsRespectsDynamicTableSizes() {
        val names = arrayOf("id", "name", "email")
        val options128 = JsonReaderOptions.of(0, 31, 128, *names)
        assertEquals(128, options128.dispatch.size)

        val options256 = JsonReaderOptions.of(0, 31, 256, *names)
        assertEquals(256, options256.dispatch.size)

        val json = "{\"email\":\"test@test.com\",\"id\":42,\"name\":\"ghost\"}"

        val reader1 = readerOf(json)
        reader1.beginObject()
        assertEquals(2, reader1.selectString(options128)) // email
        reader1.consumeKeySeparator()
        assertEquals("test@test.com", reader1.nextString())
        assertEquals(0, reader1.selectString(options128)) // id
        reader1.consumeKeySeparator()
        assertEquals(42, reader1.nextInt())
        assertEquals(1, reader1.selectString(options128)) // name
        reader1.consumeKeySeparator()
        assertEquals("ghost", reader1.nextString())
        reader1.endObject()

        val reader2 = readerOf(json)
        reader2.beginObject()
        assertEquals(2, reader2.selectString(options256)) // email
        reader2.consumeKeySeparator()
        assertEquals("test@test.com", reader2.nextString())
        assertEquals(0, reader2.selectString(options256)) // id
        reader2.consumeKeySeparator()
        assertEquals(42, reader2.nextInt())
        assertEquals(1, reader2.selectString(options256)) // name
        reader2.consumeKeySeparator()
        assertEquals("ghost", reader2.nextString())
        reader2.endObject()
    }
}
