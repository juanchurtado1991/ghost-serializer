@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.Address
import com.ghost.serialization.integration.model.ComplexObject
import com.ghost.serialization.integration.model.ComplexObjectSerializer
import com.ghost.serialization.integration.model.NestedContainer
import com.ghost.serialization.integration.model.Priority
import com.ghost.serialization.integration.model.Tag
import com.ghost.serialization.parser.streaming.GhostJsonReader
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull


class GhostRobustnessTest {

    @Test
    fun fullDeserializationOfGodObject() {
        val reader = GhostJsonReader(FULL_GOD_OBJECT_JSON.encodeToByteArray())
        val result = ComplexObjectSerializer.deserialize(reader)

        assertEquals(expected = 42, actual = result.id)
        assertEquals(expected = Long.MAX_VALUE, actual = result.userId)
        assertEquals(expected = "Ghost Robust", actual = result.name)
        assertEquals(expected = "ghost@serialization.io", actual = result.email)
        assertEquals(
            expected = 99.99,
            actual = result.score,
            absoluteTolerance = GhostIntegrationTestConstants.FLOAT_ASSERT_DELTA
        )
        assertEquals(expected = true, actual = result.isActive)

        assertEquals(expected = 30, actual = result.nullableAge)
        assertNull(actual = result.nullableName)
        assertEquals(
            expected = 3.14,
            actual = result.nullableScore!!,
            absoluteTolerance = GhostIntegrationTestConstants.FLOAT_ASSERT_DELTA
        )

        assertEquals(expected = "admin", actual = result.defaultRole)
        assertEquals(expected = Priority.HIGH, actual = result.defaultPriority)
        assertEquals(expected = 7, actual = result.defaultCount)

        assertEquals(expected = Priority.CRITICAL, actual = result.priority)
        assertEquals(expected = listOf("kotlin", "ghost", "robust"), actual = result.tags)
        assertEquals(expected = 3, actual = result.scores.size)
        assertEquals(expected = mapOf("env" to "prod", "region" to "us-east-1"), actual = result.metadata)

        assertEquals(expected = "123 Ghost Blvd", actual = result.address.street)
        assertEquals(expected = "MX", actual = result.address.country)

        assertEquals(expected = 2, actual = result.tagObjects.size)
        assertEquals(expected = "tier", actual = result.tagObjects[0].key)

        assertEquals(expected = "root", actual = result.nestedTree.label)
        assertEquals(expected = 2, actual = result.nestedTree.children!!.size)
        assertEquals(expected = "leaf", actual = result.nestedTree.children!![0].children!![0].label)
    }

    @Test
    fun missingFieldsUseKotlinDefaults() {
        val json = """
        {
            "id": 1, "userId": 100, "name": "Minimal", "email": "min@ghost.io",
            "score": 50.0, "rating": 3.0, "isActive": false, "biography": "short",
            "priority": "MEDIUM", "tags": [], "scores": [], "metadata": {},
            "address": {"street": "1st Ave", "city": "TestCity", "zipCode": "00000"},
            "tagObjects": [], "nestedTree": {"label": "solo"}
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))

        assertNull(actual = result.nullableAge)
        assertNull(actual = result.nullableName)
        assertNull(actual = result.nullableScore)
        assertEquals(expected = "viewer", actual = result.defaultRole)
        assertEquals(expected = Priority.LOW, actual = result.defaultPriority)
        assertEquals(expected = 0, actual = result.defaultCount)
        assertEquals(expected = "US", actual = result.address.country)
    }

    @Test
    fun nullOnNonNullableFieldThrows() {
        val json =
            """{"id": null, "userId": 1, "name": "x", "email": "x", "score": 1.0, "rating": 1.0, "isActive": true, "biography": "x", "priority": "LOW", "tags": [], "scores": [], "metadata": {}, "address": {"street": "x", "city": "x", "zipCode": "x"}, "tagObjects": [], "nestedTree": {"label": "x"}}"""
        assertFailsWith<Exception> {
            ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        }
    }

    @Test
    fun serializeDeserializeRoundtripParity() {
        val original = ComplexObject(
            id = 99, userId = 1234567890123456789L,
            name = "Roundtrip Test", email = "round@trip.io",
            score = 42.42, rating = 2.7f, isActive = true,
            biography = "A\tB\nC",
            nullableAge = 25, nullableName = "Nullable", nullableScore = null,
            defaultRole = "editor", defaultPriority = Priority.MEDIUM, defaultCount = 3,
            priority = Priority.HIGH,
            tags = listOf("alpha", "beta"), scores = listOf(10.0, 20.0),
            metadata = mapOf("k1" to "v1"),
            address = Address(street = "1st", city = "City", zipCode = "12345", country = "JP"),
            tagObjects = listOf(Tag(key = "a", value = "b")),
            nestedTree = NestedContainer(label = "root", children = listOf(NestedContainer(label = "leaf")))
        )

        val buffer = Buffer()
        ComplexObjectSerializer.serialize(buffer, original)
        val json = buffer.readUtf8()

        val deserialized =
            ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))

        assertEquals(expected = original.id, actual = deserialized.id)
        assertEquals(expected = original.userId, actual = deserialized.userId)
        assertEquals(expected = original.name, actual = deserialized.name)
        assertEquals(
            expected = original.score,
            actual = deserialized.score,
            absoluteTolerance = GhostIntegrationTestConstants.FLOAT_ASSERT_DELTA
        )
        assertEquals(expected = original.isActive, actual = deserialized.isActive)
        assertEquals(expected = original.biography, actual = deserialized.biography)
        assertEquals(expected = original.nullableAge, actual = deserialized.nullableAge)
        assertNull(actual = deserialized.nullableScore)
        assertEquals(expected = original.priority, actual = deserialized.priority)
        assertEquals(expected = original.tags, actual = deserialized.tags)
        assertEquals(expected = original.metadata, actual = deserialized.metadata)
        assertEquals(expected = original.address.street, actual = deserialized.address.street)
        assertEquals(expected = original.nestedTree.label, actual = deserialized.nestedTree.label)
    }

    @Test
    fun emptyCollectionsDeserializeCorrectly() {
        val json = """
        {
            "id": 1, "userId": 1, "name": "Empty", "email": "e@g.io",
            "score": 0.0, "rating": 0.0, "isActive": false, "biography": "",
            "priority": "LOW", "tags": [], "scores": [], "metadata": {},
            "address": {"street": "", "city": "", "zipCode": ""},
            "tagObjects": [], "nestedTree": {"label": "empty"}
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = emptyList(), actual = result.tags)
        assertEquals(expected = emptyList(), actual = result.scores)
        assertEquals(expected = emptyMap(), actual = result.metadata)
        assertEquals(expected = emptyList(), actual = result.tagObjects)
    }

