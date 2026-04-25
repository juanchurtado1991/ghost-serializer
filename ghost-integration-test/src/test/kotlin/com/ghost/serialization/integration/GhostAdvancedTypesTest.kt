package com.ghost.serialization.integration

import com.ghost.serialization.integration.model.GhostAdvancedProfile
import com.ghost.serialization.integration.model.GhostShape
import com.ghost.serialization.integration.model.GhostUserToken
import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testDeeplyNestedGenerics() {
        val original = com.ghost.serialization.integration.model.NestedGenericModel(
            data = mapOf(
                "level1" to listOf(
                    mapOf("item1" to 1, "item2" to 2),
                    mapOf("item3" to 3)
                )
            )
        )
        val json = Ghost.serialize(original)
        val decoded = Ghost.deserialize<com.ghost.serialization.integration.model.NestedGenericModel>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testEmojiKeys() {
        val original = com.ghost.serialization.integration.model.EmojiKeyModel(
            familyName = "family",
            rocketCount = 100,
            emojiMap = mapOf("👨‍👩‍👧‍👦" to "family", "🚀" to "rocket")
        )
        val json = Ghost.serialize(original)
        assertTrue(json.contains("👨‍👩‍👧‍👦"))
        assertTrue(json.contains("🚀"))
        
        val decoded = Ghost.deserialize<com.ghost.serialization.integration.model.EmojiKeyModel>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testOverlappingKeys() {
        val original = com.ghost.serialization.integration.model.OverlappingKeyModel(
            id = 1,
            id_internal = 2,
            identity = "secret"
        )
        val json = Ghost.serialize(original)
        val decoded = Ghost.deserialize<com.ghost.serialization.integration.model.OverlappingKeyModel>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCustomDiscriminator() {
        val created = com.ghost.serialization.integration.model.GhostKindEvent.Created("1", "juan")
        val json = Ghost.serialize(created)
        
        // Should contain "kind":"Created"
        assertTrue(json.contains("\"kind\":\"Created\""), "Should use 'kind' as discriminator")
        
        val decoded = Ghost.deserialize<com.ghost.serialization.integration.model.GhostKindEvent>(json)
        assertEquals(created, decoded)
    }

    @Test
    fun testDecimalPrecision() {
        val original = com.ghost.serialization.integration.model.DecimalStress(
            big = 1.23456789E12,
            small = 0.00000123f,
            precise = 3.141592653589793
        )
        val json = Ghost.serialize(original)
        val decoded = Ghost.deserialize<com.ghost.serialization.integration.model.DecimalStress>(json)
        
        assertEquals(original.big, decoded.big, 0.001)
        assertEquals(original.small, decoded.small, 0.0000001f)
        // Library fast-path supports 9 decimals
        assertEquals(original.precise, decoded.precise, 1.0E-9)
    }

    @Test
    fun testCircularReferenceProtection() {
        // Since Ghost doesn't have an explicit circular reference detector yet,
        // it should be caught by the maxDepth check (default 255) during deserialization
        // if we try to simulate one.
        val depth = 300
        val nestedJson = "{\"next\":".repeat(depth) + "null" + "}".repeat(depth)
        
        // This should fail with GhostJsonException due to depth limit
        kotlin.test.assertFailsWith<com.ghost.serialization.core.exception.GhostJsonException> {
            Ghost.deserialize<com.ghost.serialization.integration.model.GodObject>(nestedJson)
        }
    }
}

