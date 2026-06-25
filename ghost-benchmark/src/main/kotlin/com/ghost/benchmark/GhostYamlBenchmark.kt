@file:OptIn(
    ExperimentalStdlibApi::class,
    InternalGhostApi::class,
    ExperimentalSerializationApi::class
)
@file:Suppress("SameParameterValue", "UNCHECKED_CAST")

package com.ghost.benchmark

import com.charleskorn.kaml.Yaml
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.encodeToYamlBytes
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.integration.model.BenchResult
import com.ghost.serialization.integration.model.BenchUser
import com.ghost.serialization.integration.model.Category
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.TwitterResponse
import com.ghost.serialization.integration.model.UserRole
import com.sun.management.ThreadMXBean
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.management.ManagementFactory
import kotlin.math.sqrt

/**
 * Performance benchmarks for YAML serialization and deserialization engines:
 * compares Ghost (YAML) vs Kaml (KotlinX Serialization YAML).
 */
object GhostYamlBenchmark {

    private object Constants {
        const val VAL_20 = 20
        const val VAL_200 = 200
        const val VAL_1000 = 1000
        const val VAL_2000 = 2000
        const val DIVISOR_MS = 1_000_000.0
        const val DIVISOR_KB = 1024.0
        const val REPORT_STEP = 5000
        const val SLEEP_MS = 150L
        const val SLEEP_SHORT_MS = 50L
        const val FAIL_WARMUP_RUNS = 100
        const val BATCHES = 10
        const val PERCENT_MULTIPLIER = 100.0
        const val NANOSECONDS_IN_SECOND = 1_000_000_000.0

        const val STR_GHOST = "GHOST"
        const val STR_KAML = "KAML"
        const val STR_JACKSON = "JACKSON"
        const val STR_BETA = "beta"
        const val STR_SUCCESS = "success"
        const val STR_42 = "42"
        const val STR_USER_PREFIX = "User "
        const val STR_EMAIL = "u@e.com"
        const val STR_LEAF = "L"
        const val STR_NODE = "N"
        const val STR_TWITTER_RESOURCE = "twitter_macro.json"

        const val MSG_HEADER = "\n--- INITIALIZING YAML PERFORMANCE BENCHMARKS ---"
        const val MSG_WARMUP = "\n🔥 Warming up YAML JIT (warmup runs)..."
        const val MSG_RUNNING = "\n🚀 Running YAML performance measurements (runs)..."
        const val MSG_COLD = "COLD START (YAML first parse)"
        const val MSG_LIST_MEDIUM = "DESERIALIZATION: YAML LIST_MEDIUM (200 objects)"
        const val MSG_SYNC_LARGE = "DESERIALIZATION: YAML SYNC_FULL_LARGE (2000 objects)"
        const val MSG_WRITING = "SERIALIZATION: YAML WRITING (1000 objects)"
        const val MSG_NESTING = "STRESS TEST: YAML DEEP NESTING (20 Levels)"
        const val MSG_FAILURE = "FAILURE RESILIENCE (Malformed YAML)"
        const val MSG_TWITTER_HEADER = "\n========================================================"
        const val MSG_TWITTER_BANNER = "BENCHMARK: YAML TWITTER MACRO DATASET"
        const val MSG_TWITTER_SKIP = "  ⚠️  Skipping YAML Twitter benchmark: twitter_macro.json not found."
        const val MSG_TWITTER_WARMUP = "🔥 Warming up YAML Twitter models..."
        const val MSG_TWITTER_MEASURING = "\n🚀 Running YAML Twitter performance measurements..."
        const val MSG_TWITTER_SUMMARY = "\n--- YAML Twitter Dataset Performance Summary (Fastest First) ---"
        const val MSG_WINNER = "   WINNER: %s (%.1f%% faster than %s, %s)"
        const val MSG_WINNER_SIMPLE = "   👉 WINNER for %s: %s (%.1f%% faster, %s than %s)"
        const val MSG_MEM_LESS = "%.1f%% less memory"
        const val MSG_MEM_MORE = "but uses %.1f%% MORE memory"
    }

    private val kaml = Yaml.default
    private val jacksonYaml = YAMLMapper().apply {
        registerModule(
            KotlinModule.Builder()
                .configure(KotlinFeature.NullIsSameAsDefault, true)
                .build()
        )
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
    }

