@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeArraySeparator
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals


class HugeJsonTest {

    @Test
    fun testHugeListSyntax() {
        // Generate enough data to cross position 422 and multiple buffer flushes
        val data = List(100) { i ->
            mapOf("id" to i, "name" to "item_$i", "value" to i * 1.5)
        }

        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)

        writer.beginArray()
        for (obj in data) {
            writer.beginObject()
            writer.name(key = "id").value(obj["id"] as Int)
            writer.name(key = "name").value(obj["name"] as String)
            writer.name(key = "value").value(obj["value"] as Double)
            writer.endObject()
        }
        writer.endArray()
        writer.release()

        writer.flush()
        val json = buffer.readUtf8()

        println("JSON: $json")
        val reader = GhostJsonReader(json.encodeToByteArray())
        reader.beginArray()
        var count = 0
        while (reader.hasNext()) {
            reader.beginObject()
            assertEquals(
                expected = "id",
                actual = reader.nextKey()
            )
            reader.consumeKeySeparator()
            assertEquals(
                expected = count,
                actual = reader.nextInt()
            )

            reader.consumeArraySeparator()
            assertEquals(
                expected = "name",
                actual = reader.nextKey()
            )
            reader.consumeKeySeparator()
            assertEquals(
                expected = "item_$count",
                actual = reader.nextString()
            )

            reader.consumeArraySeparator()
            assertEquals(
                expected = "value",
                actual = reader.nextKey()
            )
            reader.consumeKeySeparator()
            assertEquals(
                expected = count * 1.5,
                actual = reader.nextDouble()
            )

            reader.endObject()
            count++
        }
        reader.endArray()
        assertEquals(
            expected = 100,
            actual = count
        )
    }
}
