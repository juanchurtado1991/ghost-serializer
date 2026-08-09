package com.ghost.serialization.yaml.testsuite

/**
 * Known, tracked gaps between [GhostYamlTreeWriter]'s round-trip output and either (a) the
 * original decoded tree, or (b) what a second, independent parser (kaml) accepts. Same philosophy
 * as [YamlTestSuiteDeviations.kt][deviationsInOutcome] — tracked by case id with a reason, not
 * silently skipped. [GhostYamlWriterConformanceTest.printWriterConformanceSummaryAndValidateDeviations]
 * fails loudly if an entry's id no longer matches a loaded, reader-decodable case.
 */
internal const val WRITER_REASON_UNQUOTED_KEY =
    "name(key: String) writes mapping keys as bare, unquoted plain-scalar text (unlike value(), " +
        "which always double-quotes). A key containing structurally-significant bytes — \": \", " +
        "an embedded newline/tab, or the stringified text of a complex (non-scalar) key the " +
        "reader already collapsed to a String — round-trips to a different structure, or fails " +
        "to re-parse at all, instead of erroring or being quoted safely."
internal const val WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION =
    "kaml's parser rejects any mapping key that isn't a simple scalar — a leading '{'/'[' " +
        "(flow-collection-shaped key text) or a bare/empty implicit key (\": value\" with " +
        "nothing before the colon) — as a matter of its own kotlinx.serialization " +
        "property-name-oriented design, regardless of whether the key is otherwise spec-legal " +
        "YAML that Ghost's own reader accepts (confirmed via round-trip: these cases decode, " +
        "re-encode, and re-decode back to an identical tree). A kaml-oracle limitation, not a " +
        "Ghost writer bug."

/** Short display label per `WRITER_REASON_*` category, for `YamlWriterComplianceReport`'s breakdown. */
internal val WRITER_REASON_LABELS: Map<String, String> = mapOf(
    WRITER_REASON_UNQUOTED_KEY to "UNQUOTED_KEY",
    WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION to "KAML_COMPLEX_KEY_LIMITATION",
)

/** See [GhostYamlWriterConformanceTest.roundTripConformance]. */
internal val writerRoundTripDeviations: Set<DeviationCase> = setOf(
    "4FJ6" because WRITER_REASON_UNQUOTED_KEY, // Nested implicit complex keys
    "5WE3" because WRITER_REASON_UNQUOTED_KEY, // Spec Example 8.17. Explicit Block Mapping Entries
    "6H3V" because WRITER_REASON_UNQUOTED_KEY, // Backslashes in singlequotes
    "6SLA" because WRITER_REASON_UNQUOTED_KEY, // Allowed characters in quoted mapping key
    "DFF7" because WRITER_REASON_UNQUOTED_KEY, // Spec Example 7.16. Flow Mapping Entries
    "FRK4" because WRITER_REASON_UNQUOTED_KEY, // Spec Example 7.3. Completely Empty Flow Nodes
    "KK5P" because WRITER_REASON_UNQUOTED_KEY, // Various combinations of explicit block mappings
    "M2N8_01" because WRITER_REASON_UNQUOTED_KEY, // Question mark edge cases
)

/** See [GhostYamlWriterConformanceTest.kamlOracleConformance]. */
internal val writerKamlOracleDeviations: Set<DeviationCase> = setOf(
    // Same root cause as writerRoundTripDeviations — the unquoted key breaks kaml too, not just
    // Ghost's own re-decode.
    "4FJ6" because WRITER_REASON_UNQUOTED_KEY,
    "5WE3" because WRITER_REASON_UNQUOTED_KEY,
    "6H3V" because WRITER_REASON_UNQUOTED_KEY,
    "6SLA" because WRITER_REASON_UNQUOTED_KEY,
    "DFF7" because WRITER_REASON_UNQUOTED_KEY,
    "FRK4" because WRITER_REASON_UNQUOTED_KEY,
    "KK5P" because WRITER_REASON_UNQUOTED_KEY,
    "M2N8_01" because WRITER_REASON_UNQUOTED_KEY,
    // kaml-only: round-trip is otherwise correct, kaml itself just can't parse the key shape.
    "26DV" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Whitespace around colon in mappings
    "2JQS" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Block Mapping with Missing Keys
    "6BFJ" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Mapping, key and flow sequence item anchors
    "6M2F" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Aliases in Explicit Block Mapping
    "6PBE" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Zero-indented sequences in explicit mapping keys
    "9MMW" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Single Pair Implicit Entries
    "CFD4" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Empty implicit key in single pair flow sequences
    "FH7J" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Tags on Empty Scalars
    "LX3P" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Implicit Flow Mapping Key on one line
    "M2N8_00" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Question mark edge cases
    "M5DY" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 2.11. Mapping between Sequences
    "NHX8" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Empty Lines at End of Document
    "PW8X" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Anchors on Empty Scalars
    "S3PD" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 8.18. Implicit Block Mapping Entries
    "SBG9" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Flow Sequence in Flow Mapping
    "SM9W_01" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Single character streams
    "UKK6_00" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Syntax character edge cases
    "WZ62" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 7.2. Empty Content
    "Y79Y_006" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Tabs in various contexts
    "Y79Y_007" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Tabs in various contexts
    "Y79Y_008" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Tabs in various contexts
)
