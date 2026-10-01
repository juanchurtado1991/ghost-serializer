@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GhostPerformanceValidationTest {

    private class MockSerializer : AbstractGhostSerializer<String>() {
        override val typeName: String = "Mock"
        override fun serialize(writer: GhostJsonWriter, value: String) {}

        override fun deserialize(reader: GhostJsonReader): String = ""
    }

    private class MockRegistry : AbstractGhostRegistry() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return if (clazz == String::class) MockSerializer() as GhostSerializer<T> else null
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(String::class to MockSerializer())
        }

    }

    @Test
    fun testDeepPrewarmLogic() {
        Ghost.serializerCache.clear()

        val ghost = Ghost
        ghost.addRegistry(registry = MockRegistry())

        ghost.prewarm()

        assertNotNull(
            actual = Ghost.serializerCache[String::class],
            message = "Prewarm must populate the cache with production-ready serializers"
        )
    }

    @Test
    fun testFieldTrieLogicCorrectness() {
        val options = JsonReaderOptions.of("id", "name", "email", "active")
        val json = """{"email": "ghost@standard.com", "id": 1}""".encodeToByteArray()
        val reader = GhostJsonReader(json)

        reader.beginObject()

        val index = reader.selectString(options = options)
        assertEquals(
            expected = 2,
            actual = index,
            message = "Trie must match 'email' with priority index 2"
        )

        reader.consumeKeySeparator()
        reader.nextString()

        val index2 = reader.selectString(options = options)
        assertEquals<Int>(expected = 0, actual = index2, message = "Trie must match 'id' with index 0")
    }
}
