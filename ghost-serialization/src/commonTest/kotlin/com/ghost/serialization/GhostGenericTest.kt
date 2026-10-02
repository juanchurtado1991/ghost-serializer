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
        assertNotNull(
            actual = serializer,
            message = "Serializer should not be null for List<String>"
        )
        assertTrue(
            actual = serializer is ListSerializer<*>,
            message = "Serializer should be ListSerializer"
        )
    }

    @Test
    fun testMapResolutionInKMP() {
        val type = typeOf<Map<String, Int>>()
        val serializer = Ghost.getSerializer(type)
        assertNotNull(
            actual = serializer,
            message = "Serializer should not be null for Map<String, Int>"
        )
        assertTrue(
            actual = serializer is MapSerializer<*>,
            message = "Serializer should be MapSerializer"
        )
    }
}
