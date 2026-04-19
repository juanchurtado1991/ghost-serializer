package com.ghost.serialization.integration

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import com.ghost.serialization.core.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GhostRobustnessTest {

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

    @Test
    fun fullDeserializationOfGodObject() {
        val reader = GhostJsonReader(Buffer().writeUtf8(FULL_GOD_OBJECT_JSON))
        val result = GodObjectSerializer.deserialize(reader)

        assertEquals(42, result.id)
        assertEquals(Long.MAX_VALUE, result.userId)
        assertEquals("Ghost Robust", result.name)
        assertEquals("ghost@serialization.io", result.email)
        assertEquals(99.99, result.score, 0.001)
        assertEquals(true, result.isActive)

        assertEquals(30, result.nullableAge)
        assertNull(result.nullableName)
        assertEquals(3.14, result.nullableScore!!, 0.001)

        assertEquals("admin", result.defaultRole)
        assertEquals(Priority.HIGH, result.defaultPriority)
        assertEquals(7, result.defaultCount)

        assertEquals(Priority.CRITICAL, result.priority)
        assertEquals(listOf("kotlin", "ghost", "robust"), result.tags)
        assertEquals(3, result.scores.size)
        assertEquals(mapOf("env" to "prod", "region" to "us-east-1"), result.metadata)

        assertEquals("123 Ghost Blvd", result.address.street)
        assertEquals("MX", result.address.country)

        assertEquals(2, result.tagObjects.size)
        assertEquals("tier", result.tagObjects[0].key)

        assertEquals("root", result.nestedTree.label)
        assertEquals(2, result.nestedTree.children!!.size)
        assertEquals("leaf", result.nestedTree.children!![0].children!![0].label)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))

        assertNull(result.nullableAge)
        assertNull(result.nullableName)
        assertNull(result.nullableScore)
        assertEquals("viewer", result.defaultRole)
        assertEquals(Priority.LOW, result.defaultPriority)
        assertEquals(0, result.defaultCount)
        assertEquals("US", result.address.country)
    }

    @Test
    fun nullOnNonNullableFieldThrows() {
        val json = """{"id": null, "userId": 1, "name": "x", "email": "x", "score": 1.0, "rating": 1.0, "isActive": true, "biography": "x", "priority": "LOW", "tags": [], "scores": [], "metadata": {}, "address": {"street": "x", "city": "x", "zipCode": "x"}, "tagObjects": [], "nestedTree": {"label": "x"}}"""
        assertFailsWith<Exception> {
            GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        }
    }

    @Test
    fun serializeDeserializeRoundtripParity() {
        val original = GodObject(
            id = 99, userId = 1234567890123456789L,
            name = "Roundtrip Test", email = "round@trip.io",
            score = 42.42, rating = 2.7f, isActive = true,
            biography = "A\tB\nC",
            nullableAge = 25, nullableName = "Nullable", nullableScore = null,
            defaultRole = "editor", defaultPriority = Priority.MEDIUM, defaultCount = 3,
            priority = Priority.HIGH,
            tags = listOf("alpha", "beta"), scores = listOf(10.0, 20.0),
            metadata = mapOf("k1" to "v1"),
            address = Address("1st", "City", "12345", "JP"),
            tagObjects = listOf(Tag("a", "b")),
            nestedTree = NestedContainer("root", listOf(NestedContainer("leaf")))
        )

        val buffer = Buffer()
        GodObjectSerializer.serialize(buffer, original)
        val json = buffer.readUtf8()

        val deserialized = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))

        assertEquals(original.id, deserialized.id)
        assertEquals(original.userId, deserialized.userId)
        assertEquals(original.name, deserialized.name)
        assertEquals(original.score, deserialized.score, 0.001)
        assertEquals(original.isActive, deserialized.isActive)
        assertEquals(original.biography, deserialized.biography)
        assertEquals(original.nullableAge, deserialized.nullableAge)
        assertNull(deserialized.nullableScore)
        assertEquals(original.priority, deserialized.priority)
        assertEquals(original.tags, deserialized.tags)
        assertEquals(original.metadata, deserialized.metadata)
        assertEquals(original.address.street, deserialized.address.street)
        assertEquals(original.nestedTree.label, deserialized.nestedTree.label)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        assertEquals(emptyList(), result.tags)
        assertEquals(emptyList(), result.scores)
        assertEquals(emptyMap(), result.metadata)
        assertEquals(emptyList(), result.tagObjects)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        assertEquals("漢字テスト🚀", result.name)
        assertEquals(listOf("日本語", "中文"), result.tags)
        assertEquals("🌍", result.nestedTree.label)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        val deepest = result.nestedTree
            .children!![0].children!![0].children!![0].children!![0]
        assertEquals("L4", deepest.label)
        assertNull(deepest.children)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        assertEquals(1, result.id)
        assertEquals("Skip", result.name)
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
        val result = GodObjectSerializer.deserialize(GhostJsonReader(Buffer().writeUtf8(json)))
        assertEquals(55, result.id)
        assertEquals("Reversed", result.name)
        assertEquals(77.7, result.score, 0.001)
        assertEquals(Priority.HIGH, result.priority)
        assertEquals("rev", result.nestedTree.label)
    }
}
