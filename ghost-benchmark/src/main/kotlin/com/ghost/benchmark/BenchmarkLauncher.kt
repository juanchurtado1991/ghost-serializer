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
    val suite = BenchmarkSuite.fromCliName(name = args.firstOrNull() ?: BenchmarkSuite.FULL.cliName)
    BenchmarkEnvironment.printConfigHeader(suite = suite)
    val threadBean = BenchmarkEnvironment.init() ?: exitProcess(1)

    val engines = BenchmarkEngines()
    val ok = when (suite) {
        BenchmarkSuite.FULL -> runFullSuite(threadBean = threadBean, engines = engines)
        BenchmarkSuite.SYNTHETIC -> runSyntheticSuite(threadBean = threadBean, engines = engines, regressionGate = true)
        BenchmarkSuite.TWITTER -> runTwitterSuite(threadBean = threadBean, regressionGate = true)
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

    BenchmarkProgress.logPhase(phase = 1, totalPhases = 5, title = "Cold start")
    runAndPrintColdStart(smallBytes = payloads.smallBytes)

    BenchmarkProgress.logPhase(
        phase = 2,
        totalPhases = 5,
        title = "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    runWarmupPhase(engines = engines, smallBytes = payloads.smallBytes, smallComplex = payloads.smallComplex)
    TwitterBenchmark.warmupGlobal(iterations = BenchmarkStandard.WARMUP_ITERATIONS)

    BenchmarkProgress.logPhase(
        phase = 3,
        totalPhases = 5,
        title = "Synthetic suite (${BenchmarkStandard.SYNTHETIC_SESSIONS} sessions × " +
                "${BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION} samples)",
    )
    performPhaseGc()
    val synthetic = runSyntheticBenchmarks(threadBean = threadBean, engines = engines, payloads = payloads)
    printFinalResults(finalResults = synthetic.aggregated, payloads = payloads)

    BenchmarkProgress.logPhase(phase = 4, totalPhases = 5, title = "Ghost special features + RawJson capture")
    performPhaseGc()
    GhostSpecialFeaturesBenchmark.run()
    RawJsonCaptureBenchmark.run()

    BenchmarkProgress.logPhase(phase = 5, totalPhases = 5, title = "Twitter macro + regression check")
    performPhaseGc()
    val twitterObs = TwitterBenchmark.run(threadBean)

    return RegressionCalculator.report(
        observed = syntheticObservations(run = synthetic) + twitterObs,
        tolerance = BenchmarkStandard.REGRESSION_TOLERANCE,
    )
}

private fun runProtoSuite(): Boolean = GhostProtoBenchmark.run()

private fun runRawJsonSuite(): Boolean {
    RawJsonCaptureBenchmark.run()
    return true
}

private fun runSpecialSuite(): Boolean {
    GhostSpecialFeaturesBenchmark.run()
    return true
}

@Suppress("SameParameterValue")
private fun runSyntheticSuite(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    regressionGate: Boolean,
): Boolean {
    val payloads = BenchmarkPayloads.create()

    BenchmarkProgress.logPhase(
        phase = 1,
        totalPhases = 2,
        title = "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    runWarmupPhase(engines = engines, smallBytes = payloads.smallBytes, smallComplex = payloads.smallComplex)

    BenchmarkProgress.logPhase(
        phase = 2,
        totalPhases = 2,
        title = "Synthetic suite (${BenchmarkStandard.SYNTHETIC_SESSIONS} sessions × " +
                "${BenchmarkStandard.SYNTHETIC_SAMPLES_PER_SESSION} samples)",
    )
    performPhaseGc()
    val synthetic = runSyntheticBenchmarks(threadBean = threadBean, engines = engines, payloads = payloads)
    printFinalResults(finalResults = synthetic.aggregated, payloads = payloads)

    return if (regressionGate) {
        RegressionCalculator.report(
            observed = syntheticObservations(run = synthetic),
            tolerance = BenchmarkStandard.REGRESSION_TOLERANCE,
        )
    } else {
        true
    }
}

private fun runTwitterSuite(threadBean: ThreadMXBean, regressionGate: Boolean): Boolean {
    BenchmarkProgress.logPhase(
        phase = 1,
        totalPhases = 2,
        title = "Twitter JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)"
    )
    performPhaseGc()
    TwitterBenchmark.warmupGlobal(iterations = BenchmarkStandard.WARMUP_ITERATIONS)

    BenchmarkProgress.logPhase(phase = 2, totalPhases = 2, title = "Twitter macro + regression check")
    performPhaseGc()
    val twitterObs = TwitterBenchmark.run(threadBean)

    return if (regressionGate) {
        RegressionCalculator.report(observed = twitterObs, tolerance = BenchmarkStandard.REGRESSION_TOLERANCE)
    } else {
        true
    }
}

private fun runYamlSuite(): Boolean = GhostYamlBenchmark.run()
