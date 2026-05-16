@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginArray
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeArraySeparator
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endArray
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.hasNext
import com.ghost.serialization.parser.ignore
import com.ghost.serialization.parser.nextDouble
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonWriter
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
            writer.beginObject().ignore()
            writer.name("id").value(obj["id"] as Int).ignore()
            writer.name("name").value(obj["name"] as String).ignore()
            writer.name("value").value(obj["value"] as Double).ignore()
            writer.endObject().ignore()
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
