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
            threadBean = threadBean,
            label = "Decode YamlBenchUser (YAML string)",
            payload = YAML_USER.trimIndent(),
        ) { text ->
            Ghost.decodeFromYaml<YamlBenchUser>(text)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Decode YamlBenchUser (YAML bytes)",
            payload = YAML_USER.trimIndent(),
        ) { bytes ->
            Ghost.decodeFromYaml<YamlBenchUser>(bytes)
        }

        val user = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER)

        measureString(
            threadBean = threadBean,
            label = "Encode YamlBenchUser (encodeToYaml string)",
            payload = YAML_USER.trimIndent(),
        ) {
            Ghost.encodeToYaml(value = user)
        }

        measureBytes(
            threadBean = threadBean,
            label = "Encode YamlBenchUser (encodeToYamlBytes)",
            payload = YAML_USER.trimIndent(),
        ) {
            Ghost.encodeToYamlBytes(value = user)
        }

        measureString(
            threadBean = threadBean,
            label = "Round-trip (decode → encodeToYaml, minimal profile)",
            payload = YAML_USER_MINIMAL.trimIndent(),
        ) {
            val decoded = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER_MINIMAL)
            Ghost.encodeToYaml(value = decoded)
        }

        println("════════════════════════════════════════════════════════════════\n")

        runKamlComparison(threadBean = threadBean)

        return true
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
                val micros = BenchmarkThroughput.opsPerSecToMicros(opsPerSec = ops)
                val microsStdev = if (ops <= 0.0) 0.0 else micros * (opsStdev / ops)
                val gb = BenchmarkThroughput.opsPerSecToGbPerSec(opsPerSec = ops, payloadBytes = payloadBytes)
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

    /** Ghost vs kaml decode/encode comparison on the same [YamlBenchUser] fixture (see class doc). */
    private fun runKamlComparison(threadBean: ThreadMXBean) {
        val yamlText = YAML_USER.trimIndent()
        val payloadBytes = yamlText.encodeToByteArray().size.toLong()
        val serializer = YamlBenchUser.serializer()
        val decodedForEncode = Ghost.decodeFromYaml<YamlBenchUser>(YAML_USER)

        repeat(BenchmarkStandard.LOCAL_WARMUP_ITERATIONS) {
            Ghost.decodeFromYaml<YamlBenchUser>(yamlText)
            Yaml.default.decodeFromString(serializer, yamlText)
            Ghost.encodeToYaml(value = decodedForEncode)
            Yaml.default.encodeToString(serializer = serializer, value = decodedForEncode)
        }

        performPhaseGc()
        val ghostDecode = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.decodeFromYaml<YamlBenchUser>(yamlText)
        }
        performPhaseGc()
        val kamlDecode = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Yaml.default.decodeFromString(serializer, yamlText)
        }

        performPhaseGc()
        val ghostEncode = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Ghost.encodeToYaml(value = decodedForEncode)
        }
        performPhaseGc()
        val kamlEncode = measurePerf(threadBean = threadBean, runs = BenchmarkStandard.MEASUREMENT_RUNS) {
            Yaml.default.encodeToString(serializer = serializer, value = decodedForEncode)
        }

        printComparison(
            payloadBytes = payloadBytes,
            categories = listOf(
                "Decode (String)" to listOf("GHOST" to ghostDecode, "KAML" to kamlDecode),
                "Encode (String)" to listOf("GHOST" to ghostEncode, "KAML" to kamlEncode),
            ),
        )
    }
}
