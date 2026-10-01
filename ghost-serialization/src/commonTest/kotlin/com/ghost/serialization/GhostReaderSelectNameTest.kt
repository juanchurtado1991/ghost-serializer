@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter
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

        val firstIndex = reader.selectString(options = options)
        assertEquals(
            expected = 1,
            actual = firstIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 10,
            actual = reader.nextInt()
        )

        val secondIndex = reader.selectString(options = options)
        assertEquals(
            expected = 0,
            actual = secondIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 5,
            actual = reader.nextInt()
        )

        val thirdIndex = reader.selectString(options = options)
        assertEquals(
            expected = 3,
            actual = thirdIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "ns",
            actual = reader.nextString()
        )

        val fourthIndex = reader.selectString(options = options)
        assertEquals(
            expected = 2,
            actual = fourthIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "n",
            actual = reader.nextString()
        )

        assertEquals(
            expected = -1,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    // ── I. UNKNOWN FIELD SKIPPING ─────────────────────────────────────

    @Test
    fun selectStringReturnsMinusTwoForUnknownFields() {
        val options = JsonReaderOptions.of("id", "name")
        val json = "{\"unknown\":\"skip_me\",\"id\":1}"
        val reader = readerOf(json)
        reader.beginObject()

        val firstIndex = reader.selectString(options = options)
        assertEquals(
            expected = -2,
            actual = firstIndex
        )
        reader.consumeKeySeparator()
        reader.skipValue()

        val secondIndex = reader.selectString(options = options)
        assertEquals(
            expected = 0,
            actual = secondIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )

        assertEquals(
            expected = -1,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    @Test
    fun skipsComplexUnknownValues() {
        val options = JsonReaderOptions.of("id")
        val json = "{\"nested\":{\"a\":\"b\",\"c\":\"d\"},\"id\":42}"
        val reader = readerOf(json)
        reader.beginObject()

        val firstIndex = reader.selectString(options = options)
        assertEquals(
            expected = -2,
            actual = firstIndex
        )
        reader.consumeKeySeparator()
        reader.skipValue()

        val secondIndex = reader.selectString(options = options)
        assertEquals(
            expected = 0,
            actual = secondIndex
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )

        assertEquals(
            expected = -1,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    // ── J. NEGATIVE & EXTREME DOUBLES ────────────────────────────────

    @Test
    fun readsNegativeDouble() {
        val reader = readerOf("{\"v\":-123.456}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = -123.456,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.001
        )
    }

    @Test
    fun readsVerySmallDouble() {
        val reader = readerOf("{\"v\":0.000001}")
        reader.beginObject()
        reader.nextKey().unused()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 0.000001,
            actual = reader.nextDouble(),
            absoluteTolerance = 1e-10
        )
    }

    // ── K. WRITER→READER BYTE ROUNDTRIP ──────────────────────────────

    @Test
    fun writerOutputIsReadableByReader() {
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        writer.beginObject()
            .name(key = "id").value(42)
            .name(key = "msg").value("hello\nworld")
            .name(key = "flag").value(true)
            .name(key = "score").value(3.14)
            .name(key = "nothing").nullValue()
            .name(key = "items")
            .beginArray().value(1).value(2).value(3).endArray().unused()
        writer.endObject().unused()

        writer.flush()
        val json = buffer.readUtf8()
        val reader = readerOf(json)

        reader.beginObject()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(
            expected = "hello\nworld",
            actual = reader.nextString()
        )

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(
            expected = true,
            actual = reader.nextBoolean()
        )

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertEquals(
            expected = 3.14,
            actual = reader.nextDouble(),
            absoluteTolerance = 0.001
        )

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        assertTrue(actual = reader.isNextNullValue())
        reader.consumeNull()

        reader.consumeArraySeparator()
        reader.nextKey().unused(); reader.consumeKeySeparator()
        val items = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(1, 2, 3),
            actual = items
        )

        reader.endObject()
    }

    // ── L. MULTIPLE ADJACENT ARRAYS ──────────────────────────────────

    @Test
    fun readsObjectWithMultipleArrays() {
        val json = "{\"a\":[1,2],\"b\":[\"x\",\"y\"]}"
        val options = JsonReaderOptions.of("a", "b")
        val reader = readerOf(json)
        reader.beginObject()

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        val ints = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(1, 2),
            actual = ints
        )

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        val strings = reader.readList { reader.nextString() }
        assertEquals(
            expected = listOf("x", "y"),
            actual = strings
        )

        assertEquals(
            expected = -1,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    // ── M. DYNAMIC TABLE SIZES ───────────────────────────────────────

    @Test
    fun jsonReaderOptionsRespectsDynamicTableSizes() {
        val names = arrayOf("id", "name", "email")
        val options128 = JsonReaderOptions.of(0, 31, 128, *names)
        assertEquals(
            expected = 128,
            actual = options128.dispatch.size
        )

        val options256 = JsonReaderOptions.of(0, 31, 256, *names)
        assertEquals(
            expected = 256,
            actual = options256.dispatch.size
        )

        val json = "{\"email\":\"test@test.com\",\"id\":42,\"name\":\"ghost\"}"

        val reader1 = readerOf(json)
        reader1.beginObject()
        assertEquals(
            expected = 2,
            actual = reader1.selectString(options = options128)
        ) // email
        reader1.consumeKeySeparator()
        assertEquals(
            expected = "test@test.com",
            actual = reader1.nextString()
        )
        assertEquals(
            expected = 0,
            actual = reader1.selectString(options = options128)
        ) // id
        reader1.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader1.nextInt()
        )
        assertEquals(
            expected = 1,
            actual = reader1.selectString(options = options128)
        ) // name
        reader1.consumeKeySeparator()
        assertEquals(
            expected = "ghost",
            actual = reader1.nextString()
        )
        reader1.endObject()

        val reader2 = readerOf(json)
        reader2.beginObject()
        assertEquals(
            expected = 2,
            actual = reader2.selectString(options = options256)
        ) // email
        reader2.consumeKeySeparator()
        assertEquals(
            expected = "test@test.com",
            actual = reader2.nextString()
        )
        assertEquals(
            expected = 0,
            actual = reader2.selectString(options = options256)
        ) // id
        reader2.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader2.nextInt()
        )
        assertEquals(
            expected = 1,
            actual = reader2.selectString(options = options256)
        ) // name
        reader2.consumeKeySeparator()
        assertEquals(
            expected = "ghost",
            actual = reader2.nextString()
        )
        reader2.endObject()
    }
}
