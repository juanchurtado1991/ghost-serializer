package com.ghost.serialization.yaml.testsuite

/**
 * Known, tracked gaps between [GhostYamlTreeWriter]'s round-trip output and either (a) the
 * original decoded tree, or (b) what a second, independent parser (kaml) accepts. Same philosophy
 * as [YamlTestSuiteDeviations.kt][deviationsInOutcome] — tracked by case id with a reason, not
 * silently skipped. [GhostYamlWriterConformanceTest.printWriterConformanceSummaryAndValidateDeviations]
 * fails loudly if an entry's id no longer matches a loaded, reader-decodable case.
 */
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
    WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION to "KAML_COMPLEX_KEY_LIMITATION",
)

/**
 * See [GhostYamlWriterConformanceTest.roundTripConformance]. Empty: `name(key: String)` used to
 * write every mapping key as bare, unquoted plain-scalar text (unlike `value()`, which always
 * double-quotes), so a key containing structurally-significant bytes (`": "`, an embedded
 * newline/tab, a leading anchor/tag/alias/quote sigil, or a leading `[`/`{` from a stringified
 * complex key) could round-trip to a different structure or fail to re-parse. Fixed via
 * `GhostYamlWriter.keyNeedsQuoting` — every previously-tracked case here now round-trips
 * correctly.
 */
internal val writerRoundTripDeviations: Set<DeviationCase> = setOf()

/** See [GhostYamlWriterConformanceTest.kamlOracleConformance]. */
internal val writerKamlOracleDeviations: Set<DeviationCase> = setOf(
    "2JQS" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Block Mapping with Missing Keys
    "6M2F" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Aliases in Explicit Block Mapping
    "CFD4" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Empty implicit key in single pair flow sequences
    "FH7J" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Tags on Empty Scalars
    "FRK4" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 7.3. Completely Empty Flow Nodes (empty implicit key — round-trip is now correct, only kaml's own bare-empty-key limitation remains)
    "M2N8_00" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Question mark edge cases
    "NHX8" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Empty Lines at End of Document
    "PW8X" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Anchors on Empty Scalars
    "S3PD" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 8.18. Implicit Block Mapping Entries
    "SM9W_01" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Single character streams
    "UKK6_00" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Syntax character edge cases
    "WZ62" because WRITER_REASON_KAML_COMPLEX_KEY_LIMITATION, // Spec Example 7.2. Empty Content
)
