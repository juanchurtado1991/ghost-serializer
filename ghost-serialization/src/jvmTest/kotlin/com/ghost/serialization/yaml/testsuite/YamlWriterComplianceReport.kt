package com.ghost.serialization.yaml.testsuite

import kotlin.system.exitProcess

/**
 * Standalone CLI report: runs the same conformance check as
 * [GhostYamlWriterConformanceTest.printWriterConformanceSummaryAndValidateDeviations] against the
 * vendored yaml-test-suite snapshot, but prints a readable summary unconditionally (the JUnit
 * version only surfaces with `--info`) and exits non-zero on any unexpected deviation — wired up
 * as `./gradlew :ghost-serialization:yamlWriterComplianceMatrix`.
 *
 * Shares [writerRoundTripMatches]/[writerOutputIsKamlAcceptable] with the JUnit test (see
 * `YamlWriterConformance.kt`) so the two can never quietly report different numbers.
 */
fun main() {
    val writableCases = YamlTestSuiteLoader.cases.filter { decodeOriginal(it) != null }
    val roundTripDeviationIds = writerRoundTripDeviations.map { it.id }.toSet()
    val kamlOracleDeviationIds = writerKamlOracleDeviations.map { it.id }.toSet()

    var roundTripPass = 0
    var roundTripKnown = 0
    val roundTripUnexpected = mutableListOf<String>()
    for (case in writableCases) {
        val expectedToMatch = case.id !in roundTripDeviationIds
        val matches = writerRoundTripMatches(case)
        when {
            matches == expectedToMatch && case.id !in roundTripDeviationIds -> roundTripPass++
            matches == expectedToMatch && case.id in roundTripDeviationIds -> roundTripKnown++
            else -> roundTripUnexpected += "[${case.id}] ${case.label}: expected matches=$expectedToMatch but was $matches"
        }
    }

    var kamlPass = 0
    var kamlKnown = 0
    val kamlUnexpected = mutableListOf<String>()
    for (case in writableCases) {
        val expectedToMatch = case.id !in kamlOracleDeviationIds
        val matches = writerOutputIsKamlAcceptable(case)
        when {
            matches == expectedToMatch && case.id !in kamlOracleDeviationIds -> kamlPass++
            matches == expectedToMatch && case.id in kamlOracleDeviationIds -> kamlKnown++
            else -> kamlUnexpected += "[${case.id}] ${case.label}: expected matches=$expectedToMatch but was $matches"
        }
    }

    val roundTripCompliancePercent = if (writableCases.isEmpty()) 0.0 else roundTripPass * 100.0 / writableCases.size
    val kamlCompliancePercent = if (writableCases.isEmpty()) 0.0 else kamlPass * 100.0 / writableCases.size

    val gapsByCategory = (writerRoundTripDeviations + writerKamlOracleDeviations)
        .groupBy({ it.reason }, { it.id })
        .map { (reason, ids) -> (WRITER_REASON_LABELS[reason] ?: reason) to ids.toSet().size }
        .sortedByDescending { it.second }

    val rule = "=".repeat(78)
    println(rule)
    println("Ghost YAML Writer Conformance — vendored yaml-test-suite snapshot")
    println(rule)
    println("Cases loaded: ${writableCases.size} reader-decodable (out of ${YamlTestSuiteLoader.cases.size} total)")
    println()
    println("Round-trip (decode -> encode -> decode reproduces the original tree):")
    println("  $roundTripPass pass, $roundTripKnown known gap(s), ${roundTripUnexpected.size} UNEXPECTED")
    println("  $roundTripPass / ${writableCases.size} = ${"%.2f".format(roundTripCompliancePercent)}%")
    println()
    println("kaml oracle (an independent second parser accepts Ghost's re-encoded output):")
    println("  $kamlPass pass, $kamlKnown known gap(s), ${kamlUnexpected.size} UNEXPECTED")
    println("  $kamlPass / ${writableCases.size} = ${"%.2f".format(kamlCompliancePercent)}%")
    println()
    println("Known gaps by category:")
    for ((label, count) in gapsByCategory) {
        println("  %-28s %3d case(s)".format(label, count))
    }
    println(rule)

    val unexpected = roundTripUnexpected + kamlUnexpected
    if (unexpected.isEmpty()) {
        println("No unexpected deviations — every known gap is tracked in YamlWriterDeviations.kt")
    } else {
        println("${unexpected.size} unexpected deviation(s) — YamlWriterDeviations.kt is out of date:")
        unexpected.forEach { println("  $it") }
        exitProcess(1)
    }
}
