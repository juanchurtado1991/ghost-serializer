@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.sample.model.CharacterResponse
import com.ghost.serialization.sample.model.PageInfo
import com.ghost.serialization.sample.util.forceGC
import com.ghost.serialization.sample.util.getCurrentThreadAllocatedBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import kotlin.time.TimeSource

class RickAndMortyRepository {

    init {
        Ghost.addRegistry(GhostModuleRegistry_serialization_sample.INSTANCE)
        Ghost.prewarm()
    }

    private val kserJson = Json { ignoreUnknownKeys = true }

    // Real client for downloading stress data (used once)
    private val downloadClient = HttpClient {
        install(ContentNegotiation) { ghost() }
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    suspend fun runBenchmark(
        pageCount: Int,
        onStatusChange: (String) -> Unit
    ): Result<List<EngineResult>> = withContext(Dispatchers.Default) {
        try {
            val stressData = downloadStressData(pageCount, onStatusChange)
            warmUpEngines(stressData, onStatusChange)

            val networkResults = benchmarkNetworkStack(stressData.bytes, onStatusChange)

            val parseStringResults = benchmarkParseString(stressData, onStatusChange)
            val parseBytesResults = benchmarkParseBytes(stressData, onStatusChange)
            val parseStreamResults = benchmarkParseStream(stressData, onStatusChange)

            val writeStringResults = benchmarkWriteString(stressData, onStatusChange)
            val writeBytesResults = benchmarkWriteBytes(stressData, onStatusChange)
            val writeBufferResults = benchmarkWriteBuffer(stressData, onStatusChange)

            onStatusChange("Done!")
            Result.success(
                networkResults +
                    parseStringResults +
                    parseBytesResults +
                    parseStreamResults +
                    writeStringResults +
                    writeBytesResults +
                    writeBufferResults
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Data Download ───────────────────────────────────────────────────────────

    private suspend fun downloadStressData(
        pageCount: Int,
        onStatusChange: (String) -> Unit
    ): StressData {
        val allBytes = mutableListOf<ByteArray>()
        onStatusChange("Downloading Stress Data ($pageCount pages)...")
        for (i in 1..pageCount) {
            onStatusChange("Downloading Page $i/$pageCount...")
            val response: HttpResponse = downloadClient.get(
                "https://rickandmortyapi.com/api/character/"
            ) { parameter("page", i) }
            if (!response.status.isSuccess()) throw Exception("Network Error on Page $i")
            allBytes.add(response.body<ByteArray>())
            delay(150) // Respect the API rate limit
        }

        val jsonString = mergePages(allBytes, pageCount)
        val bytes = jsonString.encodeToByteArray()
        return StressData(jsonString, bytes)
    }

    private fun mergePages(allBytes: List<ByteArray>, pageCount: Int): String {
        if (pageCount == 1) return allBytes[0].decodeToString()
        val responses =
            allBytes.map { kserJson.decodeFromString<CharacterResponse>(it.decodeToString()) }
        val mergedResults = responses.flatMap { it.results }
        val merged = CharacterResponse(
            info = PageInfo(
                count = mergedResults.size,
                pages = responses.size,
                next = null,
                prev = null
            ),
            results = mergedResults
        )
        return kserJson.encodeToString(CharacterResponse.serializer(), merged)
    }

    // ── Warm-Up (fair: same data, same iterations for everyone) ─────────────────

    private suspend fun warmUpEngines(
        data: StressData,
        onStatusChange: (String) -> Unit
    ) {
        onStatusChange("Aggressive JIT Warmup (${WARMUP_ITERATIONS}x)...")
        repeat(WARMUP_ITERATIONS) {
            Ghost.deserialize<CharacterResponse>(data.bytes)
            kserJson.decodeFromString<CharacterResponse>(data.jsonString)
        }
        forceGC()
    }

    // ── Network Stack Benchmarks (MockEngine — measures converter only) ──────────
    //
    // Both clients receive the exact same bytes via MockEngine.
    // This eliminates network variance and 429 errors.
    // What's measured: content negotiation + JSON parsing overhead.

    private suspend fun benchmarkNetworkStack(
        networkBytes: ByteArray,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        onStatusChange("Benchmarking NETWORK STACKS (converter only, local replay)...")

        val ghostMockClient = HttpClient(MockEngine { _ ->
            respond(
                content = networkBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { ghost() }
        }

        val kserMockClient = HttpClient(MockEngine { _ ->
            respond(
                content = networkBytes,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        val ghostNet = measureEngine("[NETWORK] GHOST (Ktor)", onStatusChange) {
            ghostMockClient.get("https://rickandmortyapi.com/api/character/")
                .body<CharacterResponse>()
        }
        val kserNet = measureEngine("[NETWORK] KSER (Ktor)", onStatusChange) {
            kserMockClient.get("https://rickandmortyapi.com/api/character/")
                .body<CharacterResponse>()
        }

        ghostMockClient.close()
        kserMockClient.close()
        return listOf(ghostNet, kserNet)
    }

    // ── Deserialization: STRING Mode ─────────────────────────────────────────────

    private suspend fun benchmarkParseString(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_STRING] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.jsonString)
        }
        val kser = measureEngine("[PARSE_STRING] KSER", onStatusChange) {
            kserJson.decodeFromString<CharacterResponse>(data.jsonString)
        }
        return listOf(ghost, kser)
    }

    // ── Deserialization: BYTES Mode ──────────────────────────────────────────────

    private suspend fun benchmarkParseBytes(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_BYTES] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.bytes)
        }
        // KSer doesn't have a native ByteArray decode path — matches real-world usage
        val kser = measureEngine("[PARSE_BYTES] KSER", onStatusChange) {
            kserJson.decodeFromString<CharacterResponse>(data.bytes.decodeToString())
        }
        return listOf(ghost, kser)
    }

    // ── Serialization: STRING Mode ───────────────────────────────────────────────

    private suspend fun benchmarkWriteString(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)
        val ghost = measureEngine("[WRITE_STRING] GHOST", onStatusChange) {
            Ghost.serialize(obj)
        }
        val kser = measureEngine("[WRITE_STRING] KSER", onStatusChange) {
            kserJson.encodeToString(CharacterResponse.serializer(), obj)
        }
        return listOf(ghost, kser)
    }

    // ── Serialization: BYTES Mode ────────────────────────────────────────────────

    private suspend fun benchmarkWriteBytes(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)
        val ghost = measureEngine("[WRITE_BYTES] GHOST", onStatusChange) {
            Ghost.encodeToBytes(obj)
        }
        val kser = measureEngine("[WRITE_BYTES] KSER", onStatusChange) {
            kserJson.encodeToString(CharacterResponse.serializer(), obj).encodeToByteArray()
        }
        return listOf(ghost, kser)
    }

    // ── Deserialization: STREAM Mode ─────────────────────────────────────────────
    //
    // Ghost reads directly from a BufferedSource (Okio native path — zero copy).
    // KSer uses kotlinx-serialization-json-okio's decodeFromBufferedSource extension.
    // Both get a fresh Buffer filled with the same bytes each iteration.

    private suspend fun benchmarkParseStream(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghost = measureEngine("[PARSE_STREAM] GHOST", onStatusChange) {
            val buf = Buffer().write(data.bytes)
            Ghost.deserialize<CharacterResponse>(buf)
        }
        val kser = measureEngine("[PARSE_STREAM] KSER", onStatusChange) {
            val buf = Buffer().write(data.bytes)
            kserJson.decodeFromBufferedSource(CharacterResponse.serializer(), buf)
        }
        return listOf(ghost, kser)
    }

    // ── Serialization: BUFFER (Sink) Mode ────────────────────────────────────────
    //
    // Ghost writes directly into an Okio Buffer sink.
    // KSer uses kotlinx-serialization-json-okio's encodeToSink extension.

    private suspend fun benchmarkWriteBuffer(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)
        val ghost = measureEngine("[WRITE_BUFFER] GHOST", onStatusChange) {
            val buf = Buffer()
            Ghost.encodeToSink(buf, obj)
        }
        val kser = measureEngine("[WRITE_BUFFER] KSER", onStatusChange) {
            val buf = Buffer()
            kserJson.encodeToBufferedSink(CharacterResponse.serializer(), obj, buf)
        }
        return listOf(ghost, kser)
    }

    // ── Measurement Utility ──────────────────────────────────────────────────────
    //
    // Identical methodology for every engine:
    //   - Single forceGC() before the run to level the heap
    //   - 100 iterations averaged
    //   - Thread-local allocation tracking

    private suspend fun measureEngine(
        name: String,
        onStatusChange: (String) -> Unit,
        block: suspend () -> Unit
    ): EngineResult {
        onStatusChange("Benchmarking $name...")
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

    // ── Initial data fetch for UI character list ─────────────────────────────────

    suspend fun fetchCharacters(page: Int): CharacterResponse = withContext(Dispatchers.Default) {
        downloadClient.get("https://rickandmortyapi.com/api/character") {
            parameter("page", page)
        }.body()
    }

    @Suppress("ArrayInDataClass")
    private data class StressData(val jsonString: String, val bytes: ByteArray)

    companion object {
        private const val WARMUP_ITERATIONS = 200
        private const val BENCHMARK_ITERATIONS = 100
        private const val NANOS_PER_MILLI = 1_000_000.0
    }
}
