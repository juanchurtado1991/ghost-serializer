package com.ghost.serialization.yaml.testsuite

/** A yaml-test-suite case id known to deviate from strict conformance, plus why. */
data class DeviationCase(val id: String, val reason: String)

/** Builds a [DeviationCase]: `"9C9N" because "Wrong indented flow sequence"`. */
infix fun String.because(reason: String): DeviationCase = DeviationCase(this, reason)

// Grouped reasons, not per-case free text: each names a real, distinct category of
// unimplemented/incomplete YAML 1.2 spec surface identified while triaging the yaml-test-suite
// run for issue #17/#18. REASON_MISC is the honest exception — composite or not-yet-individually-
// diagnosed cases.
internal const val REASON_ANCHOR_ALIAS = "Anchors/aliases in this position (flow collections, mapping keys, multiple anchors, or specific placements) not fully implemented"
internal const val REASON_BLOCK_SCALAR = "Block scalar (|/>) edge case beyond the indentation-indicator/root-indent/trailing-text fixes already landed"
internal const val REASON_COMMENT = "Comment-placement edge case (between continuation lines, immediately after specific tokens) not fully implemented"
internal const val REASON_DIRECTIVE = "YAML/TAG directives (%YAML, %TAG, reserved, duplicate, or document-boundary interaction with directives) not implemented"
internal const val REASON_DOC_MARKER = "Document marker (---/...) edge case — content directly on the marker line, stream/multi-document boundary interactions, or missing-marker validation — not fully implemented"
internal const val REASON_EMPTY_MISSING = "Empty/missing key or value edge case not fully implemented"
internal const val REASON_EXPLICIT_KEY = "Explicit/complex block-mapping keys (\"? key\" / \": value\" on separate lines, multi-line or nested complex keys) not implemented"
internal const val REASON_FLOW_COLLECTION = "Flow-collection edge case (malformed bracket/comma handling, nested anchors, multi-line spanning) not fully implemented"
internal const val REASON_FLOW_IMPLICIT_KEY = "Flow-collection implicit/single-pair mapping keys (bare \"[a: 1]\"-style entries, possibly multi-line) not implemented"
internal const val REASON_INDENTATION = "Block mapping/sequence indentation edge case (wrong/inconsistent indentation detection) not fully implemented"
internal const val REASON_MISC = "Composite/advanced YAML 1.2 spec case not yet individually triaged"
internal const val REASON_MULTILINE_PLAIN_EDGE = "Multi-line plain scalar edge case (comments between continuation lines, or as a flow-mapping key) beyond the basic folding already implemented"
internal const val REASON_MULTILINE_QUOTED = "Multi-line double/single-quoted scalar folding (line breaks, leading/trailing whitespace across lines) not implemented — different code path from plain-scalar folding"
internal const val REASON_TAB = "Tab handling in this position (outside block-mapping/sequence indentation, e.g. inside quoted-scalar folding or plain-scalar continuation) not yet implemented"
internal const val REASON_TAG = "Tag resolution (handles, prefixes, shorthands, verbatim, or tag/anchor combinations) not fully implemented"

/** Short display label per `REASON_*` category, for `YamlComplianceReport`'s grouped breakdown. */
internal val REASON_LABELS: Map<String, String> = mapOf(
    REASON_ANCHOR_ALIAS to "ANCHOR_ALIAS",
    REASON_BLOCK_SCALAR to "BLOCK_SCALAR",
    REASON_COMMENT to "COMMENT",
    REASON_DIRECTIVE to "DIRECTIVE",
    REASON_DOC_MARKER to "DOC_MARKER",
    REASON_EMPTY_MISSING to "EMPTY_MISSING",
    REASON_EXPLICIT_KEY to "EXPLICIT_KEY",
    REASON_FLOW_COLLECTION to "FLOW_COLLECTION",
    REASON_FLOW_IMPLICIT_KEY to "FLOW_IMPLICIT_KEY",
    REASON_INDENTATION to "INDENTATION",
    REASON_MISC to "MISC",
    REASON_MULTILINE_PLAIN_EDGE to "MULTILINE_PLAIN_EDGE",
    REASON_MULTILINE_QUOTED to "MULTILINE_QUOTED",
    REASON_TAB to "TAB",
    REASON_TAG to "TAG",
)

