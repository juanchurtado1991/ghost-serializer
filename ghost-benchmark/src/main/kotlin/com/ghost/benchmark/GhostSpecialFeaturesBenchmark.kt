@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.FlattenedModel
import com.ghost.serialization.integration.model.SmartDevice
import com.ghost.serialization.integration.model.SmartHome
import com.ghost.serialization.integration.model.ResilientItem
import com.ghost.serialization.integration.model.CustomCoderStressModel
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Ghost-only micro-benchmark for features that have no equivalent in other JSON libraries.
 *
 * These are measured independently because they cannot be compared fairly against
 * Gson, Moshi, KSer, or Jackson — they simply don't support these capabilities.
 *
 * Runs with the same ThreadMXBean methodology as the main benchmark.
 */
object GhostSpecialFeaturesBenchmark {

    private const val WARMUP_ITERATIONS = 5000

    fun run(runs: Int, warmupIters: Int) {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping special features benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        println("\n════════════════════════════════════════════════════════════════")
        println("  👻 GHOST SPECIAL FEATURES — EXCLUSIVE CAPABILITIES BENCHMARK")
        println("════════════════════════════════════════════════════════════════")
        println("  These features have NO equivalent in Gson, Moshi, KSer, or Jackson.")
        println("  Measured with $runs runs × ${warmupIters}-iteration JIT warmup.\n")

        // ─── 1. Polymorphism (Sealed Classes + Fallback) ─────────────
        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = "Polymorphism — Sealed Class Dispatch",
            jsonSamples = listOf(
                """{"id":"h1","devices":[{"type":"Light","brightness":80},{"type":"Thermostat","temperature":22.5},{"type":"Light","brightness":40}]}""",
                """{"id":"h2","devices":[{"type":"Thermostat","temperature":18.0},{"type":"FutureDevice","data":"x"}]}"""
            )
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        // ─── 2. Structural Flattening (@GhostFlatten) ────────────────
        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = "Structural Flattening — @GhostFlatten (3 levels deep)",
            jsonSamples = listOf(
                """{"id":1,"attributes":{"value":{"level":85},"status":"active"},"metadata":{"author":"Ghost"}}""",
                """{"id":2,"attributes":{"value":{"level":42},"status":"pending"}}"""
            )
        ) { json -> Ghost.deserialize<FlattenedModel>(json) }

        // ─── 3. Resilience (@GhostResilient) ─────────────────────────
        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = "Resilience — @GhostResilient (type mismatch recovery)",
            jsonSamples = listOf(
                """[{"id":"r1","value":10},{"id":"r2","value":"NOT_AN_INT"},{"id":"r3","value":30}]""",
                """[{"id":"r4","value":null},{"id":"r5","value":99}]"""
            )
        ) { json -> Ghost.deserialize<List<ResilientItem>>(json) }

        // ─── 4. Custom Decoders (@GhostDecoder) ─────────────────────
        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = "Custom Decoders — @GhostDecoder (hex + nullable transform)",
            jsonSamples = listOf(
                """{"id":"c1","secret":"AABBCC","score":null}""",
                """{"id":"c2","secret":"FF00FF","score":42}"""
            )
        ) { json -> Ghost.deserialize<CustomCoderStressModel>(json) }

        // ─── 5. Polymorphic Fallback (Unknown Type) ──────────────────
        benchmarkFeature(
            threadBean, runs, warmupIters,
            label = "Polymorphic Fallback — @GhostFallback (unknown discriminator)",
            jsonSamples = listOf(
                """{"id":"f1","devices":[{"type":"NeverSeenBefore","payload":"xyz"},{"type":"Light","brightness":50}]}""",
                """{"id":"f2","devices":[{"type":"AlienSensor"},{"type":"Thermostat","temperature":20.0}]}"""
            )
        ) { json -> Ghost.deserialize<SmartHome>(json) }

        println("════════════════════════════════════════════════════════════════\n")
    }

    private inline fun <reified T> benchmarkFeature(
        threadBean: ThreadMXBean,
        runs: Int,
        warmupIters: Int,
        label: String,
        jsonSamples: List<String>,
        crossinline deserialize: (String) -> T
    ) {
        // Warmup
        repeat(warmupIters) {
            for (sample in jsonSamples) {
                deserialize(sample)
            }
        }

        // Measure across all runs
        val threadId = Thread.currentThread().id
        var totalTimeNanos = 0L
        var totalAllocBytes = 0L
        val samplesPerRun = jsonSamples.size

        repeat(runs) {
            for (sample in jsonSamples) {
                val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
                val timeBefore = System.nanoTime()

                consume(deserialize(sample))

                totalTimeNanos += System.nanoTime() - timeBefore
                totalAllocBytes += threadBean.getThreadAllocatedBytes(threadId) - allocBefore
            }
        }

        val totalOps = runs.toLong() * samplesPerRun
        val avgTimeUs = totalTimeNanos / (totalOps * 1000.0)
        val avgAllocBytes = totalAllocBytes / totalOps
        val totalAllocKb = totalAllocBytes / 1024.0

        println(
            "  %-58s │ %6.2f µs/op │ %5d B/op │ %8.1f KB total".format(
                label,
                avgTimeUs,
                avgAllocBytes,
                totalAllocKb
            )
        )
    }
}