    fun run(runs: Int, warmupIters: Int, threadBean: ThreadMXBean?) {
        println(Constants.MSG_HEADER)

        val smallComplex = generateComplexData(Constants.VAL_20)
        val smallYamlString = kaml.encodeToString(smallComplex)
        val smallYamlBytes = smallYamlString.encodeToByteArray()

        val listMediumComplex = generateComplexData(Constants.VAL_200)
        val listMediumString = kaml.encodeToString(listMediumComplex)
        val listMediumBytes = listMediumString.encodeToByteArray()

        val syncLargeComplex = generateComplexData(Constants.VAL_2000)
        val syncLargeString = kaml.encodeToString(syncLargeComplex)
        val syncLargeBytes = syncLargeString.encodeToByteArray()

        val writingComplex = generateComplexData(Constants.VAL_1000)

        val stressTree = createTree(Constants.VAL_20)
        val stressTreeString = kaml.encodeToString(stressTree)
        val stressTreeBytes = stressTreeString.encodeToByteArray()

        val failureMalformed = smallYamlString.substring(0, smallYamlString.length / Constants.VAL_20) // Use division factor
        val failureBytes = failureMalformed.encodeToByteArray()

        // 1. Cold Start
        runAndPrintColdStart(smallYamlBytes, smallYamlString)

        // 2. Warmup
        performGc()
        runWarmupPhase(smallYamlBytes, smallYamlString, smallComplex, warmupIters)

        // 3. Benchmarking Sessions
        performGc()
        val sessions = runBenchmarkSessions(
            runs,
            threadBean,
            listMediumBytes = listMediumBytes,
            listMediumString = listMediumString,
            syncLargeBytes = syncLargeBytes,
            syncLargeString = syncLargeString,
            writingComplex = writingComplex,
            stressTreeBytes = stressTreeBytes,
            stressTreeString = stressTreeString,
            failureMalformed = failureMalformed,
            failureBytes = failureBytes
        )

        // 4. Calculate Final Results
        val finalResults = calculateFinalResults(sessions)

        // 5. Print Final Results
        printFinalResults(finalResults, runs)

        // 6. Run Edge/Test Datasets Benchmark
        runEdgeDatasetsBenchmark(runs, warmupIters, threadBean)
    }

