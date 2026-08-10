package com.ghost.serialization.yaml.testsuite

import kotlin.system.exitProcess

/**
 * Standalone CLI report: runs the same conformance check as
 * [GhostYamlTestSuiteConformanceTest.printConformanceSummaryAndValidateDeviations] against the
 * vendored yaml-test-suite snapshot, but prints a readable summary unconditionally (the JUnit
 * version only surfaces with `--info`) and exits non-zero on any unexpected deviation — wired up
 * as `./gradlew :ghost-serialization:yamlComplianceMatrix`.
 *
 * Shares [parseThrew]/[valueMatches] with the JUnit test (see `YamlTestSuiteJsonComparison.kt`)
 * so the two can never quietly report different numbers.
 */
fun main() {
    val cases = YamlTestSuiteLoader.cases
    val outcomeDeviationIds = deviationsInOutcome.map { it.id }.toSet()
    val valueDeviationIds = deviationsInValue.map { it.id }.toSet()

    var outcomePass = 0
    var outcomeKnown = 0
    val outcomeUnexpected = mutableListOf<String>()
    for (case in cases) {
        val expectedToThrow = case.expectError xor (case.id in outcomeDeviationIds)
        val threw = parseThrew(case)
        when {
            threw == expectedToThrow && case.id !in outcomeDeviationIds -> outcomePass++
            threw == expectedToThrow && case.id in outcomeDeviationIds -> outcomeKnown++
            else -> outcomeUnexpected += "[${case.id}] ${case.label}: expected threw=$expectedToThrow but was $threw"
        }
    }

    val valueCases = cases.filter { !it.expectError && it.inJsonText != null && it.id !in outcomeDeviationIds }
    var valuePass = 0
    var valueKnown = 0
    val valueUnexpected = mutableListOf<String>()
    for (case in valueCases) {
        val expectedToMatch = case.id !in valueDeviationIds
        val matches = valueMatches(case)
        when {
            matches == expectedToMatch && case.id !in valueDeviationIds -> valuePass++
            matches == expectedToMatch && case.id in valueDeviationIds -> valueKnown++
            else -> valueUnexpected += "[${case.id}] ${case.label}: expected matches=$expectedToMatch but was $matches"
        }
    }

    // Denominator matching matrix.yaml.info's "json" column: valid (non-error) cases that ship an
    // in.json fixture — confirmed earlier to be the same vendored tree as matrix.yaml.info's own
    // snapshot, so this number is directly comparable to that table.
    val denominator = cases.count { !it.expectError && it.inJsonText != null }
    val compliancePercent = if (denominator == 0) 0.0 else valuePass * 100.0 / denominator

    val gapsByCategory = (deviationsInOutcome + deviationsInValue)
        .groupBy({ it.reason }, { it.id })
        .map { (reason, ids) -> (REASON_LABELS[reason] ?: reason) to ids.toSet().size }
        .sortedByDescending { it.second }

    val rule = "=".repeat(78)
    println(rule)
    println("Ghost YAML Spec Compliance — vendored yaml-test-suite snapshot")
    println(rule)
    println("Cases loaded: ${cases.size}  ($denominator valid, with in.json — the matrix.yaml.info-comparable denominator)")
    println()
    println("Outcome (parses/rejects as the spec expects):")
    println("  $outcomePass pass, $outcomeKnown known gap(s), ${outcomeUnexpected.size} UNEXPECTED")
    println("Value (decoded tree matches in.json, of ${valueCases.size} checked):")
    println("  $valuePass pass, $valueKnown known gap(s), ${valueUnexpected.size} UNEXPECTED")
    println()
    println("Compliance = value-pass / $denominator valid cases:")
    println("  $valuePass / $denominator = ${"%.2f".format(compliancePercent)}%")
    println()
    println("Known gaps by category:")
    for ((label, count) in gapsByCategory) {
        println("  %-22s %3d case(s)".format(label, count))
    }
    println(rule)

    val unexpected = outcomeUnexpected + valueUnexpected
    if (unexpected.isEmpty()) {
        println("No unexpected deviations — every known gap is tracked in YamlTestSuiteDeviations.kt")
    } else {
        println("${unexpected.size} unexpected deviation(s) — YamlTestSuiteDeviations.kt is out of date:")
        unexpected.forEach { println("  $it") }
        exitProcess(1)
    }
}
