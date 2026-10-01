package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers two compounding bugs in `readBlockScalarContent`: leading blank lines were discarded
 * instead of contributing newlines to the decoded value, and a "blank-looking" line with more
 * spaces than `blockIndent` actually has leftover content that must be preserved. Fixing both
 * required `detectBlockScalarIndent`'s guessed fallback to stay at least as large as any blank
 * line already scanned. Covers yaml-test-suite `DWX9`/`T26H`/`4QFQ`/`R4YG`/`6FWR`/`F6MC`/`H2RW`/
 * `L24T_00`/`L24T_01`/`JEF9_01`/`JEF9_02`.
 */
class GhostYamlBlockScalarLeadingBlankLineTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())

    @Test
    fun preservesLeadingBlankLinesInLiteralScalar() {
        val yaml = "|\n \n  \n  literal\n"
        val doc = readerOf(yaml).readDocument()
        assertEquals(
            expected = "\n\nliteral\n",
            actual = doc
        )
    }

    @Test
    fun preservesLeftoverSpaceOnOverIndentedBlankLine() {
        // blockIndent=2 (from "  literal"); "   " has 1 space beyond that, so it contributes a
        // real " " to the content, not just a bare newline.
        val yaml = "|\n  literal\n   \n  text\n"
        val doc = readerOf(yaml).readDocument()
        assertEquals(
            expected = "literal\n \ntext\n",
            actual = doc
        )
    }

    @Test
    fun scalarOfOnlyBlankLinesStaysEmpty() {
        // yaml-test-suite JEF9_01: the sole blank line's spaces must not be treated as leftover
        // content just because they exceed the guessed (no-real-content) blockIndent.
        val yaml = "- |+\n   \n"
        val doc = readerOf(yaml).readDocument() as List<*>
        assertEquals(
            expected = listOf("\n"),
            actual = doc
        )
    }

    @Test
    fun ordinaryLiteralScalarWithNoLeadingBlankLinesUnaffected() {
        val doc = readerOf("|\n  a\n  b\n").readDocument()
        assertEquals(
            expected = "a\nb\n",
            actual = doc
        )
    }
}
