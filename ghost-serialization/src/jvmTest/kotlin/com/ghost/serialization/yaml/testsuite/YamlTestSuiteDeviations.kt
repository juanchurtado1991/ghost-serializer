package com.ghost.serialization.yaml.testsuite

/** A yaml-test-suite case id known to deviate from strict conformance, plus why. */
data class DeviationCase(val id: String, val reason: String)

/** Builds a [DeviationCase]: `"9C9N" because "Wrong indented flow sequence"`. */
infix fun String.because(reason: String): DeviationCase = DeviationCase(this, reason)

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
val deviationsInOutcome: Set<DeviationCase> = setOf()

/** See [deviationsInOutcome]. */
val deviationsInValue: Set<DeviationCase> = setOf()
