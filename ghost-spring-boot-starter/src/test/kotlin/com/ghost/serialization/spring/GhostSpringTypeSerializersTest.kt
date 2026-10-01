package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.spring.fixture.HelloMessage
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Unit coverage for [GhostSpringTypeSerializers]: top-level scalar exclusion must not
 * block collection element types (`List<String>`), and Map unwrap requires String keys.
 */
class GhostSpringTypeSerializersTest {

    private val typeSerializers = GhostSpringTypeSerializers()

    private interface Holder {
        fun strings(): List<String>
        fun ints(): List<Int>
        fun stringSet(): Set<String>
        fun stringMap(): Map<String, String>
        fun helloMap(): Map<String, HelloMessage>
        fun intKeyMap(): Map<Int, String>
    }

    @Test
    fun topLevelStringIsExcluded() {
        assertNull(actual = typeSerializers.getJsonSerializer(String::class.java))
    }

    @Test
    fun topLevelBoxedIntIsExcluded() {
        assertNull(actual = typeSerializers.getJsonSerializer(Int::class.javaObjectType))
    }

    @Test
    fun listOfStringResolves() {
        val type = Holder::class.java.getMethod("strings").genericReturnType
        val serializer = typeSerializers.getJsonSerializer(type)
        assertNotNull(actual = serializer)
        assertIs<ListSerializer<*>>(serializer)
    }

    @Test
    fun listOfIntResolves() {
        val type = Holder::class.java.getMethod("ints").genericReturnType
        assertNotNull(actual = typeSerializers.getJsonSerializer(type))
    }

    @Test
    fun setOfStringResolves() {
        val type = Holder::class.java.getMethod("stringSet").genericReturnType
        val serializer = typeSerializers.getJsonSerializer(type)
        assertNotNull(actual = serializer)
        assertIs<SetSerializer<*>>(serializer)
    }

    @Test
    fun mapStringStringResolves() {
        val type = Holder::class.java.getMethod("stringMap").genericReturnType
        val serializer = typeSerializers.getJsonSerializer(type)
        assertNotNull(actual = serializer)
        assertIs<MapSerializer<*>>(serializer)
    }

    @Test
    fun mapStringHelloResolves() {
        val type = Holder::class.java.getMethod("helloMap").genericReturnType
        assertNotNull(actual = typeSerializers.getJsonSerializer(type))
    }

    @Test
    fun mapWithNonStringKeyIsDeclined() {
        val type = Holder::class.java.getMethod("intKeyMap").genericReturnType
        assertNull(actual = typeSerializers.getJsonSerializer(type))
    }

    @Test
    fun registeredHelloMessageStillResolves() {
        val serializer = typeSerializers.getJsonSerializer(HelloMessage::class.java)
        assertNotNull(actual = serializer)
        assertSame(expected = Ghost.getSerializer(HelloMessage::class) as Any?, actual = serializer)
    }

    @Test
    fun stringSerializerIsAvailableViaGhostForElements() {
        assertSame(expected = StringSerializer as Any?, actual = Ghost.getSerializer(String::class))
    }
}
