package com.ghost.serialization

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
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
