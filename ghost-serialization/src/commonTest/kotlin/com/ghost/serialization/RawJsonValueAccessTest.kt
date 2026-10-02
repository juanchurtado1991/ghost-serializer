package com.ghost.serialization

import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawJsonValueAccessTest {

    private fun raw(json: String): RawJson = RawJson.fromString(json = json)

    @Test
    fun kindClassifiesAllJsonValueForms() {
        assertEquals(
            expected = RawJsonKind.OBJECT,
            actual = raw(json = """{"a":1}""").kind()
        )
        assertEquals(
            expected = RawJsonKind.ARRAY,
            actual = raw(json = """[1,2]""").kind()
        )
        assertEquals(
            expected = RawJsonKind.STRING,
            actual = raw(""""hello"""").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw("42").kind()
        )
        assertEquals(
            expected = RawJsonKind.NUMBER,
            actual = raw("-3.14").kind()
        )
        assertEquals(
            expected = RawJsonKind.BOOLEAN,
            actual = raw("true").kind()
        )
        assertEquals(
            expected = RawJsonKind.BOOLEAN,
            actual = raw("false").kind()
        )
        assertEquals(
            expected = RawJsonKind.NULL,
            actual = raw("null").kind()
        )
        assertEquals(
            expected = RawJsonKind.INVALID,
            actual = raw("").kind()
        )
    }

    @Test
    fun isJsonNullOnlyForNullLiteral() {
        assertTrue(actual = raw("null").isJsonNull)
        assertFalse(actual = raw("""{"x":null}""").isJsonNull)
        assertFalse(actual = raw(""""null"""").isJsonNull)
    }

    @Test
    fun asBooleanOrNull() {
        assertEquals(
            expected = true,
            actual = raw("true").asBooleanOrNull()
        )
        assertEquals(
            expected = false,
            actual = raw("false").asBooleanOrNull()
        )
        assertNull(actual = raw("null").asBooleanOrNull())
        assertNull(actual = raw("1").asBooleanOrNull())
    }

    @Test
    fun asIntAndLongOrNull_integerFormsOnly() {
        assertEquals(
            expected = 42,
            actual = raw("42").asIntOrNull()
        )
        assertEquals(
            expected = -7,
            actual = raw("-7").asIntOrNull()
        )
        assertEquals(
            expected = 42L,
            actual = raw("42").asLongOrNull()
        )
        assertNull(actual = raw("3.14").asIntOrNull())
        assertNull(actual = raw("1e3").asIntOrNull())
    }

    @Test
    fun asDoubleOrNull() {
        assertEquals(
            expected = 3.14,
            actual = raw("3.14").asDoubleOrNull()
        )
        assertEquals(
            expected = 1000.0,
            actual = raw("1e3").asDoubleOrNull()
        )
        assertEquals(
            expected = -2.0,
            actual = raw("-2").asDoubleOrNull()
        )
    }

    @Test
    fun asStringOrNull_decodesJsonStringContent() {
        assertEquals(
            expected = "off",
            actual = raw(""""off"""").asStringOrNull()
        )
        assertEquals(
            expected = "a\"b",
            actual = raw(""""a\"b"""").asStringOrNull()
        )
        assertNull(actual = raw("true").asStringOrNull())
    }

    @Test
    fun asDisplayString_scalarsAndStructured() {
        assertEquals(
            expected = "on",
            actual = raw(""""on"""").asDisplayString()
        )
        assertEquals(
            expected = "42",
            actual = raw("42").asDisplayString()
        )
        assertEquals(
            expected = "true",
            actual = raw("true").asDisplayString()
        )
        assertEquals(
            expected = "null",
            actual = raw("null").asDisplayString()
        )
        assertEquals(
            expected = """{"k":1}""",
            actual = raw("""{"k":1}""").asDisplayString()
        )
    }
}
