@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.endArray
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith


class GhostMemoryTest {

    @BeforeTest
    fun setUp() {
        Ghost.resetForTest()
    }

    private object RecursiveSerializer : AbstractGhostSerializer<Any>() {
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
        val depth = 300
        val json = "[".repeat(depth) + "1" + "]".repeat(depth)

        // Depth (300) exceeds the max depth limit (255).
        assertFailsWith<GhostJsonException> {
            RecursiveSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        }
    }

    @Test
    fun testPrimitiveFailsOnUnexpectedStructure() = runTest {
        val json = "[[[1]]]"
        // Int deserialization expects a number, not an array.
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<Int>(json)
        }
    }

    @Test
    fun testLargePayloadMemorySafety() = runTest {
        val largeString = "a".repeat(10 * 1024 * 1024)
        val json = "\"$largeString\""

        val result = Ghost.deserialize<String>(json)
        assertEquals(
            expected = largeString.length,
            actual = result.length
        )
        assertEquals(
            expected = largeString,
            actual = result
        )
    }

}

