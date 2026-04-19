package com.ghostserializer
import kotlin.test.assertTrue

import com.ghostserializer.annotations.GhostSerialization
import com.ghostserializer.serializers.ListSerializer
import com.ghostserializer.serializers.MapSerializer
import kotlin.reflect.typeOf
import kotlin.test.*

@GhostSerialization
data class TestUser(val id: Int, val name: String)

class GhostGenericTest {

    @Test
    fun testListResolutionInKMP() {
        // Given
        val type = typeOf<List<String>>()
        
        // When
        val serializer = Ghost.getSerializer(type)
        
        // Then
        assertNotNull(serializer, "Serializer should not be null for List<String>")
        assertTrue(serializer is ListSerializer<*>, "Serializer should be ListSerializer")
    }

    @Test
    fun testMapResolutionInKMP() {
        // Given
        val type = typeOf<Map<String, Int>>()
        
        // When
        val serializer = Ghost.getSerializer(type)
        
        // Then
        assertNotNull(serializer, "Serializer should not be null for Map<String, Int>")
        assertTrue(serializer is MapSerializer<*>, "Serializer should be MapSerializer")
    }
}
