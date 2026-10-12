package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A block sequence cannot start on the same line as an implicit mapping key (`key: - a`). */
class GhostYamlInlineSequenceTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())

    @Test
    fun rejectsLoneDashAfterKey() {
        assertFailsWith<GhostYamlException> {
            readerOf("key: -").readDocument()
        }
    }

    @Test
    fun rejectsSequenceEntryAfterKey() {
        assertFailsWith<GhostYamlException> {
            readerOf("key: - a").readDocument()
        }
    }

    @Test
    fun allowsSequenceOnNextLine() {
        assertEquals(
            expected = mapOf("key" to listOf("a", "b")),
            actual = readerOf("key:\n  - a\n  - b").readDocument()
        )
    }

    @Test
    fun allowsSequenceAtKeyIndent() {
        assertEquals(
            expected = mapOf("key" to listOf("a")),
            actual = readerOf("key:\n- a").readDocument()
        )
    }

    @Test
    fun allowsCompactSequenceInExplicitValue() {
        assertEquals(
            expected = mapOf("k" to listOf("v")),
            actual = readerOf("? k\n: - v").readDocument()
        )
    }

    @Test
    fun allowsDashStartingPlainScalar() {
        assertEquals(
            expected = mapOf("a" to "-x"),
            actual = readerOf("a: -x").readDocument()
        )
    }
}