    fun runTwitter(runs: Int, warmupIters: Int, threadBean: ThreadMXBean?) {
        println(Constants.MSG_TWITTER_HEADER)
        println(Constants.MSG_TWITTER_BANNER)
        println(Constants.MSG_TWITTER_HEADER)

        val resource = object {}.javaClass.classLoader.getResource(Constants.STR_TWITTER_RESOURCE)
        if (resource == null) {
            println(Constants.MSG_TWITTER_SKIP)
            return
        }

        val jsonString = resource.readText()
        val decodedObj = Ghost.deserialize<TwitterResponse>(jsonString)

        val twitterYamlString = kaml.encodeToString(decodedObj)
        val twitterYamlBytes = twitterYamlString.encodeToByteArray()

        println(Constants.MSG_TWITTER_WARMUP)
        repeat(warmupIters) { i ->
            if (warmupIters > 1 && (i + 1) % Constants.REPORT_STEP == 0) {
                println("   [Warmup ${i + 1}/$warmupIters]...")
            }
            // String Mode
            Ghost.decodeFromYaml<TwitterResponse>(twitterYamlString)
            kaml.decodeFromString<TwitterResponse>(twitterYamlString)
            jacksonYaml.readValue<TwitterResponse>(twitterYamlString)

            Ghost.encodeToYaml(decodedObj)
            kaml.encodeToString(decodedObj)
            jacksonYaml.writeValueAsString(decodedObj)

            // Bytes Mode
            Ghost.decodeFromYaml<TwitterResponse>(twitterYamlBytes)
            kaml.decodeFromString<TwitterResponse>(String(twitterYamlBytes, Charsets.UTF_8))
            jacksonYaml.readValue<TwitterResponse>(twitterYamlBytes)

            Ghost.encodeToYamlBytes(decodedObj)
        }

        performGc()
        println(Constants.MSG_TWITTER_MEASURING)

        // Decode Benchmarks
        performGc()
        val ghostDecodeStr = measureTwitterPerf(threadBean, runs) { Ghost.decodeFromYaml<TwitterResponse>(twitterYamlString) }
        performGc()
        val kamlDecodeStr = measureTwitterPerf(threadBean, runs) { kaml.decodeFromString<TwitterResponse>(twitterYamlString) }
        performGc()
        val jacksonDecodeStr = measureTwitterPerf(threadBean, runs) { jacksonYaml.readValue<TwitterResponse>(twitterYamlString) }

        performGc()
        val ghostDecodeBytes = measureTwitterPerf(threadBean, runs) { Ghost.decodeFromYaml<TwitterResponse>(twitterYamlBytes) }
        performGc()
        val kamlDecodeBytes = measureTwitterPerf(threadBean, runs) { kaml.decodeFromString<TwitterResponse>(String(twitterYamlBytes, Charsets.UTF_8)) }
        performGc()
        val jacksonDecodeBytes = measureTwitterPerf(threadBean, runs) { jacksonYaml.readValue<TwitterResponse>(twitterYamlBytes) }

        // Encode Benchmarks
        performGc()
        val ghostEncodeStr = measureTwitterPerf(threadBean, runs) { Ghost.encodeToYaml(decodedObj) }
        performGc()
        val kamlEncodeStr = measureTwitterPerf(threadBean, runs) { kaml.encodeToString(decodedObj) }
        performGc()
        val jacksonEncodeStr = measureTwitterPerf(threadBean, runs) { jacksonYaml.writeValueAsString(decodedObj) }

        performGc()
        val ghostEncodeBytes = measureTwitterPerf(threadBean, runs) { Ghost.encodeToYamlBytes(decodedObj) }
        performGc()
        val kamlEncodeBytes = measureTwitterPerf(threadBean, runs) { kaml.encodeToString(decodedObj).encodeToByteArray() }
        performGc()
        val jacksonEncodeBytes = measureTwitterPerf(threadBean, runs) { jacksonYaml.writeValueAsBytes(decodedObj) }

        printTwitterResults(
            listOf(
                "Decode (String)" to listOf(Constants.STR_GHOST to ghostDecodeStr, Constants.STR_KAML to kamlDecodeStr, Constants.STR_JACKSON to jacksonDecodeStr),
                "Decode (Bytes)" to listOf(Constants.STR_GHOST to ghostDecodeBytes, Constants.STR_KAML to kamlDecodeBytes, Constants.STR_JACKSON to jacksonDecodeBytes),
                "Encode (String)" to listOf(Constants.STR_GHOST to ghostEncodeStr, Constants.STR_KAML to kamlEncodeStr, Constants.STR_JACKSON to jacksonEncodeStr),
                "Encode (Bytes)" to listOf(Constants.STR_GHOST to ghostEncodeBytes, Constants.STR_KAML to kamlEncodeBytes, Constants.STR_JACKSON to jacksonEncodeBytes)
            )
        )
    }

    private fun runAndPrintColdStart(smallBytes: ByteArray, smallString: String) {
        val kamlTime = measureTimeNanos {
            kaml.decodeFromString<ComplexResponse>(smallString)
        }
        val jacksonTime = measureTimeNanos {
            jacksonYaml.readValue<ComplexResponse>(smallString)
        }
        val ghostTime = measureTimeNanos {
            Ghost.decodeFromYaml<ComplexResponse>(smallBytes)
        }

        val metrics = YamlBenchmarkMetrics(
            ghost = BenchResult(ghostTime, 0L),
            kaml = BenchResult(kamlTime, 0L),
            jackson = BenchResult(jacksonTime, 0L)
        )
        printRankedTable(Constants.MSG_COLD, metrics)
    }

    private fun runWarmupPhase(smallBytes: ByteArray, smallString: String, smallComplex: ComplexResponse, warmupIters: Int) {
        println(Constants.MSG_WARMUP)
        repeat(warmupIters) {
            Ghost.decodeFromYaml<ComplexResponse>(smallString)
            kaml.decodeFromString<ComplexResponse>(smallString)
            jacksonYaml.readValue<ComplexResponse>(smallString)

            Ghost.encodeToYaml(smallComplex)
            kaml.encodeToString(smallComplex)
            jacksonYaml.writeValueAsString(smallComplex)

            Ghost.decodeFromYaml<ComplexResponse>(smallBytes)
            Ghost.encodeToYamlBytes(smallComplex)
        }
    }

