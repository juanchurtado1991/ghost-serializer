@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization
import com.ghost.serialization.parser.*

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GhostPerformanceValidationTest {

    private class MockSerializer : GhostSerializer<String> {
        override val typeName: String = "Mock"
        override fun serialize(
            writer: GhostJsonWriter,
            value: String
        ) {
        }

        override fun deserialize(reader: GhostJsonReader): String = ""
    }

    private class MockRegistry : GhostRegistry {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return if (clazz == String::class) MockSerializer() as GhostSerializer<T> else null
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(String::class to MockSerializer())
        }

        override fun prewarm() {}
    }

    @Test
    fun testDeepPrewarmLogic() {
        // Reset cache for pure test
        Ghost.serializerCache.clear()

        val ghost = Ghost
        ghost.addRegistry(MockRegistry())

        // Before prewarm, cache for String::class should be null
        // (Assuming no other test populated it)
        // Actually, let's just test that after prewarm it IS populated.

        ghost.prewarm()

        assertNotNull(
            Ghost.serializerCache[String::class],
            "Prewarm must populate the cache with production-ready serializers"
        )
    }

    @Test
    fun testFieldTrieLogicCorrectness() {
        val options = JsonReaderOptions.of("id", "name", "email", "active")
        val json = """{"email": "ghost@standard.com", "id": 1}""".encodeToByteArray()
        val reader = GhostJsonReader(json)

        reader.beginObject()

        // Search for 'email'
        val index = reader.selectString(options)
        assertEquals(2, index, "Trie must match 'email' with priority index 2")

        reader.consumeKeySeparator()
        reader.nextString()

        // Search for 'id'
        val index2 = reader.selectString(options)
        assertEquals<Int>(0, index2, "Trie must match 'id' with index 0")
    }
}
