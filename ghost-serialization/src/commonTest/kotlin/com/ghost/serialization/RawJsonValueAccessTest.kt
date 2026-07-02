package com.ghost.serialization

import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RawJsonValueAccessTest {

    private fun raw(json: String): RawJson = RawJson.fromString(json)

    @Test
    fun kindClassifiesAllJsonValueForms() {
        assertEquals(RawJsonKind.OBJECT, raw("""{"a":1}""").kind())
        assertEquals(RawJsonKind.ARRAY, raw("""[1,2]""").kind())
        assertEquals(RawJsonKind.STRING, raw(""""hello"""").kind())
        assertEquals(RawJsonKind.NUMBER, raw("42").kind())
        assertEquals(RawJsonKind.NUMBER, raw("-3.14").kind())
        assertEquals(RawJsonKind.BOOLEAN, raw("true").kind())
        assertEquals(RawJsonKind.BOOLEAN, raw("false").kind())
        assertEquals(RawJsonKind.NULL, raw("null").kind())
        assertEquals(RawJsonKind.INVALID, raw("").kind())
    }

    @Test
    fun isJsonNullOnlyForNullLiteral() {
        assertTrue(raw("null").isJsonNull)
        assertFalse(raw("""{"x":null}""").isJsonNull)
        assertFalse(raw(""""null"""").isJsonNull)
    }

    @Test
    fun asBooleanOrNull() {
        assertEquals(true, raw("true").asBooleanOrNull())
        assertEquals(false, raw("false").asBooleanOrNull())
        assertNull(raw("null").asBooleanOrNull())
        assertNull(raw("1").asBooleanOrNull())
    }

    @Test
    fun asIntAndLongOrNull_integerFormsOnly() {
        assertEquals(42, raw("42").asIntOrNull())
        assertEquals(-7, raw("-7").asIntOrNull())
        assertEquals(42L, raw("42").asLongOrNull())
        assertNull(raw("3.14").asIntOrNull())
        assertNull(raw("1e3").asIntOrNull())
    }

    @Test
    fun asDoubleOrNull() {
        assertEquals(3.14, raw("3.14").asDoubleOrNull())
        assertEquals(1000.0, raw("1e3").asDoubleOrNull())
        assertEquals(-2.0, raw("-2").asDoubleOrNull())
    }

    @Test
    fun asStringOrNull_decodesJsonStringContent() {
        assertEquals("off", raw(""""off"""").asStringOrNull())
        assertEquals("a\"b", raw(""""a\"b"""").asStringOrNull())
        assertNull(raw("true").asStringOrNull())
    }

    @Test
    fun asDisplayString_scalarsAndStructured() {
        assertEquals("on", raw(""""on"""").asDisplayString())
        assertEquals("42", raw("42").asDisplayString())
        assertEquals("true", raw("true").asDisplayString())
        assertEquals("null", raw("null").asDisplayString())
        assertEquals("""{"k":1}""", raw("""{"k":1}""").asDisplayString())
    }
}
