package com.ghost.serialization

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginArray
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.decodeResilient
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.hasNext
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextChar
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.readMap
import com.ghost.serialization.parser.streaming.selectNameAndConsume
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject as stringBeginObject
import com.ghost.serialization.parser.strings.nextInt as stringNextInt
import com.ghost.serialization.parser.strings.selectNameAndConsume as stringSelectNameAndConsume
import com.ghost.serialization.parser.common.GhostJsonConstants as C
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalGhostApi::class)
class GhostJsonPathErrorTest {

    private fun flat(json: String) = GhostJsonReader(json.encodeToByteArray())
    private fun string(json: String) = GhostJsonStringReader(json)
    private fun streaming(json: String) = GhostJsonReader(json.encodeToByteArray())

    @Test
    fun pathIncludesNestedObjectFieldOnTypeError() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("name", "age")
        val json = """{"user":{"name":"Ada","age":"oops"}}"""
        val r = flat(json)
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(userOptions))
        r.nextString()
        assertEquals(1, r.selectNameAndConsume(userOptions))
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals("$.user.age", ex.path)
        assertTrue(ex.message.contains("$.user.age"))
        assertTrue(ex.hint != null && ex.hint!!.contains("coerceStringsToNumbers"))
        assertTrue(ex.message.contains("Hint:"))
    }

    @Test
    fun stringReaderMatchesFlatPathAndHint() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("age")
        val r = string("""{"user":{"age":"x"}}""")
        r.stringBeginObject()
        assertEquals(0, r.stringSelectNameAndConsume(options))
        r.stringBeginObject()
        assertEquals(0, r.stringSelectNameAndConsume(userOptions))
        val ex = assertFailsWith<GhostJsonException> { r.stringNextInt() }
        assertEquals("$.user.age", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun streamingReaderMatchesFlatPathAndHint() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("age")
        val r = streaming("""{"user":{"age":"x"}}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(userOptions))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$.user.age", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun pathIncludesDeepNesting() {
        val a = JsonReaderOptions.of("a")
        val b = JsonReaderOptions.of("b")
        val c = JsonReaderOptions.of("c")
        val r = flat("""{"a":{"b":[{"c":true}]}}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(a))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(b))
        r.beginArray()
        assertTrue(r.hasNext())
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(c))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$.a.b[0].c", ex.path)
    }

    @Test
    fun pathUsesBracketFormForSpecialKeys() {
        val options = JsonReaderOptions.of("@type")
        val r = flat("""{"@type":"oops"}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$['@type']", ex.path)
    }

    @Test
    fun pathIncludesArrayIndex() {
        val options = JsonReaderOptions.of("ids")
        val json = """{"ids":[1,2,"x"]}"""
        val r = flat(json)
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginArray()
        assertTrue(r.hasNext())
        r.nextInt()
        assertTrue(r.hasNext())
        r.nextInt()
        assertTrue(r.hasNext())
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals("$.ids[2]", ex.path)
    }

    @Test
    fun pathRootWhenErrorBeforeAnyField() {
        val r = flat("""[""")
        val ex = assertFailsWith<GhostJsonException> {
            r.beginObject()
        }
        assertEquals("$", ex.path)
        assertTrue(ex.hint != null && ex.hint!!.contains("object"))
    }

    @Test
    fun strictUnknownFieldIncludesHint() {
        val options = JsonReaderOptions.of("id")
        val r = GhostJsonReader("""{"id":1,"extra":true}""".encodeToByteArray(), strictMode = true)
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.nextInt()
        val ex = assertFailsWith<GhostJsonException> {
            r.selectString(options)
        }
        assertTrue(ex.message.contains("Unknown field"))
        assertTrue(ex.hint != null && ex.hint!!.contains("strictMode"))
    }

    @Test
    fun successfulParseLeavesNoStalePathOnNextError() {
        val options = JsonReaderOptions.of("a", "b")
        val r = flat("""{"a":1,"b":true}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.nextInt()
        assertEquals(1, r.selectNameAndConsume(options))
        r.nextBoolean()
        r.endObject()

        r.reset("""{"a":"bad"}""".encodeToByteArray())
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals("$.a", ex.path)
    }

    @Test
    fun readListPathOnElementTypeError() {
        val options = JsonReaderOptions.of("nums")
        val json = """{"nums":[10,11,false]}"""
        val r = flat(json)
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> {
            r.readList { r.nextInt() }
        }
        assertEquals("$.nums[2]", ex.path)
    }

    @Test
    fun decodeResilientRestoresPathForLaterError() {
        val options = JsonReaderOptions.of("ok", "bad")
        val r = flat("""{"ok":1,"bad":"x"}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val recovered = r.decodeResilient { r.nextInt() }
        assertEquals(1, recovered)
        assertEquals(1, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$.bad", ex.path)
    }

    @Test
    fun throwMissingRequiredFieldAppendsKeyToPath() {
        val r = flat("""{"id":1}""")
        r.beginObject()
        val options = JsonReaderOptions.of("id")
        assertEquals(0, r.selectNameAndConsume(options))
        r.nextInt()
        // Still inside object (before endObject) — mirrors codegen validate-before-endObject.
        val ex = assertFailsWith<GhostJsonException> {
            r.throwMissingRequiredField("name")
        }
        assertEquals("$.name", ex.path)
        assertTrue(ex.hint!!.contains("nullable") || ex.hint!!.contains("@GhostName"))
    }

    @Test
    fun protoJsonReaderKeepsPathAndProtoHint() {
        val options = JsonReaderOptions.of("code")
        val enumOpts = JsonReaderOptions.of("OK", "FAIL")
        val r = GhostProtoJsonFlatReader("""{"code":"WEIRD"}""".encodeToByteArray())
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> {
            r.nextProtoEnum(enumOpts)
        }
        assertEquals("$.code", ex.path)
        assertTrue(ex.hint != null && ex.hint!!.contains("enum", ignoreCase = true))
    }

    @Test
    fun hintForJsonErrorCoversPriorityPrefixes() {
        assertNotNull(hintForJsonError(C.STRICT_MODE_UNKNOWN_FIELD + "x"))
        assertNotNull(hintForJsonError(C.ERR_COERCION_DISABLED))
        assertNotNull(hintForJsonError(C.ERR_EXPECTED_BOOLEAN))
        assertNotNull(hintForJsonError(C.ERR_TRAILING_COMMA))
        assertNotNull(hintForJsonError(C.ERR_NON_FINITE))
        assertNotNull(hintForJsonError(C.ERR_LEADING_ZEROS))
        assertNotNull(hintForJsonError(C.ERR_DEPTH_EXCEEDED))
        assertNotNull(hintForJsonError(C.ERR_MAX_COLLECTION_SIZE))
        assertNotNull(hintForJsonError(C.UNTERMINATED_STRING_ERROR))
        assertNotNull(hintForJsonError(C.ERR_EXPECTED_BEGIN_OBJ))
        assertNotNull(hintForJsonError(C.ERR_EXPECTED_BEGIN_ARR))
        assertNotNull(hintForJsonError(C.ERR_EXPECTED_STRING))
        assertNotNull(hintForJsonError(C.ERR_EXPECTED_NUMBER))
        assertNotNull(hintForJsonError(C.ERR_REQUIRED_FIELD_PREFIX + "id" + C.ERR_REQUIRED_FIELD_SUFFIX))
        assertNotNull(hintForJsonError(C.ERR_MISSING_DISCRIMINATOR))
        assertNotNull(hintForJsonError(C.ERR_UNKNOWN_DISCRIMINATOR_PREFIX + "Foo"))
        assertNotNull(hintForJsonError(C.ERR_INVALID_ENUM_VALUE))
        assertNotNull(hintForJsonError(C.ERR_UNEXPECTED_ENUM_INDEX_PREFIX + "3"))
        assertNotNull(hintForJsonError(C.ERR_UNKNOWN_ENUM))
        assertNotNull(hintForJsonError(C.ERR_INVALID_BASE64))
        assertNotNull(hintForJsonError(C.ERR_PROTO_FRACTIONAL_INT))
        assertNull(hintForJsonError("Completely unknown parser noise"))
    }

    @Test
    fun happyPathLeavesTrackerEmptyAfterEndObject() {
        val options = JsonReaderOptions.of("a")
        val r = flat("""{"a":1}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.nextInt()
        r.endObject()
        // Next error at root should report `$` (no stale breadcrumbs).
        val ex = assertFailsWith<GhostJsonException> { r.beginArray() }
        assertEquals("$", ex.path)
    }

    @Test
    fun readMapFinishesPathSoSiblingErrorIsClean() {
        val options = JsonReaderOptions.of("meta", "age")
        val r = flat("""{"meta":{"k":"v"},"age":true}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.readMap({ r.nextKey()!! }, { r.nextString() })
        assertEquals(1, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$.age", ex.path)
    }

    @Test
    fun nextCharFinishesPathSoSiblingErrorIsClean() {
        val options = JsonReaderOptions.of("ch", "age")
        val r = flat("""{"ch":"A","age":true}""")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        assertEquals('A', r.nextChar())
        assertEquals(1, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals("$.age", ex.path)
    }
}
