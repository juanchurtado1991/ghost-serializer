@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.charleskorn.kaml.Yaml
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.encodeToYamlBytes
import com.ghost.serialization.integration.model.YamlBenchUser
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only YAML round-trip benchmark, plus a Ghost-vs-kaml decode/encode comparison, both
 * exercising KSP-generated `GhostYamlSerializer` on the `YamlBenchUser` fixture.
 *
 * The kaml comparison is fixture-only — it is NOT a run against the official yaml-test-suite /
 * matrix.yaml.info spec-compliance matrix. Tracked separately:
 * https://github.com/juanchurtado1991/ghost-serializer/issues/17
 */
object GhostYamlBenchmark {

    private const val YAML_USER = """
id: 42
name: Ghost Benchmark
email: bench@ghost.io
score: 88.5
isActive: true
role: VIEWER
"""

    private const val YAML_USER_MINIMAL = """
id: 7
name: Neo
email: neo@matrix.io
score: 100.0
"""

    /**
     * Runs YAML decode, encode, and round-trip scenarios.
     *
     * @return `true` when the suite completes (always, including when ThreadMXBean is unavailable).
     */
    fun run(): Boolean {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping YAML benchmark.")
            return true
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 YAML ROUND-TRIP — GhostYamlSerializer (YamlBenchUser)")
        println("════════════════════════════════════════════════════════════════")

        measureString(
            threadBean,
            label = "Decode YamlBenchUser (YAML string)",
            yaml = YAML_USER,
        ) { text ->
            Ghost.decodeFromYaml<YamlBenchUser>(text)
        }

        measureBytes(
            threadBean,
            label = "Decode YamlBenchUser (YAML bytes)",
            yaml = YAML_USER,
        ) { bytes ->
            Ghost.decodeFromYaml<YamlBenchUser>(bytes)
        }

        val user = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER)

        measureString(
            threadBean,
            label = "Encode YamlBenchUser (encodeToYaml string)",
            yaml = YAML_USER,
        ) {
            Ghost.encodeToYaml(user)
        }

        measureBytes(
            threadBean,
            label = "Encode YamlBenchUser (encodeToYamlBytes)",
            yaml = YAML_USER,
        ) {
            Ghost.encodeToYamlBytes(user)
        }

