@file:OptIn(InternalGhostApi::class, NativeRuntimeApi::class)

package com.ghost.serialization.sample.api

import com.charleskorn.kaml.Yaml
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.encodeToYamlBytes
import com.ghost.serialization.sample.model.BenchUser
import com.ghost.serialization.sample.model.ComplexResponse
import com.ghost.serialization.sample.model.ExtremeMetadata
import com.ghost.serialization.sample.model.UserRole
import com.ghost.serialization.sample.util.forceGC
import com.ghost.serialization.sample.util.getCurrentThreadAllocatedBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.TimeSource

/**
 * iOS actual — benchmarks Ghost and KAML only.
 * Jackson is a JVM-only library and is not available on this platform.
 */
actual class YamlBenchmarkRepository actual constructor() {

    private val kaml = Yaml.default

    actual suspend fun runBenchmark(
        userCount: Int,
        onStatusChange: (String) -> Unit,
        onPreviewReady: (String) -> Unit
    ): Result<YamlBenchmarkResult> = withContext(Dispatchers.Default) {
        try {
            val sampleData = generateSampleData(userCount)

            onStatusChange("Encoding to YAML (Ghost)...")
            val ghostYamlString = Ghost.encodeToYaml(sampleData)
            val ghostYamlBytes = ghostYamlString.encodeToByteArray()
            onPreviewReady(ghostYamlString)

            onStatusChange("Warming up engines...")
            warmUp(sampleData, ghostYamlString, ghostYamlBytes)

            onStatusChange("Benchmarking [ENCODE_STRING] GHOST...")
            val encodeStringGhost = measureEngine("[ENCODE_STRING] GHOST") {
                Ghost.encodeToYaml(sampleData)
            }

            onStatusChange("Benchmarking [ENCODE_STRING] KAML...")
            val encodeStringKaml = measureEngine("[ENCODE_STRING] KAML") {
                kaml.encodeToString(sampleData)
            }

            onStatusChange("Benchmarking [ENCODE_BYTES] GHOST...")
            val encodeBytesGhost = measureEngine("[ENCODE_BYTES] GHOST") {
                Ghost.encodeToYamlBytes(sampleData)
            }

            onStatusChange("Benchmarking [ENCODE_BYTES] KAML...")
            val encodeBytesKaml = measureEngine("[ENCODE_BYTES] KAML") {
                kaml.encodeToString(sampleData).encodeToByteArray()
            }

            onStatusChange("Benchmarking [DECODE_STRING] GHOST...")
            val decodeStringGhost = measureEngine("[DECODE_STRING] GHOST") {
                Ghost.decodeFromYaml<ComplexResponse>(ghostYamlString)
            }

            onStatusChange("Benchmarking [DECODE_STRING] KAML...")
            val decodeStringKaml = measureEngine("[DECODE_STRING] KAML") {
                kaml.decodeFromString<ComplexResponse>(ghostYamlString)
            }

            onStatusChange("Benchmarking [DECODE_BYTES] GHOST...")
            val decodeBytesGhost = measureEngine("[DECODE_BYTES] GHOST") {
                Ghost.decodeFromYaml<ComplexResponse>(ghostYamlBytes)
            }

            onStatusChange("Benchmarking [DECODE_BYTES] KAML...")
            val decodeBytesKaml = measureEngine("[DECODE_BYTES] KAML") {
                kaml.decodeFromString<ComplexResponse>(ghostYamlBytes.decodeToString())
            }

            onStatusChange("Done!")

            val engineResults = listOf(
                encodeStringGhost, encodeStringKaml,
                encodeBytesGhost, encodeBytesKaml,
                decodeStringGhost, decodeStringKaml,
                decodeBytesGhost, decodeBytesKaml
            )

            Result.success(
                YamlBenchmarkResult(
                    engineResults = engineResults,
                    previewYaml = ghostYamlString
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun warmUp(
        sampleData: ComplexResponse,
        yamlString: String,
        yamlBytes: ByteArray
    ) {
        repeat(WARMUP_ITERATIONS) {
            Ghost.encodeToYaml(sampleData)
            kaml.encodeToString(sampleData)

            Ghost.encodeToYamlBytes(sampleData)
            kaml.encodeToString(sampleData).encodeToByteArray()

            Ghost.decodeFromYaml<ComplexResponse>(yamlString)
            kaml.decodeFromString<ComplexResponse>(yamlString)

            Ghost.decodeFromYaml<ComplexResponse>(yamlBytes)
            kaml.decodeFromString<ComplexResponse>(yamlBytes.decodeToString())
        }
        forceGC()
    }

    private inline fun measureEngine(
        name: String,
        crossinline block: () -> Unit
    ): EngineResult {
        forceGC()

        var totalTimeNs = 0L
        var totalMem = 0L

        repeat(BENCHMARK_ITERATIONS) {
            val memStart = getCurrentThreadAllocatedBytes()
            val start = TimeSource.Monotonic.markNow()

            block()

            val durationNs = start.elapsedNow().inWholeNanoseconds
            val memEnd = getCurrentThreadAllocatedBytes()

            totalTimeNs += durationNs
            totalMem += if (memEnd >= memStart) memEnd - memStart else 0L
        }

        return EngineResult(
            name = name,
            timeMs = (totalTimeNs / BENCHMARK_ITERATIONS.toDouble()) / NANOS_PER_MILLI,
            memoryBytes = totalMem / BENCHMARK_ITERATIONS
        )
    }

    private fun generateSampleData(userCount: Int): ComplexResponse {
        val history = IntArray(HISTORY_SIZE) { it }
        val metadata = ExtremeMetadata(
            timestamp = FIXED_TIMESTAMP,
            role = UserRole.EDITOR,
            tags = listOf("yaml", "ghost", "benchmark"),
            precision = 1.2e-4,
            history = history
        )
        val users = List(userCount) { index ->
            BenchUser(
                id = index,
                name = "User $index",
                email = "user$index@ghost.io",
                isActive = index % 2 == 0,
                score = index * 1.5
            )
        }
        return ComplexResponse(
            status = "success",
            users = users,
            metadata = metadata,
            shards = mapOf("primary" to "us-east-1", "replica" to "eu-west-1")
        )
    }

    companion object {
        private const val WARMUP_ITERATIONS = 50
        private const val BENCHMARK_ITERATIONS = 100
        private const val NANOS_PER_MILLI = 1_000_000.0
        private const val HISTORY_SIZE = 100
        private const val FIXED_TIMESTAMP = 1_700_000_000_000L
    }
}