    private fun runBenchmarkSessions(
        runs: Int,
        threadBean: ThreadMXBean?,
        listMediumBytes: ByteArray,
        listMediumString: String,
        syncLargeBytes: ByteArray,
        syncLargeString: String,
        writingComplex: ComplexResponse,
        stressTreeBytes: ByteArray,
        stressTreeString: String,
        failureMalformed: String,
        failureBytes: ByteArray
    ): List<YamlBenchmarkSessionResults> {
        val sessions = mutableListOf<YamlBenchmarkSessionResults>()
        repeat(runs) { i ->
            if (runs > 1 && (i + 1) % Constants.REPORT_STEP == 0) {
                println("[Run ${i + 1}/$runs] Benchmarking YAML...")
            }

            val listMedium = runListMediumDeserialization(threadBean, listMediumBytes, listMediumString)
            val syncLarge = runSyncLargeDeserialization(threadBean, syncLargeBytes, syncLargeString)
            val writing = runSerialization(threadBean, writingComplex)
            val stress = runStressTests(stressTreeBytes, stressTreeString)
            val failure = runFailureTests(failureMalformed, failureBytes)

            sessions.add(
                YamlBenchmarkSessionResults(
                    listMedium = listMedium,
                    syncLarge = syncLarge,
                    writing = writing,
                    stress = stress,
                    failure = failure
                )
            )
        }
        return sessions
    }

    private fun runListMediumDeserialization(threadBean: ThreadMXBean?, bytes: ByteArray, string: String): YamlModeMetrics {
        return runDeserializationAllModes(threadBean, bytes, string)
    }

    private fun runSyncLargeDeserialization(threadBean: ThreadMXBean?, bytes: ByteArray, string: String): YamlModeMetrics {
        return runDeserializationAllModes(threadBean, bytes, string)
    }

    private fun runSerialization(threadBean: ThreadMXBean?, complex: ComplexResponse): YamlModeMetrics {
        return runSerializationAllModes(threadBean, complex)
    }

    private fun runDeserializationAllModes(threadBean: ThreadMXBean?, bytes: ByteArray, string: String): YamlModeMetrics {
        val ghostStr = measurePerf(threadBean) { Ghost.decodeFromYaml<ComplexResponse>(string) }
        val kamlStr = measurePerf(threadBean) { kaml.decodeFromString<ComplexResponse>(string) }
        val jacksonStr = measurePerf(threadBean) { jacksonYaml.readValue<ComplexResponse>(string) }

        val ghostBytes = measurePerf(threadBean) { Ghost.decodeFromYaml<ComplexResponse>(bytes) }
        val kamlBytes = measurePerf(threadBean) { kaml.decodeFromString<ComplexResponse>(String(bytes, Charsets.UTF_8)) }
        val jacksonBytes = measurePerf(threadBean) { jacksonYaml.readValue<ComplexResponse>(bytes) }

        return YamlModeMetrics(
            string = YamlBenchmarkMetrics(
                ghost = BenchResult(ghostStr.second, ghostStr.third),
                kaml = BenchResult(kamlStr.second, kamlStr.third),
                jackson = BenchResult(jacksonStr.second, jacksonStr.third)
            ),
            bytes = YamlBenchmarkMetrics(
                ghost = BenchResult(ghostBytes.second, ghostBytes.third),
                kaml = BenchResult(kamlBytes.second, kamlBytes.third),
                jackson = BenchResult(jacksonBytes.second, jacksonBytes.third)
            )
        )
    }

