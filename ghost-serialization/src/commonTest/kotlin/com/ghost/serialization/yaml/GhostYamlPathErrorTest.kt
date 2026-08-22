package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
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

    private fun reader(yaml: String) = GhostYamlFlatReader(yaml.encodeToByteArray())

    @Test
    fun pathIncludesNestedMappingFieldOnTypeError() {
        val options = JsonReaderOptions.of("user")
        val userOptions = JsonReaderOptions.of("name", "age")
        val r = reader(
            """
            user:
              name: Ada
              age: [1, 2]
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(userOptions))
        r.nextString()
        assertEquals(1, r.selectNameAndConsume(userOptions))
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.user.age", ex.path)
        assertNotNull(ex.hint)
        assertTrue(ex.message.contains("Hint:"))
    }

    @Test
    fun pathIncludesSequenceIndex() {
        val options = JsonReaderOptions.of("ids")
        val r = reader(
            """
            ids:
              - 1
              - 2
              - true
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginArray()
        assertTrue(r.hasNextArrayElement())
        r.nextInt()
        assertTrue(r.hasNextArrayElement())
        r.nextInt()
        assertTrue(r.hasNextArrayElement())
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.ids[2]", ex.path)
    }

    @Test
    fun aliasUseSiteReportsReferencingPathNotAnchorSite() {
        val options = JsonReaderOptions.of("base", "user")
        val ageOpts = JsonReaderOptions.of("age")
        val r = reader(
            """
            base: &b
              age:
                - 1
            user: *b
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.skipValue()
        assertEquals(1, r.selectNameAndConsume(options))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(ageOpts))
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.user.age", ex.path)
    }

    @Test
    fun mergeKeyLooksLikeLocalFieldPath() {
        val options = JsonReaderOptions.of("user")
        val ageOpts = JsonReaderOptions.of("age")
        val r = reader(
            """
            user:
              <<:
                age:
                  - 1
            """.trimIndent()
        )
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(ageOpts))
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.user.age", ex.path)
    }

    @Test
    fun throwMissingRequiredFieldAppendsKey() {
        val r = reader("id: 1")
        r.beginObject()
        val options = JsonReaderOptions.of("id")
        assertEquals(0, r.selectNameAndConsume(options))
        r.nextInt()
        val ex = assertFailsWith<GhostYamlException> {
            r.throwMissingRequiredField("name")
        }
        assertEquals("$.name", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun parsePhaseErrorKeepsRootPath() {
        val r = reader("*missing")
        val ex = assertFailsWith<GhostYamlException> { r.readDocument() }
        assertEquals("$", ex.path)
        assertTrue(ex.message.contains("position="))
        assertNotNull(ex.hint)
        assertTrue(ex.hint!!.contains("anchor") || ex.hint!!.contains("&"))
    }

    @Test
    fun hintForYamlErrorCoversCursorPrefixes() {
        assertNotNull(hintForYamlError("Expected Int but found true"))
        assertNotNull(hintForYamlError("Expected Map but found []"))
        assertNotNull(hintForYamlError("Expected List but found {}"))
        assertNotNull(hintForYamlError("Required field 'x' missing in JSON"))
        assertNull(hintForYamlError("Some obscure YAML lexer noise"))
    }

    @Test
    fun invalidNumericStringBecomesYamlExceptionWithPath() {
        val options = JsonReaderOptions.of("age")
        val r = reader("age: nope")
        r.beginObject()
        assertEquals(0, r.selectNameAndConsume(options))
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.age", ex.path)
        assertNotNull(ex.hint)
    }

    @Test
    fun multiDocumentResetsPathBetweenDocuments() {
        val yaml = """
            ---
            a: [1]
            ---
            b: true
            """.trimIndent()
        val r = reader(yaml)
        val docs = r.readAllDocuments { doc ->
            // Force a cursor walk so pathTracker is used, then leave via clearAfterDocument.
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
        assertEquals(listOf("a", "b"), docs)

        // Fresh error after multi-doc must not retain breadcrumbs from prior documents.
        r.reset("""c: [1]""".encodeToByteArray())
        r.beginObject()
        assertEquals("c", r.nextKey())
        val ex = assertFailsWith<GhostYamlException> { r.nextInt() }
        assertEquals("$.c", ex.path)
    }
}
