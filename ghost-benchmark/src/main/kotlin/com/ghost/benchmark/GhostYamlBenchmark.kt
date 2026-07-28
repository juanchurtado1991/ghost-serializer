@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.encodeToYamlBytes
import com.ghost.serialization.integration.model.YamlBenchUser
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only YAML round-trip benchmark (no KSER/Moshi equivalent).
 *
 * Exercises KSP-generated [com.ghost.serialization.yaml.contract.GhostYamlSerializer] paths on the
 * integration fixture [com.ghost.serialization.integration.model.YamlBenchUser].
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
        return true
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
