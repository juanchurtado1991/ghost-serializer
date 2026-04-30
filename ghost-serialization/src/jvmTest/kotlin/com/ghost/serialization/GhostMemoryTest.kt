@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginArray
import com.ghost.serialization.parser.endArray
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.writer.GhostJsonWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhostMemoryTest {

    // --- Recursive Serializer for testing depth ---
    private object RecursiveSerializer : GhostSerializer<Any> {
        override val typeName: String = "Recursive"
        override fun serialize(writer: GhostJsonWriter, value: Any) {}
        override fun deserialize(reader: GhostJsonReader): Any {
            reader.beginArray()
            val result = if (reader.peekByte() == '['.code.toByte()) {
                deserialize(reader)
            } else {
                reader.nextInt()
            }
            reader.endArray()
            return result
        }
    }

    @Test
    fun testDeepRecursionProtection() = runTest {
        // Create a deeply nested JSON string: [[[[...]]]]
        val depth = 300
        val json = "[".repeat(depth) + "1" + "]".repeat(depth)

        // This should throw GhostJsonException because depth > 255
        assertFailsWith<GhostJsonException> {
            RecursiveSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        }
    }

    @Test
    fun testPrimitiveFailsOnUnexpectedStructure() = runTest {
        val json = "[[[1]]]"
        // IntSerializer should fail because it expects a number, not an array
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<Int>(json)
        }
    }

    @Test
    fun testLargePayloadMemorySafety() = runTest {
        // 10MB JSON string
        val largeString = "a".repeat(10 * 1024 * 1024)
        val json = "\"$largeString\""

        val result = Ghost.deserialize<String>(json)
        assertEquals(largeString.length, result.length)
        assertEquals(largeString, result)
    }

}

