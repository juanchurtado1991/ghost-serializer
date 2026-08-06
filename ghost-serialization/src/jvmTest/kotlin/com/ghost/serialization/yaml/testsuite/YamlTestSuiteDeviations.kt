package com.ghost.serialization.yaml.testsuite

/** A yaml-test-suite case id known to deviate from strict conformance, plus why. */
data class DeviationCase(val id: String, val reason: String)

/** Builds a [DeviationCase]: `"9C9N" because "Wrong indented flow sequence"`. */
infix fun String.because(reason: String): DeviationCase = DeviationCase(this, reason)

// Grouped reasons, not per-case free text: each of these describes a real, distinct category of
// unimplemented/incomplete YAML 1.2 spec surface identified while triaging the yaml-test-suite
// run for issue #17/#18, not a placeholder. See that issue for the full category breakdown and
// case counts. REASON_MISC is the honest exception — composite spec examples or single edge
// cases that combine several of the above (or something not yet individually diagnosed) rather
// than a clean single category.
private const val REASON_ANCHOR_ALIAS = "Anchors/aliases in this position (flow collections, mapping keys, multiple anchors, or specific placements) not fully implemented"
private const val REASON_BLOCK_SCALAR = "Block scalar (|/>) edge case beyond the indentation-indicator/root-indent/trailing-text fixes already landed"
private const val REASON_COMMENT = "Comment-placement edge case (between continuation lines, immediately after specific tokens) not fully implemented"
private const val REASON_DIRECTIVE = "YAML/TAG directives (%YAML, %TAG, reserved, duplicate, or document-boundary interaction with directives) not implemented"
private const val REASON_DOC_MARKER = "Document marker (---/...) edge case — content directly on the marker line, stream/multi-document boundary interactions, or missing-marker validation — not fully implemented"
private const val REASON_EMPTY_MISSING = "Empty/missing key or value edge case not fully implemented"
private const val REASON_EXPLICIT_KEY = "Explicit/complex block-mapping keys (\"? key\" / \": value\" on separate lines, multi-line or nested complex keys) not implemented"
private const val REASON_FLOW_COLLECTION = "Flow-collection edge case (malformed bracket/comma handling, nested anchors, multi-line spanning) not fully implemented"
private const val REASON_FLOW_IMPLICIT_KEY = "Flow-collection implicit/single-pair mapping keys (bare \"[a: 1]\"-style entries, possibly multi-line) not implemented"
private const val REASON_INDENTATION = "Block mapping/sequence indentation edge case (wrong/inconsistent indentation detection) not fully implemented"
private const val REASON_MISC = "Composite/advanced YAML 1.2 spec case not yet individually triaged"
private const val REASON_MULTILINE_PLAIN_EDGE = "Multi-line plain scalar edge case (comments between continuation lines, or as a flow-mapping key) beyond the basic folding already implemented"
private const val REASON_MULTILINE_QUOTED = "Multi-line double/single-quoted scalar folding (line breaks, leading/trailing whitespace across lines) not implemented — different code path from plain-scalar folding"
private const val REASON_TAB = "Tab handling in this position (outside block-mapping/sequence indentation, e.g. inside quoted-scalar folding or plain-scalar continuation) not yet implemented"
private const val REASON_TAG = "Tag resolution (handles, prefixes, shorthands, verbatim, or tag/anchor combinations) not fully implemented"

/**
 * Known, tracked gaps between Ghost's YAML decoding and the yaml-test-suite's expectations.
 *
 * Philosophy (adapted from `snakeyaml-engine-kmp`'s own `Deviations.kt`): unexplained or
 * deliberate divergences get tracked here by case id with a one-line reason — they are not
 * silently skipped, and their presence is not used to claim 100% spec compliance. Delete an
 * entry once the underlying gap is actually fixed;
 * [GhostYamlTestSuiteConformanceTest.printConformanceSummaryAndValidateDeviations] fails loudly
 * if an entry's id no longer matches any loaded case (e.g. after refreshing the vendored
 * snapshot), so stale entries can't linger unnoticed.
 *
 * Two categories, mirroring the two checks in [GhostYamlTestSuiteConformanceTest]:
 * - [deviationsInOutcome]: Ghost's parse-succeeds/fails outcome itself disagrees with the case.
 * - [deviationsInValue]: Ghost parses successfully, as expected, but the decoded value doesn't
 *   match the case's `in.json` fixture.
 */
