package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.exception.hintForJsonError
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
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
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.strings.beginObject as stringBeginObject
import com.ghost.serialization.parser.strings.nextInt as stringNextInt
import com.ghost.serialization.parser.strings.selectNameAndConsume as stringSelectNameAndConsume
import com.ghost.serialization.proto.GhostProtoConstants as PC

@OptIn(InternalGhostApi::class)
class GhostJsonPathErrorTest {

    private fun flat(json: String) = GhostJsonReader(json.encodeToByteArray())
    private fun string(json: String) = GhostJsonStringReader(rawData = json)
    private fun streaming(json: String) = GhostJsonReader(json.encodeToByteArray())

    @Test
    fun pathIncludesNestedObjectFieldOnTypeError() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("name", "age")
        val json = """{"user":{"name":"Ada","age":"oops"}}"""
        val r = flat(json = json)
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = userOptions)
        )
        r.nextString()
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = userOptions)
        )
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
        assertTrue(actual = ex.message.contains("$.user.age"))
        assertTrue(actual = ex.hint != null && ex.hint!!.contains("coerceStringsToNumbers"))
        assertTrue(actual = ex.message.contains("Hint:"))
    }

    @Test
    fun stringReaderMatchesFlatPathAndHint() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("age")
        val r = string(json = """{"user":{"age":"x"}}""")
        r.stringBeginObject()
        assertEquals(
            expected = 0,
            actual = r.stringSelectNameAndConsume(options)
        )
        r.stringBeginObject()
        assertEquals(
            expected = 0,
            actual = r.stringSelectNameAndConsume(userOptions)
        )
        val ex = assertFailsWith<GhostJsonException> { r.stringNextInt() }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun streamingReaderMatchesFlatPathAndHint() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("age")
        val r = streaming(json = """{"user":{"age":"x"}}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = userOptions)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun pathIncludesDeepNesting() {
        val a = JsonReaderOptions.of("a")
        val b = JsonReaderOptions.of("b")
        val c = JsonReaderOptions.of("c")
        val r = flat(json = """{"a":{"b":[{"c":true}]}}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = a)
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = b)
        )
        r.beginArray()
        assertTrue(actual = r.hasNext())
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = c)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$.a.b[0].c",
            actual = ex.path
        )
    }

    @Test
    fun pathUsesBracketFormForSpecialKeys() {
        val options = JsonReaderOptions.of("@type")
        val r = flat(json = """{"@type":"oops"}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$['@type']",
            actual = ex.path
        )
    }

    @Test
    fun pathIncludesArrayIndex() {
        val options = JsonReaderOptions.of("ids")
        val json = """{"ids":[1,2,"x"]}"""
        val r = flat(json = json)
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginArray()
        assertTrue(actual = r.hasNext())
        r.nextInt()
        assertTrue(actual = r.hasNext())
        r.nextInt()
        assertTrue(actual = r.hasNext())
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals(
            expected = "$.ids[2]",
            actual = ex.path
        )
    }

    @Test
    fun pathRootWhenErrorBeforeAnyField() {
        val r = flat(json = """[""")
        val ex = assertFailsWith<GhostJsonException> {
            r.beginObject()
        }
        assertEquals(
            expected = "$",
            actual = ex.path
        )
        assertTrue(actual = ex.hint != null && ex.hint!!.contains("object"))
    }

    @Test
    fun strictUnknownFieldIncludesHint() {
        val options = JsonReaderOptions.of("id")
        val r = GhostJsonReader("""{"id":1,"extra":true}""".encodeToByteArray(), strictMode = true)
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextInt()
        val ex = assertFailsWith<GhostJsonException> {
            r.selectString(options = options)
        }
        assertTrue(actual = ex.message.contains("Unknown field"))
        assertTrue(actual = ex.hint != null && ex.hint!!.contains("strictMode"))
    }

    @Test
    fun successfulParseLeavesNoStalePathOnNextError() {
        val options = JsonReaderOptions.of("a", "b")
        val r = flat(json = """{"a":1,"b":true}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextInt()
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextBoolean()
        r.endObject()

        r.reset("""{"a":"bad"}""".encodeToByteArray())
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> {
            r.nextInt()
        }
        assertEquals(
            expected = "$.a",
            actual = ex.path
        )
    }

    @Test
    fun readListPathOnElementTypeError() {
        val options = JsonReaderOptions.of("nums")
        val json = """{"nums":[10,11,false]}"""
        val r = flat(json = json)
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> {
            r.readList { r.nextInt() }
        }
        assertEquals(
            expected = "$.nums[2]",
            actual = ex.path
        )
    }

    @Test
    fun decodeResilientRestoresPathForLaterError() {
        val options = JsonReaderOptions.of("ok", "bad")
        val r = flat(json = """{"ok":1,"bad":"x"}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val recovered = r.decodeResilient { r.nextInt() }
        assertEquals(
            expected = 1,
            actual = recovered
        )
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$.bad",
            actual = ex.path
        )
    }

    @Test
    fun throwMissingRequiredFieldAppendsKeyToPath() {
        val r = flat(json = """{"id":1}""")
        r.beginObject()
        val options = JsonReaderOptions.of("id")
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextInt()
        // Still inside object (before endObject) — mirrors codegen validate-before-endObject.
        val ex = assertFailsWith<GhostJsonException> {
            r.throwMissingRequiredField(jsonName = "name")
        }
        assertEquals(
            expected = "$.name",
            actual = ex.path
        )
        assertTrue(actual = ex.hint!!.contains("nullable") || ex.hint!!.contains("@GhostName"))
    }

    @Test
    fun protoJsonReaderKeepsPathAndProtoHint() {
        val options = JsonReaderOptions.of("code")
        val enumOpts = JsonReaderOptions.of("OK", "FAIL")
        val r = GhostProtoJsonFlatReader(rawData = """{"code":"WEIRD"}""".encodeToByteArray())
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> {
            r.nextProtoEnum(options = enumOpts)
        }
        assertEquals(
            expected = "$.code",
            actual = ex.path
        )
        assertTrue(actual = ex.hint != null && ex.hint!!.contains("enum", ignoreCase = true))
    }

    @Test
    fun hintForJsonErrorCoversPriorityPrefixes() {
        assertNotNull(actual = (EM.STRICT_MODE_UNKNOWN_FIELD + "x").hintForJsonError())
        assertNotNull(actual = EM.ERR_COERCION_DISABLED.hintForJsonError())
        assertNotNull(actual = EM.ERR_EXPECTED_BOOLEAN.hintForJsonError())
        assertNotNull(actual = EM.ERR_TRAILING_COMMA.hintForJsonError())
        assertNotNull(actual = EM.ERR_NON_FINITE.hintForJsonError())
        assertNotNull(actual = EM.ERR_LEADING_ZEROS.hintForJsonError())
        assertNotNull(actual = EM.ERR_DEPTH_EXCEEDED.hintForJsonError())
        assertNotNull(actual = EM.ERR_MAX_COLLECTION_SIZE.hintForJsonError())
        assertNotNull(actual = EM.UNTERMINATED_STRING_ERROR.hintForJsonError())
        assertNotNull(actual = EM.ERR_EXPECTED_BEGIN_OBJ.hintForJsonError())
        assertNotNull(actual = EM.ERR_EXPECTED_BEGIN_ARR.hintForJsonError())
        assertNotNull(actual = EM.ERR_EXPECTED_STRING.hintForJsonError())
        assertNotNull(actual = EM.ERR_EXPECTED_NUMBER.hintForJsonError())
        assertNotNull(actual = (EM.ERR_REQUIRED_FIELD_PREFIX + "id" + EM.ERR_REQUIRED_FIELD_SUFFIX).hintForJsonError())
        assertNotNull(actual = EM.ERR_MISSING_DISCRIMINATOR.hintForJsonError())
        assertNotNull(actual = (EM.ERR_UNKNOWN_DISCRIMINATOR_PREFIX + "Foo").hintForJsonError())
        assertNotNull(actual = EM.ERR_INVALID_ENUM_VALUE.hintForJsonError())
        assertNotNull(actual = (EM.ERR_UNEXPECTED_ENUM_INDEX_PREFIX + "3").hintForJsonError())
        assertNotNull(actual = EM.ERR_UNKNOWN_ENUM.hintForJsonError())
        assertNotNull(actual = PC.ERR_INVALID_BASE64.hintForJsonError())
        assertNotNull(actual = PC.ERR_PROTO_FRACTIONAL_INT.hintForJsonError())
        assertNull(actual = "Completely unknown parser noise".hintForJsonError())
    }

    @Test
    fun happyPathLeavesTrackerEmptyAfterEndObject() {
        val options = JsonReaderOptions.of("a")
        val r = flat(json = """{"a":1}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextInt()
        r.endObject()
        // Next error at root should report `$` (no stale breadcrumbs).
        val ex = assertFailsWith<GhostJsonException> { r.beginArray() }
        assertEquals(
            expected = "$",
            actual = ex.path
        )
    }

    @Test
    fun readMapFinishesPathSoSiblingErrorIsClean() {
        val options = JsonReaderOptions.of("meta", "age")
        val r = flat(json = """{"meta":{"k":"v"},"age":true}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.readMap(keyParser = { r.nextKey()!! }, valueParser = { r.nextString() })
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$.age",
            actual = ex.path
        )
    }

    @Test
    fun nextCharFinishesPathSoSiblingErrorIsClean() {
        val options = JsonReaderOptions.of("ch", "age")
        val r = flat(json = """{"ch":"A","age":true}""")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        assertEquals(
            expected = 'A',
            actual = r.nextChar()
        )
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostJsonException> { r.nextInt() }
        assertEquals(
            expected = "$.age",
            actual = ex.path
        )
    }
}
