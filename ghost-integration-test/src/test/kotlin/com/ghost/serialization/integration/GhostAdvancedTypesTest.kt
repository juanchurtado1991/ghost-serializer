@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.DecimalStress
import com.ghost.serialization.integration.model.EmojiKeyModel
import com.ghost.serialization.integration.model.GhostAdvancedProfile
import com.ghost.serialization.integration.model.GhostKindEvent
import com.ghost.serialization.integration.model.GhostShape
import com.ghost.serialization.integration.model.GhostUserToken
import com.ghost.serialization.integration.model.GodObject
import com.ghost.serialization.integration.model.NestedGenericModel
import com.ghost.serialization.integration.model.OverlappingKeyModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostAdvancedTypesTest {

    @Test
    fun testValueClassRoundtrip() {
        val original = GhostUserToken(value = "secret_123")
        val json = Ghost.serialize(original)
        // Value class should be unboxed to a simple string in JSON
        assertEquals(expected = "\"secret_123\"", actual = json)

        val deserialized = Ghost.deserialize<GhostUserToken>(json)
        assertEquals(expected = original, actual = deserialized)
    }

    @Test
    fun testSealedClassPolymorphism() {
        val circle: GhostShape = GhostShape.Circle(5.0)
        val square: GhostShape = GhostShape.Square(side = 10.0)

        val jsonCircle = Ghost.serialize(circle)
        val jsonSquare = Ghost.serialize(square)

        val decodedCircle = Ghost.deserialize<GhostShape>(jsonCircle)
        val decodedSquare = Ghost.deserialize<GhostShape>(jsonSquare)

        assertEquals(expected = circle, actual = decodedCircle)
        assertEquals(expected = square, actual = decodedSquare)
    }

    @Test
    fun testNestedAdvancedTypes() {
        val profile = GhostAdvancedProfile(
            token = GhostUserToken(value = "abc"),
            shapes = listOf(GhostShape.Circle(1.0), GhostShape.Square(side = 2.0))
        )

        val json = Ghost.serialize(profile)
        val decoded = Ghost.deserialize<GhostAdvancedProfile>(json)

        assertEquals(expected = profile, actual = decoded)
    }

    @Test
    fun testDeeplyNestedGenerics() {
        val original = NestedGenericModel(
            data = mapOf(
                "level1" to listOf(
                    mapOf("item1" to 1, "item2" to 2),
                    mapOf("item3" to 3)
                )
            )
        )
        val json = Ghost.serialize(original)
        val decoded =
            Ghost.deserialize<NestedGenericModel>(json)
        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun testEmojiKeys() {
        val original = EmojiKeyModel(
            familyName = "family",
            rocketCount = 100,
            emojiMap = mapOf("👨‍👩‍👧‍👦" to "family", "🚀" to "rocket")
        )
        val json = Ghost.serialize(original)
        assertTrue(actual = json.contains("👨‍👩‍👧‍👦"))
        assertTrue(actual = json.contains("🚀"))

        val decoded = Ghost.deserialize<EmojiKeyModel>(json)
        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun testOverlappingKeys() {
        val original = OverlappingKeyModel(
            id = 1,
            id_internal = 2,
            identity = "secret"
        )
        val json = Ghost.serialize(original)
        val decoded = Ghost.deserialize<OverlappingKeyModel>(json)
        assertEquals(expected = original, actual = decoded)
    }

    @Test
    fun testCustomDiscriminator() {
        val created = GhostKindEvent.Created(id = "1", name = "juan")
        val json = Ghost.serialize(created)

        assertTrue(actual = json.contains("\"kind\":\"Created\""), message = "Should use 'kind' as discriminator")

        val decoded =
            Ghost.deserialize<GhostKindEvent>(json)
        assertEquals(expected = created, actual = decoded)
    }

    @Test
    fun testDecimalPrecision() {
        val original = DecimalStress(
            big = 1.23456789E12,
            small = 0.00000123f,
            precise = 3.141592653589793
        )
        val json = Ghost.serialize(original)
        val decoded =
            Ghost.deserialize<DecimalStress>(json)

        assertEquals(
            expected = original.big,
            actual = decoded.big,
            absoluteTolerance = GhostIntegrationTestConstants.FLOAT_ASSERT_DELTA
        )
        assertEquals(expected = original.small, actual = decoded.small, absoluteTolerance = 0.0000001f)
        // Library fast-path supports 9 decimals
        assertEquals(original.precise, decoded.precise, 1.0E-9)
    }

    @Test
    fun testCircularReferenceProtection() {
        // Ghost has no explicit circular reference detector; the maxDepth check (default 255)
        // catches a simulated cycle instead.
        val depth = 300
        val nestedJson = "{\"next\":".repeat(depth) + "null" + "}".repeat(depth)

        kotlin.test.assertFailsWith<GhostJsonException> {
            Ghost.deserialize<GodObject>(nestedJson)
        }
    }
}

