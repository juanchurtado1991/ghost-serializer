@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.peekStringField
import com.ghost.serialization.parser.strings.readList
import com.ghost.serialization.parser.strings.selectNameAndConsume
import com.ghost.serialization.parser.strings.selectString
import com.ghost.serialization.parser.strings.skipValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/** [com.ghost.serialization.parser.strings.GhostJsonStringReader] contract: field selection and peeking. */
@OptIn(InternalGhostApi::class)
class GhostStringReaderSelectTest {

    // ══════════════════════════════════════════════════════════════════
    // selectString / selectNameAndConsume
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun selectNameAndConsumeMatchesKnownKey() {
        val options = JsonReaderOptions.of("name")
        val reader = stringReaderOf(json = """{"name":"Ghost"}""")
        reader.beginObject()
        val index = reader.selectNameAndConsume(options = options)
        assertEquals(
            expected = 0,
            actual = index
        )
        assertEquals(
            expected = "Ghost",
            actual = reader.nextString()
        )
    }

    @Test
    fun selectNameAndConsumeReturnsNoneForUnknownKey() {
        val options = JsonReaderOptions.of("name")
        val reader = stringReaderOf(json = """{"other":"value"}""")
        reader.beginObject()
        val index = reader.selectNameAndConsume(options = options)
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = index
        )
        reader.skipValue()
    }

    @Test
    fun selectStringWithEmptyOptionsSkipsAllFields() {
        val options = JsonReaderOptions.of()
        val json = """{"a":1,"b":2}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()

        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = SCN.MATCH_END,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    @Test
    fun selectStringWithSingleOption() {
        val options = JsonReaderOptions.of("only")
        val reader = stringReaderOf(json = """{"only":"found"}""")
        reader.beginObject()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "found",
            actual = reader.nextString()
        )
        assertEquals(
            expected = SCN.MATCH_END,
            actual = reader.selectString(options = options)
        )
        reader.endObject()
    }

    @Test
    fun readsObjectWithMultipleArrays() {
        val options = JsonReaderOptions.of("a", "b")
        val json = """{"a":[1,2],"b":[3,4]}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        val list1 = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(1, 2),
            actual = list1
        )

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        val list2 = reader.readList { reader.nextInt() }
        assertEquals(
            expected = listOf(3, 4),
            actual = list2
        )
        reader.endObject()
    }

    @Test
    fun selectStringWithThreePrefixVariants() {
        val options = JsonReaderOptions.of("user", "userId", "userIds", "userName")
        val json = """{"userIds":[1],"userName":"g","userId":42,"user":"obj"}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()

        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }
        assertEquals(
            expected = 3,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "g",
            actual = reader.nextString()
        )
        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "obj",
            actual = reader.nextString()
        )
    }

    @Test
    fun selectStringWithLongFieldName() {
        val longName = "thisIsAVeryLongFieldNameThatExceedsNormalLengths"
        val options = JsonReaderOptions.of(longName)
        val json = """{"$longName":"found"}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "found",
            actual = reader.nextString()
        )
    }

    @Test
    fun selectStringWithSingleCharFields() {
        val options = JsonReaderOptions.of("a", "b", "c")
        val json = """{"b":2,"c":3,"a":1}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 2,
            actual = reader.nextInt()
        )
        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 3,
            actual = reader.nextInt()
        )
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
    }

    @Test
    fun selectStringWithUnderscoreFields() {
        val options = JsonReaderOptions.of("user_id", "user_name", "user_ids")
        val json = """{"user_ids":[1,2],"user_id":42,"user_name":"ghost"}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()

        assertEquals(
            expected = 2,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.readList { reader.nextInt() }

        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )

        assertEquals(
            expected = 1,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = "ghost",
            actual = reader.nextString()
        )
    }

    @Test
    fun skipsObjectContainingBracesInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = """{"junk":{"msg":"value with { and } inside"},"id":1}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 1,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsArrayContainingBracketsInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = """{"junk":["contains [ and ]"],"id":2}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 2,
            actual = reader.nextInt()
        )
    }

    @Test
    fun skipsObjectContainingEscapedQuotesInStrings() {
        val options = JsonReaderOptions.of("id")
        val json = "{\"junk\":{\"msg\":\"escaped \\\"quotes\\\" and {braces}\"},\"id\":3}"
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        reader.skipValue()
        assertEquals(
            expected = 0,
            actual = reader.selectString(options = options)
        )
        reader.consumeKeySeparator()
        assertEquals(
            expected = 3,
            actual = reader.nextInt()
        )
    }

    // ══════════════════════════════════════════════════════════════════
    // peekStringField
    // ══════════════════════════════════════════════════════════════════

    @Test
    fun peekStringFieldReturnsValueForKnownKey() {
        val json = """{"type":"USER","id":1}"""
        val reader = stringReaderOf(json = json)
        reader.beginObject()
        val typeValue = reader.peekStringField(name = "type")
        assertEquals(
            expected = "USER",
            actual = typeValue
        )
        // peekStringField must not advance position — the object is still parseable
        assertNotNull(actual = reader.nextKey())
    }

    @Test
    fun peekStringFieldReturnsNullForMissingKey() {
        val reader = stringReaderOf(json = """{"id":1}""")
        val typeValue = reader.peekStringField(name = "type")
        assertNull(actual = typeValue)
    }

    @Test
    fun peekStringFieldReturnsNullWhenValueIsNotString() {
        val reader = stringReaderOf(json = """{"v":42}""")
        val result = reader.peekStringField(name = "v")
        assertNull(actual = result)
    }

    @Test
    fun peekStringFieldFindsDiscriminatorAfterNestedObject() {
        val json = """{"meta":{"version":1},"type":"USER"}"""
        val reader = stringReaderOf(json = json)
        assertEquals(
            expected = "USER",
            actual = reader.peekStringField(name = "type")
        )
    }

    @Test
    fun peekStringFieldFindsDiscriminatorAfterNestedArray() {
        val json = """{"devices":[{"id":"hub-1"}],"pageType":"loggedIn"}"""
        val reader = stringReaderOf(json = json)
        assertEquals(
            expected = "loggedIn",
            actual = reader.peekStringField(name = "pageType")
        )
    }
}
