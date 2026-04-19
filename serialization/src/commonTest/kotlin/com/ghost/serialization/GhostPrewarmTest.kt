package com.ghostserializer
import kotlin.test.assertTrue

import com.ghostserializer.core.contract.GhostRegistry
import com.ghostserializer.core.contract.GhostSerializer
import com.ghostserializer.core.parser.GhostJsonReader
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class GhostPrewarmTest {

    class MockUser(val id: Int, val name: String)

    class MockUserSerializer : GhostSerializer<MockUser> {
        var warmupCalled = false
        override fun serialize(writer: com.ghostserializer.core.writer.GhostJsonWriter, value: MockUser) {}
        override fun deserialize(reader: GhostJsonReader): MockUser {
            return MockUser(1, "test")
        }
        override fun warmUp() {
            warmupCalled = true
        }
    }

    class MockRegistry : GhostRegistry {
        val serializer = MockUserSerializer()
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return if (clazz == MockUser::class) serializer as GhostSerializer<T> else null
        }
        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(MockUser::class to serializer)
        }
        override fun registeredCount(): Int = 1
    }

    @Test
    fun testDeepPrewarmInducesWarmup() {
        val registry = MockRegistry()
        Ghost.addRegistry(registry)
        
        Ghost.prewarm()
        
        assertTrue(registry.serializer.warmupCalled, "Deep Prewarm must call warmUp() on serializers to induce JIT optimization")
        
        // Verify cache population
        val cached = Ghost.getSerializer(MockUser::class)
        assertTrue(cached != null, "Prewarm must populate the global serializer cache")
    }
}
