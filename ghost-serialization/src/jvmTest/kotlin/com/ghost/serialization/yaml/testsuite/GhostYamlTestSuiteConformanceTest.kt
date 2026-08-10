package com.ghost.serialization.yaml.testsuite

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.stream.Stream

/**
 * Runs Ghost's YAML decoder against the vendored yaml-test-suite snapshot (see
 * `yaml-test-suite/README.md`), checking two things per case since Ghost has no low-level
 * parser-event API to compare against `test.event` the way SnakeYAML-based tooling does:
 *
 * - [outcomeConformance]: does parsing throw when the case expects an error, and not throw
 *   otherwise?
 * - [valueConformance]: when the case has an `in.json` fixture and expects success, does the
 *   decoded tree match it?
 *
 * Known gaps are tracked in `YamlTestSuiteDeviations.kt` rather than silently skipped — see that
 * file's KDoc for the philosophy.
 */
class GhostYamlTestSuiteConformanceTest {

    private val outcomeDeviationIds = deviationsInOutcome.map { it.id }.toSet()
    private val valueDeviationIds = deviationsInValue.map { it.id }.toSet()

    @TestFactory
    fun outcomeConformance(): Stream<DynamicTest> {
        return YamlTestSuiteLoader.cases.stream().map { case ->
            dynamicTest("[${case.id}] ${case.label}") {
                val expectedToThrow = case.expectError xor (case.id in outcomeDeviationIds)
                val threw = parseThrew(case)
                assertTrue(
                    threw == expectedToThrow,
                    "case ${case.id}: expected threw=$expectedToThrow but was $threw",
                )
            }
        }
    }

    @TestFactory
    fun valueConformance(): Stream<DynamicTest> {
        val cases = YamlTestSuiteLoader.cases.filter { case ->
            !case.expectError && case.inJsonText != null && case.id !in outcomeDeviationIds
        }
        return cases.stream().map { case ->
            dynamicTest("[${case.id}] ${case.label}") {
                val expectedToMatch = case.id !in valueDeviationIds
                val matches = valueMatches(case)
                assertTrue(
                    matches == expectedToMatch,
                    "case ${case.id}: expected matches=$expectedToMatch but was $matches",
                )
            }
        }
    }

    /**
     * Prints a compact pass/known-deviation/unexpected-failure summary (only visible with
     * `--info`, or from the IDE — the root `build.gradle.kts` sets
     * `testLogging.showStandardStreams = false`; the JUnit HTML/XML report is CI's real source
     * of truth), and fails if any tracked deviation id no longer matches a loaded case — the
     * guard that catches stale entries after refreshing the vendored snapshot.
     */
    @Test
    fun printConformanceSummaryAndValidateDeviations() {
        val cases = YamlTestSuiteLoader.cases
        val caseIds = cases.map { it.id }.toSet()

        val staleOutcomeIds = deviationsInOutcome.map { it.id } - caseIds
        val staleValueIds = deviationsInValue.map { it.id } - caseIds
        assertTrue(
            staleOutcomeIds.isEmpty() && staleValueIds.isEmpty(),
            "Stale deviation ids no longer present in the loaded yaml-test-suite snapshot: " +
                "outcome=$staleOutcomeIds value=$staleValueIds",
        )

        var outcomePass = 0
        var outcomeKnown = 0
        var outcomeUnexpected = 0
        for (case in cases) {
            val expectedToThrow = case.expectError xor (case.id in outcomeDeviationIds)
            val matchesExpectation = parseThrew(case) == expectedToThrow
            when {
                matchesExpectation && case.id !in outcomeDeviationIds -> outcomePass++
                matchesExpectation && case.id in outcomeDeviationIds -> outcomeKnown++
                else -> outcomeUnexpected++
            }
        }

        val valueCases = cases.filter {
            !it.expectError && it.inJsonText != null && it.id !in outcomeDeviationIds
        }
        var valuePass = 0
        var valueKnown = 0
        var valueUnexpected = 0
        for (case in valueCases) {
            val expectedToMatch = case.id !in valueDeviationIds
            val matchesExpectation = valueMatches(case) == expectedToMatch
            when {
                matchesExpectation && case.id !in valueDeviationIds -> valuePass++
                matchesExpectation && case.id in valueDeviationIds -> valueKnown++
                else -> valueUnexpected++
            }
        }

        println(
            """
            --- YAML test-suite conformance (${cases.size} cases) ---
              Outcome: $outcomePass pass, $outcomeKnown known deviations, $outcomeUnexpected UNEXPECTED
              Value:   $valuePass pass, $valueKnown known deviations, $valueUnexpected UNEXPECTED (of ${valueCases.size} checked)
            """.trimIndent()
        )
    }
}
