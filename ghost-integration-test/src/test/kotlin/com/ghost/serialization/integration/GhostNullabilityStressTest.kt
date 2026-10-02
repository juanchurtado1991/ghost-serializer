package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.DefaultValueNullModel
import com.ghost.serialization.integration.model.NullabilityStressModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GhostNullabilityStressTest {

    @Test
    fun testNestedNullablesRoundtrip() {
        val model = NullabilityStressModel(
            nullableList = listOf("A", null, "C"),
            nullableMap = mapOf("one" to 1, "two" to null),
            nestedNullable = listOf(listOf(1, null), null, listOf(3))
        )

        val json = Ghost.serialize(model)
        val decoded = Ghost.deserialize<NullabilityStressModel>(json)

        assertEquals(expected = model, actual = decoded)
    }

    @Test
    fun testAllNulls() {
        val model = NullabilityStressModel(nullableList = null, nullableMap = null, nestedNullable = null)
        val json = Ghost.serialize(model)
        assertEquals(expected = "{\"nullableList\":null,\"nullableMap\":null,\"nestedNullable\":null}", actual = json)

        val decoded = Ghost.deserialize<NullabilityStressModel>(json)
        assertNull(actual = decoded.nullableList)
        assertNull(actual = decoded.nullableMap)
        assertNull(actual = decoded.nestedNullable)
    }

    @Test
    fun testExplicitNullVsMissingKey() {
        val jsonMissing = "{}"
        val decoded1 = Ghost.deserialize<DefaultValueNullModel>(jsonMissing)
        assertEquals(expected = "Default", actual = decoded1.name)
        assertEquals(expected = 42, actual = decoded1.age)

        // Explicit null on a non-nullable field must fail, not fall back to the default
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<DefaultValueNullModel>("{\"name\":null}")
        }

        // Explicit null on a nullable field overrides its default instead of failing
        val jsonExplicitNull = "{\"age\":null}"
        val decoded3 = Ghost.deserialize<DefaultValueNullModel>(jsonExplicitNull)
        assertEquals(expected = "Default", actual = decoded3.name)
        assertNull(actual = decoded3.age)
    }
}
