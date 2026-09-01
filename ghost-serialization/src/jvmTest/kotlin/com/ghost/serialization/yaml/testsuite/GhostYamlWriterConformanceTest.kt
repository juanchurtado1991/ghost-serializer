package com.ghost.serialization.yaml.testsuite

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

/**
 * Runs [GhostYamlTreeWriter] (a hand-rolled bridge from an already-decoded tree to
 * `GhostYamlWriter`'s low-level API) against every
 * vendored yaml-test-suite case the reader can successfully decode, checking two things:
 *
 * - [roundTripConformance]: does `decode -> encode -> decode` produce the same tree as the
 *   original decode?
 * - [kamlOracleConformance]: does a second, independent YAML parser (kaml) accept Ghost's own
 *   re-encoded output?
 *
 * Known gaps are tracked in `YamlWriterDeviations.kt` rather than silently skipped — same
 * philosophy as [GhostYamlTestSuiteConformanceTest].
 */
class GhostYamlWriterConformanceTest {

    private val roundTripDeviationIds = writerRoundTripDeviations.map { it.id }.toSet()
    private val kamlOracleDeviationIds = writerKamlOracleDeviations.map { it.id }.toSet()

    /** Cases the reader can decode at all — a writer round-trip check is moot otherwise. */
    private val writableCases = YamlTestSuiteLoader.cases.filter { decodeOriginal(it) != null }

    @TestFactory
    fun roundTripConformance(): Stream<DynamicTest> {
        return writableCases.stream().map { case ->
            dynamicTest("[${case.id}] ${case.label}") {
                val expectedToMatch = case.id !in roundTripDeviationIds
                val matches = writerRoundTripMatches(case)
                assertTrue(
                    matches == expectedToMatch,
                    "case ${case.id}: expected matches=$expectedToMatch but was $matches",
                )
            }
        }
    }

    @TestFactory
    fun kamlOracleConformance(): Stream<DynamicTest> {
        return writableCases.stream().map { case ->
            dynamicTest("[${case.id}] ${case.label}") {
                val expectedToMatch = case.id !in kamlOracleDeviationIds
                val matches = writerOutputIsKamlAcceptable(case)
                assertTrue(
                    matches == expectedToMatch,
                    "case ${case.id}: expected matches=$expectedToMatch but was $matches",
                )
            }
        }
    }

    /**
     * Prints a compact pass/known-deviation/unexpected summary (only visible with `--info`, or
     * from the IDE — same reasoning as [GhostYamlTestSuiteConformanceTest]'s equivalent), and
     * fails if any tracked deviation id no longer matches a loaded, reader-decodable case.
     */
    @Test
    fun printWriterConformanceSummaryAndValidateDeviations() {
        val writableIds = writableCases.map { it.id }.toSet()

        val staleRoundTripIds = writerRoundTripDeviations.map { it.id } - writableIds
        val staleKamlIds = writerKamlOracleDeviations.map { it.id } - writableIds
        assertTrue(
            staleRoundTripIds.isEmpty() && staleKamlIds.isEmpty(),
            "Stale writer deviation ids no longer present among reader-decodable cases: " +
                "roundTrip=$staleRoundTripIds kaml=$staleKamlIds",
        )

        var roundTripPass = 0
        var roundTripKnown = 0
        var roundTripUnexpected = 0
        for (case in writableCases) {
            val expectedToMatch = case.id !in roundTripDeviationIds
            val matchesExpectation = writerRoundTripMatches(case) == expectedToMatch
            when {
                matchesExpectation && case.id !in roundTripDeviationIds -> roundTripPass++
                matchesExpectation && case.id in roundTripDeviationIds -> roundTripKnown++
                else -> roundTripUnexpected++
            }
        }

        var kamlPass = 0
        var kamlKnown = 0
        var kamlUnexpected = 0
        for (case in writableCases) {
            val expectedToMatch = case.id !in kamlOracleDeviationIds
            val matchesExpectation = writerOutputIsKamlAcceptable(case) == expectedToMatch
            when {
                matchesExpectation && case.id !in kamlOracleDeviationIds -> kamlPass++
                matchesExpectation && case.id in kamlOracleDeviationIds -> kamlKnown++
                else -> kamlUnexpected++
            }
        }

        println(
            """
            --- YAML writer conformance (${writableCases.size} reader-decodable cases) ---
              Round-trip: $roundTripPass pass, $roundTripKnown known deviations, $roundTripUnexpected UNEXPECTED
              kaml oracle: $kamlPass pass, $kamlKnown known deviations, $kamlUnexpected UNEXPECTED
            """.trimIndent()
        )
    }
}
