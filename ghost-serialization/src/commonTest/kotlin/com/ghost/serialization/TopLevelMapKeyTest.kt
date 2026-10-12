package com.ghost.serialization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * JSON object keys are strings, so a top-level map needs a [String] key type, as in generated
 * models: any other key type has no serializer instead of yielding a map whose keys are Strings.
 */
class TopLevelMapKeyTest {

    @Test
    fun nonStringKeysHaveNoSerializer() {
        assertFailsWith<IllegalArgumentException> {
            Ghost.deserialize<Map<Int, String>>(json = "{\"1\":\"a\"}")
        }
        assertFailsWith<IllegalArgumentException> {
            Ghost.deserialize<List<Map<Long, Int>>>(json = "[{\"1\":2}]")
        }
        assertFailsWith<IllegalArgumentException> {
            Ghost.encodeToString(value = mapOf(1 to "a"))
        }
    }

    @Test
    fun stringKeysStillRoundTrip() {
        val json = "{\"1\":\"a\"}"
        val decoded = Ghost.deserialize<Map<String, String>>(json = json)
        assertEquals(
            expected = mapOf("1" to "a"),
            actual = decoded
        )
        assertEquals(
            expected = json,
            actual = Ghost.encodeToString(value = decoded)
        )
    }
}