    @Test
    fun unicodeStringsRoundtrip() {
        val json = """
        {
            "id": 1, "userId": 1, "name": "漢字テスト🚀", "email": "emoji@test.io",
            "score": 0.0, "rating": 0.0, "isActive": true,
            "biography": "Héllo Wörld \u00e9\u00e8\u00ea",
            "priority": "LOW", "tags": ["日本語", "中文"],
            "scores": [], "metadata": {"emoji": "🎉"},
            "address": {"street": "Ñoño St", "city": "São Paulo", "zipCode": "00000"},
            "tagObjects": [], "nestedTree": {"label": "🌍"}
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = "漢字テスト🚀", actual = result.name)
        assertEquals(expected = listOf("日本語", "中文"), actual = result.tags)
        assertEquals(expected = "🌍", actual = result.nestedTree.label)
    }

    @Test
    fun deeplyNestedContainerDeserializes() {
        val json = """
        {
            "id": 1, "userId": 1, "name": "Deep", "email": "d@g.io",
            "score": 0.0, "rating": 0.0, "isActive": true, "biography": "",
            "priority": "LOW", "tags": [], "scores": [], "metadata": {},
            "address": {"street": "", "city": "", "zipCode": ""},
            "tagObjects": [],
            "nestedTree": {
                "label": "L0",
                "children": [{"label": "L1", "children": [{"label": "L2",
                    "children": [{"label": "L3", "children": [{"label": "L4"}]}]}]}]
            }
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        val deepest = result.nestedTree
            .children!![0].children!![0].children!![0].children!![0]
        assertEquals(expected = "L4", actual = deepest.label)
        assertNull(actual = deepest.children)
    }

    @Test
    fun extraUnknownFieldsAreSkippedSilently() {
        val json = """
        {
            "id": 1, "userId": 1, "name": "Skip", "email": "s@g.io",
            "UNKNOWN_STRING": "should be skipped",
            "UNKNOWN_OBJECT": {"nested": true, "deep": [1,2,3]},
            "UNKNOWN_ARRAY": [1, "two", null, false],
            "score": 1.0, "rating": 1.0, "isActive": true, "biography": "ok",
            "priority": "LOW", "tags": [], "scores": [], "metadata": {},
            "address": {"street": "x", "city": "x", "zipCode": "x"},
            "tagObjects": [], "nestedTree": {"label": "x"}
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = 1, actual = result.id)
        assertEquals(expected = "Skip", actual = result.name)
    }

    @Test
    fun reversedFieldOrderDeserializesCorrectly() {
        val json = """
        {
            "nestedTree": {"label": "rev"},
            "tagObjects": [{"key": "k", "value": "v"}],
            "address": {"street": "r", "city": "c", "zipCode": "z"},
            "metadata": {"rev": "true"}, "scores": [9.9], "tags": ["reversed"],
            "priority": "HIGH", "biography": "reversed bio",
            "isActive": false, "rating": 1.1, "score": 77.7,
            "email": "rev@g.io", "name": "Reversed", "userId": 999, "id": 55
        }
        """
        val result = ComplexObjectSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = 55, actual = result.id)
        assertEquals(expected = "Reversed", actual = result.name)
        assertEquals(
            expected = 77.7,
            actual = result.score,
            absoluteTolerance = GhostIntegrationTestConstants.FLOAT_ASSERT_DELTA
        )
        assertEquals(expected = Priority.HIGH, actual = result.priority)
        assertEquals(expected = "rev", actual = result.nestedTree.label)
    }

    private companion object {
        const val FULL_GOD_OBJECT_JSON = """
        {
            "id": 42,
            "userId": 9223372036854775807,
            "name": "Ghost Robust",
            "email": "ghost@serialization.io",
            "score": 99.99,
            "rating": 4.5,
            "isActive": true,
            "biography": "Line1\nLine2\tTabbed \"Quoted\" Back\\slash",
            "nullableAge": 30,
            "nullableName": null,
            "nullableScore": 3.14,
            "defaultRole": "admin",
            "defaultPriority": "HIGH",
            "defaultCount": 7,
            "priority": "CRITICAL",
            "tags": ["kotlin", "ghost", "robust"],
            "scores": [1.1, 2.2, 3.3],
            "metadata": {"env": "prod", "region": "us-east-1"},
            "address": {"street": "123 Ghost Blvd", "city": "Ghostville", "zipCode": "90210", "country": "MX"},
            "tagObjects": [{"key": "tier", "value": "premium"}, {"key": "plan", "value": "enterprise"}],
            "nestedTree": {"label": "root", "children": [{"label": "child1", "children": [{"label": "leaf"}]}, {"label": "child2"}]}
        }
        """
    }
}
