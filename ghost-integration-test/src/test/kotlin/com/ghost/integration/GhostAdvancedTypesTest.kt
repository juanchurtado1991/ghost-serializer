package com.ghost.serialization.integration

import com.ghost.integration.model.GhostAdvancedProfile
import com.ghost.integration.model.GhostShape
import com.ghost.integration.model.GhostUserToken
import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostAdvancedTypesTest {

    @Test
    fun testValueClassRoundtrip() {
        val original = GhostUserToken("secret_123")
        val json = Ghost.serialize(original)
        // Value class should be unboxed to a simple string in JSON
        assertEquals("\"secret_123\"", json)
        
        val deserialized = Ghost.deserialize<GhostUserToken>(json)
        assertEquals(original, deserialized)
    }

    @Test
    fun testSealedClassPolymorphism() {
        val circle: GhostShape = GhostShape.Circle(5.0)
        val square: GhostShape = GhostShape.Square(10.0)
        
        val jsonCircle = Ghost.serialize(circle)
        val jsonSquare = Ghost.serialize(square)
        
        val decodedCircle = Ghost.deserialize<GhostShape>(jsonCircle)
        val decodedSquare = Ghost.deserialize<GhostShape>(jsonSquare)
        
        assertEquals(circle, decodedCircle)
        assertEquals(square, decodedSquare)
    }

    @Test
    fun testNestedAdvancedTypes() {
        val profile = GhostAdvancedProfile(
            token = GhostUserToken("abc"),
            shapes = listOf(GhostShape.Circle(1.0), GhostShape.Square(2.0))
        )
        
        val json = Ghost.serialize(profile)
        val decoded = Ghost.deserialize<GhostAdvancedProfile>(json)
        
        assertEquals(profile, decoded)
    }
}