    private fun runSerializationAllModes(threadBean: ThreadMXBean?, complex: ComplexResponse): YamlModeMetrics {
        val ghostStr = measurePerf(threadBean) { Ghost.encodeToYaml(complex) }
        val kamlStr = measurePerf(threadBean) { kaml.encodeToString(complex) }
        val jacksonStr = measurePerf(threadBean) { jacksonYaml.writeValueAsString(complex) }

        val ghostBytes = measurePerf(threadBean) { Ghost.encodeToYamlBytes(complex) }
        val kamlBytes = measurePerf(threadBean) { kaml.encodeToString(complex).encodeToByteArray() }
        val jacksonBytes = measurePerf(threadBean) { jacksonYaml.writeValueAsBytes(complex) }

        return YamlModeMetrics(
            string = YamlBenchmarkMetrics(
                ghost = BenchResult(ghostStr.second, ghostStr.third),
                kaml = BenchResult(kamlStr.second, kamlStr.third),
                jackson = BenchResult(jacksonStr.second, jacksonStr.third)
            ),
            bytes = YamlBenchmarkMetrics(
                ghost = BenchResult(ghostBytes.second, ghostBytes.third),
                kaml = BenchResult(kamlBytes.second, kamlBytes.third),
                jackson = BenchResult(jacksonBytes.second, jacksonBytes.third)
            )
        )
    }

    private fun runStressTests(treeBytes: ByteArray, treeString: String): YamlBenchmarkMetrics {
        val kamlTime = measureTimeNanos {
            kaml.decodeFromString<Category>(treeString)
        }
        val jacksonTime = measureTimeNanos {
            jacksonYaml.readValue<Category>(treeString)
        }
        val ghostTime = measureTimeNanos {
            Ghost.decodeFromYaml<Category>(treeBytes)
        }

        return YamlBenchmarkMetrics(
            ghost = BenchResult(ghostTime, 0L),
            kaml = BenchResult(kamlTime, 0L),
            jackson = BenchResult(jacksonTime, 0L)
        )
    }

    private fun runFailureTests(malformed: String, bytes: ByteArray): YamlBenchmarkMetrics {
        val kamlTime = measureAvgFailSpeed {
            try {
                kaml.decodeFromString<ComplexResponse>(malformed)
            } catch (_: Exception) {
            }
        }
        val jacksonTime = measureAvgFailSpeed {
            try {
                jacksonYaml.readValue<ComplexResponse>(malformed)
            } catch (_: Exception) {
            }
        }
        val ghostTime = measureAvgFailSpeed {
            try {
                Ghost.decodeFromYaml<ComplexResponse>(bytes)
            } catch (_: Exception) {
            }
        }

        return YamlBenchmarkMetrics(
            ghost = BenchResult(ghostTime, 0L),
            kaml = BenchResult(kamlTime, 0L),
            jackson = BenchResult(jacksonTime, 0L)
        )
    }

    private fun calculateFinalResults(sessions: List<YamlBenchmarkSessionResults>): YamlBenchmarkSessionResults {
        if (sessions.size == 1) return sessions.last()

        return YamlBenchmarkSessionResults(
            listMedium = averageModeMetrics(sessions.map { it.listMedium }),
            syncLarge = averageModeMetrics(sessions.map { it.syncLarge }),
            writing = averageModeMetrics(sessions.map { it.writing }),
            stress = averageMetrics(sessions.map { it.stress }),
            failure = averageMetrics(sessions.map { it.failure })
        )
    }

    private fun averageModeMetrics(list: List<YamlModeMetrics>): YamlModeMetrics {
        return YamlModeMetrics(
            string = averageMetrics(list.map { it.string }),
            bytes = averageMetrics(list.map { it.bytes })
        )
    }

    private fun averageMetrics(list: List<YamlBenchmarkMetrics>): YamlBenchmarkMetrics {
        return YamlBenchmarkMetrics(
            ghost = averageBenchResult(list.map { it.ghost }),
            kaml = averageBenchResult(list.map { it.kaml }),
            jackson = averageBenchResult(list.map { it.jackson })
        )
    }

    private fun averageBenchResult(list: List<BenchResult>): BenchResult {
        val avgNanos = list.map { it.nanos }.average().toLong()
        val avgBytes = list.map { it.allocBytes }.average().toLong()

        val stDevNanos = if (list.size > 1) {
            val avg = avgNanos / Constants.DIVISOR_MS
            val variance = list.map { (it.nanos / Constants.DIVISOR_MS - avg).let { d -> d * d } }.average()
            (sqrt(variance) * Constants.DIVISOR_MS).toLong()
        } else 0L

        return BenchResult(avgNanos, avgBytes, stDevNanos)
    }

