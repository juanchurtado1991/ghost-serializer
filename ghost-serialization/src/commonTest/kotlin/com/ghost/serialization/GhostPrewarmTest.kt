@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertTrue

@Suppress("UNCHECKED_CAST")
class GhostPrewarmTest {

    class MockUser(val id: Int, val name: String)

    class MockUserSerializer : AbstractGhostSerializer<MockUser>() {
        override val typeName: String = "MockUser"
        var warmupCalled = false
        override fun serialize(
            writer: GhostJsonWriter,
            value: MockUser
        ) {
        }

        override fun deserialize(reader: GhostJsonReader): MockUser {
            return MockUser(id = 1, name = "test")
        }

        override fun warmUp() {
            warmupCalled = true
        }
    }

    class MockRegistry : AbstractGhostRegistry() {
        val serializer = MockUserSerializer()
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return if (clazz == MockUser::class) serializer as GhostSerializer<T> else null
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(MockUser::class to serializer)
        }

    }

    @Test
    fun testDeepPrewarmInducesWarmup() {
        val registry = MockRegistry()
        Ghost.addRegistry(registry = registry)

        Ghost.prewarm()

        assertTrue(
            actual = registry.serializer.warmupCalled,
            message = "Deep Prewarm must call warmUp() on serializers to induce JIT optimization"
        )

        // Verify cache population
        val cached = Ghost.getSerializer(MockUser::class)
        assertTrue(
            actual = cached != null,
            message = "Prewarm must populate the global serializer cache"
        )
    }
}
