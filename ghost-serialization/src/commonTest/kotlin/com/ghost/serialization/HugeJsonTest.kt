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
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeArraySeparator
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endArray
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.hasNext
import com.ghost.serialization.parser.strings.nextDouble
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Buffer


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
            writer.name("id").value(obj["id"] as Int)
            writer.name("name").value(obj["name"] as String)
            writer.name("value").value(obj["value"] as Double)
            writer.endObject()
        }
        writer.endArray()
        writer.release()

        writer.flush()
        val json = buffer.readUtf8()

        // Verify syntax by parsing it back with a standard parser (or our reader)
        println("JSON: $json")
        val reader = GhostJsonReader(json.encodeToByteArray())
        reader.beginArray()
        var count = 0
        while (reader.hasNext()) {
            reader.beginObject()
            assertEquals("id", reader.nextKey())
            reader.consumeKeySeparator()
            assertEquals(count, reader.nextInt())

            reader.consumeArraySeparator()
            assertEquals("name", reader.nextKey())
            reader.consumeKeySeparator()
            assertEquals("item_$count", reader.nextString())

            reader.consumeArraySeparator()
            assertEquals("value", reader.nextKey())
            reader.consumeKeySeparator()
            assertEquals(count * 1.5, reader.nextDouble())

            reader.endObject()
            count++
        }
        reader.endArray()
        assertEquals(100, count)
    }
}