    private fun printFinalResults(finalResults: YamlBenchmarkSessionResults, runs: Int) {
        val titleSuffix = if (runs > 1) " (STATISTICAL AVG OF $runs RUNS)" else ""

        printModeTables(
            "${Constants.MSG_LIST_MEDIUM}$titleSuffix",
            finalResults.listMedium
        )
        printModeTables(
            "${Constants.MSG_SYNC_LARGE}$titleSuffix",
            finalResults.syncLarge
        )
        printModeTables(
            "${Constants.MSG_WRITING}$titleSuffix",
            finalResults.writing
        )
        printRankedTable(
            "${Constants.MSG_NESTING}$titleSuffix",
            finalResults.stress
        )
        printRankedTable(
            "${Constants.MSG_FAILURE}$titleSuffix",
            finalResults.failure
        )
    }

    private fun printModeTables(title: String, metrics: YamlModeMetrics) {
        println("\n========================================================")
        println("BENCHMARK: $title")
        println("========================================================")
        printRankedSubTable("STRING MODE", metrics.string)
        printRankedSubTable("BYTES MODE", metrics.bytes)
    }

    private fun printRankedSubTable(label: String, metrics: YamlBenchmarkMetrics) {
        println("\n--- $label ---")
        printRankedTableBody(metrics)
    }

    private fun printRankedTable(title: String, metrics: YamlBenchmarkMetrics) {
        println("\n========================================================")
        println("BENCHMARK: $title")
        println("========================================================")
        printRankedTableBody(metrics)
    }

    private data class YamlEngineRank(
        val name: String,
        val nanos: Long,
        val mem: Long,
        val stDevNanos: Long
    )

    private fun printRankedTableBody(metrics: YamlBenchmarkMetrics) {
        val rankings = listOf(
            YamlEngineRank(
                Constants.STR_GHOST,
                metrics.ghost.nanos,
                metrics.ghost.allocBytes,
                metrics.ghost.stdevNanos
            ),
            YamlEngineRank(
                Constants.STR_KAML,
                metrics.kaml.nanos,
                metrics.kaml.allocBytes,
                metrics.kaml.stdevNanos
            ),
            YamlEngineRank(
                Constants.STR_JACKSON,
                metrics.jackson.nanos,
                metrics.jackson.allocBytes,
                metrics.jackson.stdevNanos
            )
        ).sortedBy { if (it.nanos <= 0) Long.MAX_VALUE else it.nanos }

        println("| RANK | ENGINE   | TOTAL(ms)       | MEM(KB)    |")
        println("|------|----------|-----------------|------------|")

        rankings.forEachIndexed { index, rank ->
            val totalMs = rank.nanos / Constants.DIVISOR_MS
            val stDevMs = rank.stDevNanos / Constants.DIVISOR_MS
            val memKb = rank.mem / Constants.DIVISOR_KB

            val timeStr = if (stDevMs > 0) {
                "%7.3f ±%-5.3f".format(totalMs, stDevMs)
            } else {
                "%7.3f        ".format(totalMs)
            }

            println("| %-4d | %-8s | %-15s | %10.1f |".format(index + 1, rank.name, timeStr, memKb))
        }

        val winner = rankings.first()
        val slowest = rankings.last()
        if (winner.nanos > 0 && slowest.nanos > 0) {
            val speedVsSlowest = ((slowest.nanos.toDouble() / winner.nanos.toDouble()) - 1.0) * Constants.PERCENT_MULTIPLIER
            val memSavedVsSlowest = if (slowest.mem > 0) {
                ((slowest.mem.toDouble() - winner.mem.toDouble()) / slowest.mem.toDouble()) * Constants.PERCENT_MULTIPLIER
            } else {
                0.0
            }
            val memString = if (memSavedVsSlowest >= 0.0) {
                Constants.MSG_MEM_LESS.format(memSavedVsSlowest)
            } else {
                Constants.MSG_MEM_MORE.format(-memSavedVsSlowest)
            }
            println(
                Constants.MSG_WINNER.format(
                    winner.name,
                    speedVsSlowest,
                    slowest.name,
                    memString
                )
            )
        }
    }