        measureString(
            threadBean,
            label = "Round-trip (decode → encodeToYaml, minimal profile)",
            yaml = YAML_USER_MINIMAL,
        ) {
            val decoded = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER_MINIMAL)
            Ghost.encodeToYaml(decoded)
        }

        println("════════════════════════════════════════════════════════════════\n")

        runKamlComparison(threadBean)

        return true
    }

    /** Ghost vs kaml decode/encode comparison on the same [YamlBenchUser] fixture (see class doc). */
    private fun runKamlComparison(threadBean: ThreadMXBean) {
        val yamlText = YAML_USER.trimIndent()
        val payloadBytes = yamlText.encodeToByteArray().size.toLong()
        val serializer = YamlBenchUser.serializer()
        val decodedForEncode = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER)

        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) {
            Ghost.decodeFromYaml<YamlBenchUser>(yamlText)
            Yaml.default.decodeFromString(serializer, yamlText)
            Ghost.encodeToYaml(decodedForEncode)
            Yaml.default.encodeToString(serializer, decodedForEncode)
        }

        cleanHeap()
        val ghostDecode = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.decodeFromYaml<YamlBenchUser>(yamlText)
        }
        cleanHeap()
        val kamlDecode = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Yaml.default.decodeFromString(serializer, yamlText)
        }

        cleanHeap()
        val ghostEncode = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToYaml(decodedForEncode)
        }
        cleanHeap()
        val kamlEncode = measurePerf(threadBean, BenchmarkStandard.MEASUREMENT_RUNS) {
            Yaml.default.encodeToString(serializer, decodedForEncode)
        }

        printComparison(
            payloadBytes = payloadBytes,
            categories = listOf(
                "Decode (String)" to listOf("GHOST" to ghostDecode, "KAML" to kamlDecode),
                "Encode (String)" to listOf("GHOST" to ghostEncode, "KAML" to kamlEncode),
            ),
        )
    }

    private fun printComparison(
        payloadBytes: Long,
        categories: List<Pair<String, List<Pair<String, Triple<Double, Double, Double>>>>>,
    ) {
        println("\n--- Ghost vs kaml — YamlBenchUser fixture (fixture-only, NOT the yaml-test-suite matrix) ---")
        println(
            "  Payload: %d bytes → µs/op and decimal GB/s (ops/s × payload / 10⁹)".format(payloadBytes)
        )
        println("| Operation          | Engine | Throughput (GB/s) | Latency (µs/op) | Mem (KB/op) |")
        println("|--------------------|--------|-------------------|-----------------|-------------|")
        for ((label, scores) in categories) {
            val sorted = scores.sortedByDescending { it.second.first }
            for (res in sorted) {
                val ops = res.second.first
                val opsStdev = res.second.second
                val micros = BenchmarkThroughput.opsPerSecToMicros(ops)
                val microsStdev = if (ops <= 0.0) 0.0 else micros * (opsStdev / ops)
                val gb = BenchmarkThroughput.opsPerSecToGbPerSec(ops, payloadBytes)
                println(
                    "| %-18s | %-6s | %17.3f | %7.1f ±%-5.1f | %11.1f |".format(
                        label, res.first, gb, micros, microsStdev, res.second.third
                    )
                )
            }
            val winner = sorted[0]
            val slowest = sorted.last()
            val pct = ((winner.second.first - slowest.second.first) / slowest.second.first) * 100.0
            println(
                "   👉 WINNER for %s: %s (%.1f%% faster than %s)".format(
                    label, winner.first, pct, slowest.first
                )
            )
            println("|--------------------|--------|-------------------|-----------------|-------------|")
        }
    }

    @Volatile
    private var blackHoleSink: Any? = null
    private fun consume(obj: Any?) {
        blackHoleSink = obj
    }

    private fun cleanHeap() {
        System.gc()
        System.runFinalization()
    }

    private inline fun <T> measurePerf(
        threadBean: ThreadMXBean,
        runs: Int,
        crossinline block: () -> T,
    ): Triple<Double, Double, Double> {
        val currentThreadId = Thread.currentThread().id
        val startAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
        val startTime = System.nanoTime()

        val numBatches = if (runs >= 10) 10 else 1
        val runsPerBatch = runs / numBatches
        val batchThroughputs = DoubleArray(numBatches)
        repeat(numBatches) { b ->
            val start = System.nanoTime()
            repeat(runsPerBatch) {
                val res = block()
                consume(res)
            }
            val elapsed = System.nanoTime() - start
            batchThroughputs[b] = runsPerBatch / (elapsed.toDouble() / 1_000_000_000.0)
        }

        val elapsedNanos = System.nanoTime() - startTime
        val endAllocatedBytes = threadBean.getThreadAllocatedBytes(currentThreadId)
        val avgThroughput = runs / (elapsedNanos.toDouble() / 1_000_000_000.0)

        val stdDev = if (numBatches > 1) {
            val mean = batchThroughputs.average()
            val variance = batchThroughputs.map { (it - mean) * (it - mean) }.sum() / (numBatches - 1)
            kotlin.math.sqrt(variance)
        } else {
            0.0
        }

        val allocatedBytes = endAllocatedBytes - startAllocatedBytes
        val kbPerOp = if (allocatedBytes > 0) (allocatedBytes.toDouble() / runs) / 1024.0 else 0.0

        return Triple(avgThroughput, stdDev, kbPerOp)
    }

    private inline fun measureBytes(
        threadBean: ThreadMXBean,
        label: String,
        yaml: String,
        crossinline block: (ByteArray) -> Any?,
    ) {
        val payload = yaml.trimIndent().encodeToByteArray()
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(payload) }
        BenchmarkProgress.logStep("Measure: $label")
        report(threadBean, label, payloadBytes = payload.size.toLong(), block = { block(payload) })
    }

    private inline fun measureString(
        threadBean: ThreadMXBean,
        label: String,
        yaml: String,
        crossinline block: (String) -> Any?,
    ) {
        val payload = yaml.trimIndent()
        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) { block(payload) }
        BenchmarkProgress.logStep("Measure: $label")
        report(
            threadBean,
            label,
            payloadBytes = payload.encodeToByteArray().size.toLong(),
            block = { block(payload) },
        )
    }

    private inline fun report(
        threadBean: ThreadMXBean,
        label: String,
        payloadBytes: Long,
        crossinline block: () -> Any?,
    ) {
        val threadId = Thread.currentThread().id
        var totalNanos = 0L
        var totalAlloc = 0L

        repeat(BenchmarkStandard.MEASUREMENT_RUNS) {
            val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
            val timeBefore = System.nanoTime()
            block()
            totalNanos += System.nanoTime() - timeBefore
            totalAlloc += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
        }

        val avgMicros = totalNanos / BenchmarkStandard.MEASUREMENT_RUNS / 1_000.0
        val avgKb = (totalAlloc.toDouble() / BenchmarkStandard.MEASUREMENT_RUNS) / 1024.0
        val gbPerSec = BenchmarkThroughput.microsToGbPerSec(avgMicros, payloadBytes)
        println(
            "  %-58s │ %6.3f GB/s │ %8.2f µs/op │ %8.3f KB/op".format(
                label,
                gbPerSec,
                avgMicros,
                avgKb,
            )
        )
    }
}
