package com.ghost.serialization

import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostGenericTest {

    @Test
    fun testListResolutionInKMP() {
        val type = typeOf<List<String>>()
        val serializer = Ghost.getSerializer(type)
        assertNotNull(serializer, "Serializer should not be null for List<String>")
        assertTrue(serializer is ListSerializer<*>, "Serializer should be ListSerializer")
    }

    @Test
    fun testMapResolutionInKMP() {
        val type = typeOf<Map<String, Int>>()
        val serializer = Ghost.getSerializer(type)
        assertNotNull(serializer, "Serializer should not be null for Map<String, Int>")
        assertTrue(serializer is MapSerializer<*>, "Serializer should be MapSerializer")
    }
}