val deviationsInOutcome: Set<DeviationCase> = setOf(
    "2CMS" because REASON_MULTILINE_PLAIN_EDGE, // Invalid mapping in plain multiline
    "2SXE" because REASON_ANCHOR_ALIAS, // Anchors With Colon in Name
    "35KP" because REASON_TAG, // Tags for Root Objects
    "4ABK" because REASON_FLOW_COLLECTION, // Flow Mapping Separate Values
    "4CQQ" because REASON_FLOW_COLLECTION, // Spec Example 2.18. Multi-line Flow Scalars
    "4FJ6" because REASON_EXPLICIT_KEY, // Nested implicit complex keys
    "4JVG" because REASON_ANCHOR_ALIAS, // Scalar value with two anchors
    "5TRB" because REASON_DOC_MARKER, // Invalid document-start marker in doublequoted tring
    "5U3A" because REASON_MISC, // Sequence on same Line as Mapping Key
    "62EZ" because REASON_MISC, // Invalid block mapping key on same line as previous key
    "6BFJ" because REASON_ANCHOR_ALIAS, // Mapping, key and flow sequence item anchors
    "7LBH" because REASON_FLOW_IMPLICIT_KEY, // Multiline double quoted implicit keys
    "87E4" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.8. Single Quoted Implicit Keys
    "8KB6" because REASON_MULTILINE_PLAIN_EDGE, // Multiline plain flow mapping key without value
    "8UDB" because REASON_FLOW_COLLECTION, // Spec Example 7.14. Flow Sequence Entries
    "9BXH" because REASON_MULTILINE_QUOTED, // Multiline doublequoted flow mapping key without value
    "9C9N" because REASON_FLOW_COLLECTION, // Wrong indented flow sequence
    "9KBC" because REASON_DOC_MARKER, // Mapping starting at --- line
    "9MMW" because REASON_FLOW_IMPLICIT_KEY, // Single Pair Implicit Entries
    "9MQT_01" because REASON_MISC, // Scalar doc with '...' in content
    "BF9H" because REASON_MULTILINE_PLAIN_EDGE, // Trailing comment in multiline plain scalar
    "CFD4" because REASON_FLOW_IMPLICIT_KEY, // Empty implicit key in single pair flow sequences
    "CN3R" because REASON_ANCHOR_ALIAS, // Various location of anchors in flow sequence
    "CT4Q" because REASON_EXPLICIT_KEY, // Spec Example 7.20. Single Pair Explicit Entry
    "CXX2" because REASON_ANCHOR_ALIAS, // Mapping with anchor on document start line
    "D49Q" because REASON_FLOW_IMPLICIT_KEY, // Multiline single quoted implicit keys
    "DFF7" because REASON_FLOW_COLLECTION, // Spec Example 7.16. Flow Mapping Entries
    "DK95_01" because REASON_TAB, // Tabs that look like indentation
    "DMG6" because REASON_INDENTATION, // Wrong indendation in Map
    "E76Z" because REASON_ANCHOR_ALIAS, // Aliases in Implicit Block Mapping
    "EW3V" because REASON_INDENTATION, // Wrong indendation in mapping
    "G4RS" because REASON_MISC, // Spec Example 2.17. Quoted Scalars
    "G5U8" because REASON_DOC_MARKER, // Plain dashes in flow sequence
    "GDY7" because REASON_COMMENT, // Comment that looks like a mapping key
    "HMQ5" because REASON_MISC, // Spec Example 6.23. Node Properties
    "HU3P" because REASON_MULTILINE_PLAIN_EDGE, // Invalid Mapping in plain scalar
    "JKF3" because REASON_MULTILINE_QUOTED, // Multiline unidented double quoted block key
    "JY7Z" because REASON_MISC, // Trailing content that looks like a mapping
    "L9U5" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.11. Plain Implicit Keys
    "LQZ7" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.4. Double Quoted Implicit Keys
    "LX3P" because REASON_FLOW_COLLECTION, // Implicit Flow Mapping Key on one line
    "M5C3" because REASON_BLOCK_SCALAR, // Spec Example 8.21. Block Scalar Nodes
    "M6YH" because REASON_INDENTATION, // Block sequence indentation
    "MUS6_01" because REASON_DIRECTIVE, // Directive variants
    "N4JP" because REASON_INDENTATION, // Bad indentation in mapping
    "NB6Z" because REASON_TAB, // Multiline plain value with tabs on empty lines
    "NJ66" because REASON_MULTILINE_PLAIN_EDGE, // Multiline plain flow mapping key
    "P2EQ" because REASON_MISC, // Invalid sequene item on same line as previous item
    "Q9WF" because REASON_MISC, // Spec Example 6.12. Separation Spaces
    "QB6E" because REASON_INDENTATION, // Wrong indented multiline quoted scalar
    "QF4Y" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.19. Single Pair Flow Mappings
    "RXY3" because REASON_MULTILINE_QUOTED, // Invalid document-end marker in single quoted string
    "RZP5" because REASON_COMMENT, // Various Trailing Comments [1.3]
    "RZT7" because REASON_MISC, // Spec Example 2.28. Log File
    "SU74" because REASON_ANCHOR_ALIAS, // Anchor and alias as mapping key
    "SY6V" because REASON_ANCHOR_ALIAS, // Anchor before sequence entry on same line
    "U44R" because REASON_INDENTATION, // Bad indentation in mapping (2)
    "UGM3" because REASON_MISC, // Spec Example 2.27. Invoice
    "UT92" because REASON_EXPLICIT_KEY, // Spec Example 9.4. Explicit Documents
    "UV7Q" because REASON_TAB, // Legal tab after indentation
    "VJP3_00" because REASON_FLOW_COLLECTION, // Flow collections over many lines
    "WZ62" because REASON_EMPTY_MISSING, // Spec Example 7.2. Empty Content
    "X38W" because REASON_ANCHOR_ALIAS, // Aliases in Flow Objects
    "XW4D" because REASON_COMMENT, // Various Trailing Comments
    "Y79Y_000" because REASON_TAB, // Tabs in various contexts
    "Y79Y_003" because REASON_TAB, // Tabs in various contexts
    "Y79Y_004" because REASON_TAB, // Tabs in various contexts
    "Y79Y_005" because REASON_TAB, // Tabs in various contexts
    "Y79Y_006" because REASON_TAB, // Tabs in various contexts
    "Y79Y_007" because REASON_TAB, // Tabs in various contexts
    "Y79Y_008" because REASON_TAB, // Tabs in various contexts
    "Y79Y_009" because REASON_EXPLICIT_KEY, // Tabs in various contexts (explicit key with colon-terminated content)
    "YJV2" because REASON_FLOW_COLLECTION, // Dash in flow sequence
    "ZCZ6" because REASON_MISC, // Invalid mapping in plain single line value
    "ZL4Z" because REASON_MISC, // Invalid nested mapping
    "ZVH3" because REASON_INDENTATION, // Wrong indented sequence item
)