/**
 * Known, tracked gaps between Ghost's YAML decoding and the yaml-test-suite's expectations.
 * Tracked by case id with a one-line reason rather than silently skipped, so their presence isn't
 * mistaken for 100% spec compliance; delete an entry once its gap is fixed —
 * [GhostYamlTestSuiteConformanceTest.printConformanceSummaryAndValidateDeviations] fails loudly if
 * an id no longer matches a loaded case.
 *
 * Two categories, mirroring the two checks in [GhostYamlTestSuiteConformanceTest]:
 * [deviationsInOutcome] (parse-succeeds/fails outcome disagrees) and [deviationsInValue] (parses
 * as expected, but decoded value doesn't match `in.json`).
 */
val deviationsInOutcome: Set<DeviationCase> = setOf(
    "35KP" because REASON_TAG, // Tags for Root Objects
    "4JVG" because REASON_ANCHOR_ALIAS, // Scalar value with two anchors
    "5U3A" because REASON_MISC, // Sequence on same Line as Mapping Key
    "62EZ" because REASON_MISC, // Invalid block mapping key on same line as previous key
    "9C9N" because REASON_FLOW_COLLECTION, // Wrong indented flow sequence
    "CXX2" because REASON_ANCHOR_ALIAS, // Mapping with anchor on document start line
    "DK95_01" because REASON_TAB, // Tabs that look like indentation
    "DMG6" because REASON_INDENTATION, // Wrong indendation in Map
    "JY7Z" because REASON_MISC, // Trailing content that looks like a mapping
    "M5C3" because REASON_BLOCK_SCALAR, // Spec Example 8.21. Block Scalar Nodes
    "M6YH" because REASON_INDENTATION, // Block sequence indentation
    "N4JP" because REASON_INDENTATION, // Bad indentation in mapping
    "P2EQ" because REASON_MISC, // Invalid sequene item on same line as previous item
    "Q9WF" because REASON_FLOW_COLLECTION, // Spec 6.12: readKey has no bracket-depth tracking, so a flow mapping used as an implicit key breaks at its own first inner ':'
    "QB6E" because REASON_INDENTATION, // Wrong indented multiline quoted scalar
    "RZP5" because REASON_COMMENT, // Various Trailing Comments [1.3]
    "U44R" because REASON_INDENTATION, // Bad indentation in mapping (2)
    "UV7Q" because REASON_TAB, // Legal tab after indentation
    "V9D5" because REASON_EXPLICIT_KEY, // Spec 8.19: explicit key's compact nested mapping swallows the outer pair's ": value" line instead of returning to readExplicitKeyEntry
    "VJP3_00" because REASON_FLOW_COLLECTION, // Flow collections over many lines
    "X38W" because REASON_ANCHOR_ALIAS, // Aliases in Flow Objects
    "XW4D" because REASON_COMMENT, // Various Trailing Comments
    "Y79Y_000" because REASON_TAB, // Tabs in various contexts
    "Y79Y_003" because REASON_TAB, // Tabs in various contexts
    "Y79Y_004" because REASON_TAB, // Tabs in various contexts
    "Y79Y_005" because REASON_TAB, // Tabs in various contexts
    "Y79Y_006" because REASON_TAB, // Tabs in various contexts
    "Y79Y_007" because REASON_TAB, // Tabs in various contexts
    "Y79Y_008" because REASON_TAB, // Tabs in various contexts
    "ZL4Z" because REASON_MISC, // Invalid nested mapping
    "ZVH3" because REASON_INDENTATION, // Wrong indented sequence item
)

/** See [deviationsInOutcome]. */
val deviationsInValue: Set<DeviationCase> = setOf(
    "35KP" because REASON_TAG, // Tags for Root Objects
    "4WA9" because REASON_BLOCK_SCALAR, // Literal scalars
    "6VJK" because REASON_BLOCK_SCALAR, // Spec Example 2.15. Folded newlines are preserved for "more indented" and blank lines
    "7T8X" because REASON_EMPTY_MISSING, // Spec Example 8.10. Folded Lines - 8.13. Final Empty Lines
    "A2M4" because REASON_INDENTATION, // Spec Example 6.2. Indentation Indicators
    "AB8U" because REASON_INDENTATION, // Sequence entry that looks like two with wrong indentation
    "M5C3" because REASON_BLOCK_SCALAR, // Spec Example 8.21. Block Scalar Nodes
    "M6YH" because REASON_INDENTATION, // Block sequence indentation
    "MJS9" because REASON_BLOCK_SCALAR, // Spec Example 6.7. Block Folding
    "R4YG" because REASON_INDENTATION, // Spec Example 8.2. Block Indentation Indicator
    "UV7Q" because REASON_TAB, // Legal tab after indentation
)
