package com.ghost.serialization

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/** Top-level collections whose type argument is nullable decode and encode JSON `null` elements. */
class NullableTypeArgumentTest {

    @Test
    fun nullableElementsRoundTripOnEveryChannel() {
        val json = "[1,null,3]"
        listOf(
            Ghost.deserialize<List<Int?>>(json = json),
            Ghost.deserialize<List<Int?>>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<List<Int?>>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = listOf(1, null, 3),
                actual = decoded
            )
        }
        assertEquals(
            expected = json,
            actual = Ghost.encodeToString(value = listOf(1, null, 3))
        )
    }

    @Test
    fun nullableMapValuesAndNestedListsRoundTrip() {
        val mapJson = "{\"a\":null,\"b\":\"x\"}"
        assertEquals(
            expected = mapOf("a" to null, "b" to "x"),
            actual = Ghost.deserialize<Map<String, String?>>(json = mapJson)
        )
        val nestedJson = "[[\"a\",null],null]"
        val nested = Ghost.deserialize<List<List<String?>?>>(json = nestedJson)
        assertEquals(
            expected = listOf(listOf("a", null), null),
            actual = nested
        )
        assertEquals(
            expected = nestedJson,
            actual = Ghost.encodeToString(value = nested)
        )
    }
}
