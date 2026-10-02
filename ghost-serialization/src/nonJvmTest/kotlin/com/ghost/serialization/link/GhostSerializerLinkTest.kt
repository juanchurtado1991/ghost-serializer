@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.link

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Kotlin/Native and Kotlin/Wasm resolution through `@GhostSerializerLink`, attached by
 * `ghost-compiler-plugin` (applied to these test compilations in `build.gradle.kts`) — no test
 * here calls `Ghost.addRegistry` except the one proving an explicit registry takes precedence.
 */
class GhostSerializerLinkTest {

    @BeforeTest
    fun resetGhost() {
        Ghost.resetForTest()
    }

    @Test
    fun deserialize_annotatedClassDecodesWithoutRegistry() {
        val device = Ghost.deserialize<LinkedDevice>(json = EMPTY_OBJECT_JSON)

        assertEquals(
            expected = LinkedDeviceSerializer.DECODED_ID,
            actual = device.id
        )
    }

    @Test
    fun deserialize_unannotatedClassErrorExplainsRegistration() {
        val error = assertFailsWith<IllegalArgumentException> {
            Ghost.deserialize<UnlinkedDevice>(json = EMPTY_OBJECT_JSON)
        }

        val message = error.message.orEmpty()
        assertTrue(
            actual = message.contains(other = Ghost.NOT_FOUND),
            message = message
        )
        assertTrue(
            actual = message.contains(other = ADD_REGISTRY_HINT),
            message = message
        )
    }

    @Test
    fun getSerializer_annotatedClassResolvesLinkedSerializer() {
        assertSame(
            expected = LinkedDeviceSerializer,
            actual = Ghost.getSerializer(clazz = LinkedDevice::class)
        )
    }

    @Test
    fun getSerializer_explicitRegistryTakesPrecedenceOverLink() {
        Ghost.addRegistry(registry = LinkedDeviceOverrideRegistry)

        assertSame(
            expected = LinkedDeviceOverrideSerializer,
            actual = Ghost.getSerializer(clazz = LinkedDevice::class)
        )
    }

    @Test
    fun getSerializer_sealedSubclassResolvesParentSerializer() {
        assertSame<Any?>(
            expected = LinkedShapeSerializer,
            actual = Ghost.getSerializer(clazz = LinkedShape.Circle::class)
        )
    }

    @Test
    fun getSerializer_unannotatedClassReturnsNull() {
        val serializer: GhostSerializer<UnlinkedDevice>? = Ghost.getSerializer(clazz = UnlinkedDevice::class)

        assertNull(actual = serializer)
    }

    private object LinkedDeviceOverrideRegistry : AbstractGhostRegistry() {
        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
            mapOf(LinkedDevice::class to LinkedDeviceOverrideSerializer)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getSerializer(
            clazz: KClass<T>
        ): GhostSerializer<T>? = getAllSerializers()[clazz] as GhostSerializer<T>?
    }

    private companion object {
        const val ADD_REGISTRY_HINT = "Ghost.addRegistry"
        const val EMPTY_OBJECT_JSON = "{}"
    }
}
