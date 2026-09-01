@file:OptIn(
    ExperimentalStdlibApi::class,
    InternalGhostApi::class,
    ExperimentalSerializationApi::class,
)

package com.ghost.benchmark

import com.ghost.serialization.InternalGhostApi
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.system.exitProcess

/**
 * CLI entry point for every benchmark suite. Each Gradle task launches a fresh JVM with
 * `-Pghost.benchmark.profile=full|fast` (default `full`); `-PskipTests` skips the `:allTests` gate.
 * Suite selection is driven by the first CLI argument; see [BenchmarkSuite].
 */
fun main(args: Array<String>) {
    val suite = BenchmarkSuite.fromCliName(args.firstOrNull() ?: BenchmarkSuite.FULL.cliName)
    BenchmarkEnvironment.printConfigHeader(suite)
    val threadBean = BenchmarkEnvironment.init() ?: exitProcess(1)

    val engines = BenchmarkEngines()
    val ok = when (suite) {
        BenchmarkSuite.FULL -> runFullSuite(threadBean, engines)
        BenchmarkSuite.SYNTHETIC -> runSyntheticSuite(threadBean, engines, regressionGate = true)
        BenchmarkSuite.TWITTER -> runTwitterSuite(threadBean, regressionGate = true)
        BenchmarkSuite.SPECIAL -> runSpecialSuite()
        BenchmarkSuite.RAWJSON -> runRawJsonSuite()
        BenchmarkSuite.YAML -> runYamlSuite()
        BenchmarkSuite.PROTO -> runProtoSuite()
    }

    println("\n[COMPLETE] ${suite.cliName} benchmark finished.")
    exitProcess(if (ok) 0 else 1)
}

private fun runFullSuite(threadBean: ThreadMXBean, engines: BenchmarkEngines): Boolean {
    val payloads = BenchmarkPayloads.create()

    BenchmarkProgress.logPhase(1, 5, "Cold start")
    runAndPrintColdStart(payloads.smallBytes)

    BenchmarkProgress.logPhase(
        2,
        5,
        "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    runWarmupPhase(engines, payloads.smallBytes, payloads.smallComplex)
    TwitterBenchmark.warmupGlobal(BenchmarkStandard.WARMUP_ITERATIONS)

    BenchmarkProgress.logPhase(
        3,
        5,
        "Synthetic suite (${BenchmarkStandard.SYNTHETIC_SESSIONS} sessions × " +
                "${BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION} samples)",
    )
    performPhaseGc()
    val synthetic = runSyntheticBenchmarks(threadBean, engines, payloads)
    printFinalResults(synthetic.aggregated, payloads)

    BenchmarkProgress.logPhase(4, 5, "Ghost special features + RawJson capture")
    performPhaseGc()
    GhostSpecialFeaturesBenchmark.run()
    RawJsonCaptureBenchmark.run()

    BenchmarkProgress.logPhase(5, 5, "Twitter macro + regression check")
    performPhaseGc()
    val twitterObs = TwitterBenchmark.run(threadBean)

    return RegressionCalculator.report(
        syntheticObservations(synthetic) + twitterObs,
        BenchmarkStandard.REGRESSION_TOLERANCE,
    )
}

@Suppress("SameParameterValue")
private fun runSyntheticSuite(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    regressionGate: Boolean,
): Boolean {
    val payloads = BenchmarkPayloads.create()

    BenchmarkProgress.logPhase(
        1,
        2,
        "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    runWarmupPhase(engines, payloads.smallBytes, payloads.smallComplex)

    BenchmarkProgress.logPhase(
        2,
        2,
        "Synthetic suite (${BenchmarkStandard.SYNTHETIC_SESSIONS} sessions × " +
                "${BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION} samples)",
    )
    performPhaseGc()
    val synthetic = runSyntheticBenchmarks(threadBean, engines, payloads)
    printFinalResults(synthetic.aggregated, payloads)

    return if (regressionGate) {
        RegressionCalculator.report(
            syntheticObservations(synthetic),
            BenchmarkStandard.REGRESSION_TOLERANCE,
        )
    } else {
        true
    }
}

private fun runTwitterSuite(threadBean: ThreadMXBean, regressionGate: Boolean): Boolean {
    BenchmarkProgress.logPhase(
        1,
        2,
        "Twitter JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    TwitterBenchmark.warmupGlobal(BenchmarkStandard.WARMUP_ITERATIONS)

    BenchmarkProgress.logPhase(2, 2, "Twitter macro + regression check")
    performPhaseGc()
    val twitterObs = TwitterBenchmark.run(threadBean)

    return if (regressionGate) {
        RegressionCalculator.report(twitterObs, BenchmarkStandard.REGRESSION_TOLERANCE)
    } else {
        true
    }
}

private fun runSpecialSuite(): Boolean {
    GhostSpecialFeaturesBenchmark.run()
    return true
}

private fun runRawJsonSuite(): Boolean {
    RawJsonCaptureBenchmark.run()
    return true
}

private fun runYamlSuite(): Boolean = GhostYamlBenchmark.run()

private fun runProtoSuite(): Boolean = GhostProtoBenchmark.run()
