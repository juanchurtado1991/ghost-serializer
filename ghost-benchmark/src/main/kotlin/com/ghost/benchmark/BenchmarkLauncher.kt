@file:OptIn(
    ExperimentalStdlibApi::class,
    InternalGhostApi::class,
    ExperimentalSerializationApi::class,
)

package com.ghost.benchmark

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ComplexResponse
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.system.exitProcess
import okio.ByteString.Companion.encodeUtf8

/**
 * CLI entry for all benchmark suites.
 *
 * ```
 * ./gradlew :ghost-benchmark:benchmarkTwitter -PskipTests
 * ./gradlew :ghost-benchmark:benchmarkSynthetic -PskipTests
 * ./gradlew :ghost-benchmark:benchmarkRegression -PskipTests
 * ./gradlew :ghost-benchmark:benchmarkRegressionFast -PskipTests   # ~1–2 min dev gate
 * ./gradlew :ghost-benchmark:run -PskipTests          # full README suite
 * ```
 */
fun main(args: Array<String>) {
    val suite = BenchmarkSuite.fromCliName(args.firstOrNull() ?: BenchmarkSuite.FULL.cliName)
    BenchmarkEnvironment.printConfigHeader(suite)
    val threadBean = BenchmarkEnvironment.init()
    if (threadBean == null) {
        exitProcess(1)
    }

    val engines = BenchmarkEngines()
    val ok = when (suite) {
        BenchmarkSuite.FULL -> runFullSuite(threadBean, engines)
        BenchmarkSuite.SYNTHETIC -> runSyntheticSuite(threadBean, engines, regressionGate = true)
        BenchmarkSuite.TWITTER -> runTwitterSuite(threadBean, regressionGate = true)
        BenchmarkSuite.SPECIAL -> runSpecialSuite()
        BenchmarkSuite.RAWJSON -> runRawJsonSuite()
    }

    println("\n[COMPLETE] ${suite.cliName} benchmark finished.")
    exitProcess(if (ok) 0 else 1)
}

private fun runFullSuite(threadBean: ThreadMXBean, engines: BenchmarkEngines): Boolean {
    val payloads = BenchmarkPayloads.create()

    BenchmarkProgress.logPhase(1, 5, "Cold start")
    runAndPrintColdStart(payloads.smallBytes, engines)

    BenchmarkProgress.logPhase(2, 5, "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)")
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
    printFinalResults(synthetic.aggregated)

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

private fun runSyntheticSuite(
    threadBean: ThreadMXBean,
    engines: BenchmarkEngines,
    regressionGate: Boolean,
): Boolean {
    val payloads = BenchmarkPayloads.create()

    BenchmarkProgress.logPhase(1, 2, "Global JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)")
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
    printFinalResults(synthetic.aggregated)

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
    BenchmarkProgress.logPhase(1, 2, "Twitter JIT warmup (${BenchmarkStandard.WARMUP_ITERATIONS} iterations)")
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

/** Pre-generated JSON payloads reused across synthetic suites. */
internal data class BenchmarkPayloads(
    val smallComplex: ComplexResponse,
    val smallBytes: okio.ByteString,
    val listMediumBytes: okio.ByteString,
    val syncLargeBytes: okio.ByteString,
    val writingComplex: ComplexResponse,
    val stressTreeBytes: okio.ByteString,
    val failureMalformed: String,
    val failureBytes: okio.ByteString,
) {
    companion object {
        fun create(): BenchmarkPayloads {
            val smallComplex = generateComplexData(20)
            val smallBytes = generateNeutralJson(smallComplex).encodeUtf8()
            val listMediumBytes = generateNeutralJson(generateComplexData(200)).encodeUtf8()
            val syncLargeBytes = generateNeutralJson(generateComplexData(2000)).encodeUtf8()
            val writingComplex = generateComplexData(1000)
            val stressTreeBytes = generateNeutralJson(createTree(20)).encodeUtf8()
            val failureMalformed = smallBytes.utf8().substring(0, smallBytes.size / 2)
            return BenchmarkPayloads(
                smallComplex = smallComplex,
                smallBytes = smallBytes,
                listMediumBytes = listMediumBytes,
                syncLargeBytes = syncLargeBytes,
                writingComplex = writingComplex,
                stressTreeBytes = stressTreeBytes,
                failureMalformed = failureMalformed,
                failureBytes = failureMalformed.encodeUtf8(),
            )
        }
    }
}