/** See [deviationsInOutcome]. */
val deviationsInValue: Set<DeviationCase> = setOf(
    "26DV" because REASON_MISC, // Whitespace around colon in mappings
    "2SXE" because REASON_ANCHOR_ALIAS, // Anchors With Colon in Name
    "35KP" because REASON_TAG, // Tags for Root Objects
    "4CQQ" because REASON_FLOW_COLLECTION, // Spec Example 2.18. Multi-line Flow Scalars
    "4QFQ" because REASON_INDENTATION, // Spec Example 8.2. Block Indentation Indicator [1.3]
    "4WA9" because REASON_BLOCK_SCALAR, // Literal scalars
    "6FWR" because REASON_BLOCK_SCALAR, // Block Scalar Keep
    "6VJK" because REASON_BLOCK_SCALAR, // Spec Example 2.15. Folded newlines are preserved for "more indented" and blank lines
    "7T8X" because REASON_EMPTY_MISSING, // Spec Example 8.10. Folded Lines - 8.13. Final Empty Lines
    "87E4" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.8. Single Quoted Implicit Keys
    "8KB6" because REASON_MULTILINE_PLAIN_EDGE, // Multiline plain flow mapping key without value
    "8UDB" because REASON_FLOW_COLLECTION, // Spec Example 7.14. Flow Sequence Entries
    "9BXH" because REASON_MULTILINE_QUOTED, // Multiline doublequoted flow mapping key without value
    "A2M4" because REASON_INDENTATION, // Spec Example 6.2. Indentation Indicators
    "AB8U" because REASON_INDENTATION, // Sequence entry that looks like two with wrong indentation
    "CN3R" because REASON_ANCHOR_ALIAS, // Various location of anchors in flow sequence
    "CT4Q" because REASON_EXPLICIT_KEY, // Spec Example 7.20. Single Pair Explicit Entry
    "DE56_00" because REASON_TAB, // Trailing tabs in double quoted
    "DE56_01" because REASON_TAB, // Trailing tabs in double quoted
    "DE56_02" because REASON_TAB, // Trailing tabs in double quoted
    "DE56_03" because REASON_TAB, // Trailing tabs in double quoted
    "DWX9" because REASON_BLOCK_SCALAR, // Spec Example 8.8. Literal Content
    "E76Z" because REASON_ANCHOR_ALIAS, // Aliases in Implicit Block Mapping
    "F6MC" because REASON_BLOCK_SCALAR, // More indented lines at the beginning of folded block scalars
    "G4RS" because REASON_MISC, // Spec Example 2.17. Quoted Scalars
    "H2RW" because REASON_MISC, // Blank lines
    "HMQ5" because REASON_MISC, // Spec Example 6.23. Node Properties
    "HWV9" because REASON_DOC_MARKER, // Document-end marker
    "L24T_00" because REASON_MISC, // Trailing line of spaces
    "L24T_01" because REASON_MISC, // Trailing line of spaces
    "L9U5" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.11. Plain Implicit Keys
    "LQZ7" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.4. Double Quoted Implicit Keys
    "M5C3" because REASON_BLOCK_SCALAR, // Spec Example 8.21. Block Scalar Nodes
    "M6YH" because REASON_INDENTATION, // Block sequence indentation
    "M7A3" because REASON_DOC_MARKER, // Spec Example 9.3. Bare Documents
    "MJS9" because REASON_BLOCK_SCALAR, // Spec Example 6.7. Block Folding
    "NB6Z" because REASON_TAB, // Multiline plain value with tabs on empty lines
    "NJ66" because REASON_MULTILINE_PLAIN_EDGE, // Multiline plain flow mapping key
    "QF4Y" because REASON_FLOW_IMPLICIT_KEY, // Spec Example 7.19. Single Pair Flow Mappings
    "QT73" because REASON_DOC_MARKER, // Comment and document-end marker
    "R4YG" because REASON_INDENTATION, // Spec Example 8.2. Block Indentation Indicator
    "RZT7" because REASON_MISC, // Spec Example 2.28. Log File
    "SM9W_00" because REASON_DOC_MARKER, // Single character streams
    "T26H" because REASON_BLOCK_SCALAR, // Spec Example 8.8. Literal Content [1.3]
    "UGM3" because REASON_MISC, // Spec Example 2.27. Invoice
    "UT92" because REASON_EXPLICIT_KEY, // Spec Example 9.4. Explicit Documents
    "UV7Q" because REASON_TAB, // Legal tab after indentation
    "W42U" because REASON_MISC, // Spec Example 8.15. Block Sequence Entry Types
    "WZ62" because REASON_EMPTY_MISSING, // Spec Example 7.2. Empty Content
    "Y79Y_010" because REASON_TAB, // Tabs in various contexts
)
