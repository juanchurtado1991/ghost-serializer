package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readAllDocuments
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.parser.yaml.reset
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.exception.hintForYamlError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cursor-phase JSONPath + hints for YAML typed decode.
 *
 * Parse-phase errors keep [GhostYamlException.path] at `"$"` on purpose (no AST yet).
 * Alias use-sites report the referencing path, not the anchor definition site.
 */
@OptIn(InternalGhostApi::class)
class GhostYamlPathErrorTest {

    private fun reader(yaml: String) = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())

    @Test
    fun pathIncludesNestedMappingFieldOnTypeError() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("name", "age")
        val r = reader(
            yaml = """
            user:
              name: Ada
              age: [1, 2]
            """.trimIndent()
        )
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
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
        assertNotNull(actual = ex.hint)
        assertTrue(actual = ex.message.contains("Hint:"))
    }

    @Test
    fun pathIncludesSequenceIndex() {
        val options = JsonReaderOptions.of("ids")
        val r = reader(
            yaml = """
            ids:
              - 1
              - 2
              - true
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginArray()
        assertTrue(actual = r.hasNextArrayElement())
        r.nextInt()
        assertTrue(actual = r.hasNextArrayElement())
        r.nextInt()
        assertTrue(actual = r.hasNextArrayElement())
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.ids[2]",
            actual = ex.path
        )
    }

    @Test
    fun aliasUseSiteReportsReferencingPathNotAnchorSite() {
        val options = JsonReaderOptions.of("base", "user")
        val ageOpts = JsonReaderOptions.of("age")
        val r = reader(
            yaml = """
            base: &b
              age:
                - 1
            user: *b
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.skipValue()
        assertEquals(
            expected = 1,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = ageOpts)
        )
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
    }

    @Test
    fun mergeKeyLooksLikeLocalFieldPath() {
        val options = JsonReaderOptions.of("user")
        val ageOpts = JsonReaderOptions.of("age")
        val r = reader(
            yaml = """
            user:
              <<:
                age:
                  - 1
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = ageOpts)
        )
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.user.age",
            actual = ex.path
        )
    }

    @Test
    fun throwMissingRequiredFieldAppendsKey() {
        val r = reader(yaml = "id: 1")
        r.beginObject()
        val options = JsonReaderOptions.of("id")
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        r.nextInt()
        val ex = assertFailsWith<GhostYamlException> {
            r.throwMissingRequiredField(jsonName = "name")
        }
        assertEquals(
            expected = "$.name",
            actual = ex.path
        )
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun parsePhaseErrorKeepsRootPath() {
        val r = reader(yaml = "*missing")
        val ex = assertFailsWith<GhostYamlException> { r.readDocument() }
        assertEquals(
            expected = "$",
            actual = ex.path
        )
        assertTrue(actual = ex.message.contains("position="))
        assertNotNull(actual = ex.hint)
        assertTrue(actual = ex.hint!!.contains("anchor") || ex.hint!!.contains("&"))
    }

    @Test
    fun hintForYamlErrorCoversCursorPrefixes() {
        assertNotNull(actual = "Expected Int but found true".hintForYamlError())
        assertNotNull(actual = "Expected Map but found []".hintForYamlError())
        assertNotNull(actual = "Expected List but found {}".hintForYamlError())
        assertNotNull(actual = "Required field 'x' missing in JSON".hintForYamlError())
        assertNull(actual = "Some obscure YAML lexer noise".hintForYamlError())
    }

    @Test
    fun invalidNumericStringBecomesYamlExceptionWithPath() {
        val options = JsonReaderOptions.of("age")
        val r = reader(yaml = "age: nope")
        r.beginObject()
        assertEquals(
            expected = 0,
            actual = r.selectNameAndConsume(options = options)
        )
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.age",
            actual = ex.path
        )
        assertNotNull(actual = ex.hint)
    }

    @Test
    fun multiDocumentResetsPathBetweenDocuments() {
        val yaml = """
            ---
            a: [1]
            ---
            b: true
            """.trimIndent()
        val r = reader(yaml = yaml)
        val docs = r.readAllDocuments { doc ->
            // Force a cursor walk so pathTracker engages before clearAfterDocument resets it.
            doc.beginObject()
            val key = doc.nextKey()
            if (key == "a") {
                doc.beginArray()
                while (doc.hasNextArrayElement()) {
                    doc.nextInt()
                }
                doc.endArray()
            } else {
                doc.nextBoolean()
            }
            doc.endObject()
            key
        }
        assertEquals(
            expected = listOf("a", "b"),
            actual = docs
        )

        // Fresh error after multi-doc must not retain breadcrumbs from prior documents.
        r.reset("""c: [1]""".encodeToByteArray())
        r.beginObject()
        assertEquals(
            expected = "c",
            actual = r.nextKey()
        )
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals(
            expected = "$.c",
            actual = ex.path
        )
    }
}
