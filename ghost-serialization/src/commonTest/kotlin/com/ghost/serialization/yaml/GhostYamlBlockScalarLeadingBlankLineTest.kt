package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Two compounding bugs in `readBlockScalarContent`
 * (found via yaml-test-suite cases `DWX9`/`T26H`/`4QFQ`/`R4YG`/`6FWR`/`F6MC`/`H2RW`/`L24T_00`/`L24T_01`):
 *
 * 1. Leading blank lines (before the first real content line) were unconditionally discarded
 *    instead of contributing their newlines to the decoded value — real content per the spec, not
 *    just structural padding used to auto-detect `blockIndent`.
 * 2. A "blank-looking" line (nothing but spaces) with *more* spaces than `blockIndent` isn't
 *    actually blank: the leftover spaces beyond `blockIndent` are real content and must be
 *    preserved, not discarded as if the whole line were empty.
 *
 * Fixing both together required a third fix: when `detectBlockScalarIndent`
 * falls back to a guessed `blockIndent` (no real content line ever found in the whole scalar, e.g.
 * `JEF9_01`/`JEF9_02` — a scalar consisting *only* of blank lines), that guess must stay at least
 * as large as any blank line already scanned past, or fix #2 would wrongly treat those as having
 * leftover content when there was never a genuine content line to establish `blockIndent` against.
 */
class GhostYamlBlockScalarLeadingBlankLineTest {

    private fun readerOf(yaml: String) = GhostYamlFlatReader(yaml.encodeToByteArray())

    @Test
    fun preservesLeadingBlankLinesInLiteralScalar() {
        val yaml = "|\n \n  \n  literal\n"
        val doc = readerOf(yaml).readDocument()
        assertEquals("\n\nliteral\n", doc)
    }

    @Test
    fun preservesLeftoverSpaceOnOverIndentedBlankLine() {
        // blockIndent=2 (from "  literal"); the "   " line has 1 space beyond blockIndent, so it
        // contributes a real " " to the content, not just a bare newline.
        val yaml = "|\n  literal\n   \n  text\n"
        val doc = readerOf(yaml).readDocument()
        assertEquals("literal\n \ntext\n", doc)
    }

    @Test
    fun scalarOfOnlyBlankLinesStaysEmpty() {
        // yaml-test-suite JEF9_01: must NOT regress into treating the sole blank line's spaces as
        // leftover content just because they exceed the guessed (no-real-content) blockIndent.
        val yaml = "- |+\n   \n"
        val doc = readerOf(yaml).readDocument() as List<*>
        assertEquals(listOf("\n"), doc)
    }

    @Test
    fun ordinaryLiteralScalarWithNoLeadingBlankLinesUnaffected() {
        val doc = readerOf("|\n  a\n  b\n").readDocument()
        assertEquals("a\nb\n", doc)
    }
}