    private fun printTwitterResults(
        categories: List<Pair<String, List<Pair<String, Triple<Double, Double, Double>>>>>
    ) {
        println(Constants.MSG_TWITTER_SUMMARY)
        println("| Operation          | Engine  | Throughput (ops/s) |  StDev (ops/s) | Mem (KB/op) |")
        println("|--------------------|---------|---------------------|----------------|-------------|")
        for ((label, engines) in categories) {
            val sorted = engines
                .filter { it.second.first > 0.0 }
                .sortedByDescending { it.second.first }

            for (res in sorted) {
                println("| %-18s | %-7s | %19.3f | %14.3f | %11.1f |".format(
                    label, res.first, res.second.first, res.second.second, res.second.third
                ))
            }
            if (sorted.size >= 2) {
                val winner = sorted[0]
                val loser = sorted[sorted.size - 1]
                val pct = ((winner.second.first - loser.second.first) / loser.second.first) * Constants.PERCENT_MULTIPLIER
                val memPct = if (loser.second.third > 0) {
                    ((loser.second.third - winner.second.third) / loser.second.third) * Constants.PERCENT_MULTIPLIER
                } else {
                    0.0
                }
                val memString = if (memPct >= 0.0) {
                    Constants.MSG_MEM_LESS.format(memPct)
                } else {
                    Constants.MSG_MEM_MORE.format(-memPct)
                }
                println(
                    Constants.MSG_WINNER_SIMPLE.format(
                        label, winner.first, pct, memString, loser.first
                    )
                )
            }
            println("|--------------------|---------|---------------------|----------------|-------------|")
        }
    }

    private fun generateComplexData(count: Int): ComplexResponse {
        val history = IntArray(Constants.VAL_1000) { it }
        val meta = ExtremeMetadata(
            System.currentTimeMillis(),
            UserRole.EDITOR,
            listOf(Constants.STR_BETA),
            1.2e-4,
            history
        )
        val users = List(count) { i ->
            BenchUser(
                i,
                "${Constants.STR_USER_PREFIX}$i",
                Constants.STR_EMAIL,
                1.0,
                true,
                UserRole.VIEWER,
                null
            )
        }
        return ComplexResponse(
            Constants.STR_SUCCESS,
            users,
            meta,
            Constants.STR_42
        )
    }

    private fun createTree(d: Int): Category = if (d <= 0) {
        Category(name = Constants.STR_LEAF)
    } else {
        Category(
            name = Constants.STR_NODE,
            subCategories = listOf(createTree(d - 1))
        )
    }

    private inline fun measureAvgFailSpeed(block: () -> Unit): Long {
        val startTime = System.nanoTime()
        repeat(Constants.FAIL_WARMUP_RUNS) { block() }
        return (System.nanoTime() - startTime) / Constants.FAIL_WARMUP_RUNS
    }

    @Volatile
    private var blackHoleSink: Any? = null
    private fun consume(obj: Any?) {
        blackHoleSink = obj
    }

    private inline fun measureTimeNanos(block: () -> Unit): Long {
        val startTimeNanos = System.nanoTime()
        block()
        return System.nanoTime() - startTimeNanos
    }

    private inline fun <T> measurePerf(
        threadBean: ThreadMXBean?,
        block: () -> T
    ): Triple<T, Long, Long> {
        val currentThreadId = Thread.currentThread().id
        val startAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L
        val startTimeNanos = System.nanoTime()

        val result = block()

        val endTimeNanos = System.nanoTime()
        val endAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L

        consume(result)
        val durationNanos = endTimeNanos - startTimeNanos
        val allocatedBytes = endAllocatedBytes - startAllocatedBytes

        return Triple(result, durationNanos, allocatedBytes)
    }

    private inline fun <T> measureTwitterPerf(
        threadBean: ThreadMXBean?,
        runs: Int,
        crossinline block: () -> T
    ): Triple<Double, Double, Double> {
        val numBatches = if (runs >= Constants.BATCHES) Constants.BATCHES else 1
        val runsPerBatch = runs / numBatches

        val currentThreadId = Thread.currentThread().id
        val startAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L
        val startTime = System.nanoTime()

        val batchThroughputs = DoubleArray(numBatches)
        repeat(numBatches) { b ->
            val start = System.nanoTime()
            repeat(runsPerBatch) {
                val res = block()
                consume(res)
            }
            val elapsed = System.nanoTime() - start
            val batchThroughput = runsPerBatch / (elapsed.toDouble() / Constants.NANOSECONDS_IN_SECOND)
            batchThroughputs[b] = batchThroughput
        }

        val elapsedNanos = System.nanoTime() - startTime
        val endAllocatedBytes = threadBean?.getThreadAllocatedBytes(currentThreadId) ?: 0L

        val avgThroughput = runs / (elapsedNanos.toDouble() / Constants.NANOSECONDS_IN_SECOND)

        val stdDev = if (numBatches > 1) {
            val mean = batchThroughputs.average()
            val variance = batchThroughputs.map { (it - mean) * (it - mean) }.sum() / (numBatches - 1)
            sqrt(variance)
        } else {
            0.0
        }

        val allocatedBytes = endAllocatedBytes - startAllocatedBytes
        val kbPerOp = if (allocatedBytes > 0) (allocatedBytes.toDouble() / runs) / Constants.DIVISOR_KB else 0.0

        return Triple(avgThroughput, stdDev, kbPerOp)
    }

    fun runEdgeDatasetsBenchmark(runs: Int, warmupIters: Int, threadBean: ThreadMXBean?) {
        val files = listOf(
            "spring_boot_app.yaml",
            "edge_multiline.yaml",
            "edge_flow_style.yaml",
            "edge_explicit_tags.yaml",
            "edge_anchors.yaml",
            "edge_polymorphism.yaml"
        )

        println("\n========================================================")
        println("BENCHMARK: YAML EDGE CASE / TEST DATASETS (GENERIC PARSING)")
        println("========================================================")

        for (fileName in files) {
            val resource = object {}.javaClass.classLoader.getResource("yaml/$fileName")
            if (resource == null) {
                println("  ⚠️  Skipping $fileName benchmark: not found.")
                continue
            }
            val yamlString = resource.readText()
            val yamlBytes = yamlString.encodeToByteArray()

            println("\n🔥 Warming up $fileName models...")
            repeat(warmupIters) {
                try { GhostYamlFlatReader(yamlBytes).readDocument() } catch (t: Throwable) {}
                try { kaml.parseToYamlNode(yamlString) } catch (t: Throwable) {}
                try { jacksonYaml.readTree(yamlString) } catch (t: Throwable) {}
            }

            performGc()
            println("🚀 Measuring $fileName performance...")

            val ghostScore = try {
                measureTwitterPerf(threadBean, runs) {
                    GhostYamlFlatReader(yamlBytes).readDocument()
                }
            } catch (t: Throwable) {
                Triple(0.0, 0.0, 0.0)
            }

            performGc()
            val kamlScore = try {
                measureTwitterPerf(threadBean, runs) {
                    kaml.parseToYamlNode(yamlString)
                }
            } catch (t: Throwable) {
                Triple(0.0, 0.0, 0.0)
            }

            performGc()
            val jacksonScore = try {
                measureTwitterPerf(threadBean, runs) {
                    jacksonYaml.readTree(yamlString)
                }
            } catch (t: Throwable) {
                Triple(0.0, 0.0, 0.0)
            }

            println("\n--- Performance Summary for $fileName ---")
            printTwitterResults(
                listOf(
                    "Parse Generic" to listOf(Constants.STR_GHOST to ghostScore, Constants.STR_KAML to kamlScore, Constants.STR_JACKSON to jacksonScore)
                )
            )
        }
    }

    private fun performGc() {
        System.gc()
        System.runFinalization()
        Thread.sleep(Constants.SLEEP_MS)
        System.gc()
        Thread.sleep(Constants.SLEEP_SHORT_MS)
    }
}

internal data class YamlBenchmarkSessionResults(
    val listMedium: YamlModeMetrics,
    val syncLarge: YamlModeMetrics,
    val writing: YamlModeMetrics,
    val stress: YamlBenchmarkMetrics,
    val failure: YamlBenchmarkMetrics
)

internal data class YamlModeMetrics(
    val string: YamlBenchmarkMetrics,
    val bytes: YamlBenchmarkMetrics
)

internal data class YamlBenchmarkMetrics(
    val ghost: BenchResult,
    val kaml: BenchResult,
    val jackson: BenchResult
)
