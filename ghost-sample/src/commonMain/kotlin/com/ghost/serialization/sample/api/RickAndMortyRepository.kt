@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import com.ghost.serialization.ktor.bodyGhost
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class RickAndMortyRepository {

    init {
        Ghost.addRegistry(GhostModuleRegistry_serialization_sample.INSTANCE)
        Ghost.prewarm()
    }

    private val kSerJson = Json { ignoreUnknownKeys = true }

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

            val networkResults = benchmarkNetworkStack(
                stressData.bytes,
                onStatusChange
            )

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

            if (!response.status.isSuccess()) {
                throw Exception("Network Error on Page $i")
            }

            allBytes.add(response.body<ByteArray>())

            // Respect the API rate limit
            delay(150.milliseconds)
        }

        val jsonString = mergePages(allBytes, pageCount)
        val bytes = jsonString.encodeToByteArray()
        return StressData(jsonString, bytes)
    }

    private fun mergePages(allBytes: List<ByteArray>, pageCount: Int): String {
        if (pageCount == 1) return allBytes[0].decodeToString()
        val responses = allBytes.map {
            kSerJson.decodeFromString<CharacterResponse>(
                string = it.decodeToString()
            )
        }

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

        return kSerJson.encodeToString(
            serializer = CharacterResponse.serializer(),
            value = merged
        )
    }

    // ── Warm-Up (fair: same data, same iterations for everyone) ─────────────────

    private fun warmUpEngines(
        data: StressData,
        onStatusChange: (String) -> Unit
    ) {
        onStatusChange("Aggressive JIT Warmup (${BenchmarkEngine.WARMUP_ITERATIONS}x)...")

        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)

        repeat(BenchmarkEngine.WARMUP_ITERATIONS) {
            // 1. Warmup Deserialization (Parse)
            // Bytes path
            Ghost.deserialize<CharacterResponse>(data.bytes)
            kSerJson.decodeFromString<CharacterResponse>(data.bytes.decodeToString())

            // String path
            Ghost.deserialize<CharacterResponse>(data.jsonString)
            kSerJson.decodeFromString<CharacterResponse>(data.jsonString)

            // Stream/Buffer path (Okio)
            val bufReadGhost = Buffer().write(data.bytes)
            Ghost.deserialize<CharacterResponse>(bufReadGhost)
            
            val bufReadKser = Buffer().write(data.bytes)
            kSerJson.decodeFromBufferedSource(
                deserializer = CharacterResponse.serializer(),
                source = bufReadKser
            )

            // 2. Warmup Serialization (Write)
            // Bytes path
            Ghost.encodeToBytes(obj)
            kSerJson.encodeToString(CharacterResponse.serializer(), obj).encodeToByteArray()

            // String path
            Ghost.serialize(obj)
            kSerJson.encodeToString(CharacterResponse.serializer(), obj)

            // Stream/Buffer path (Okio)
            val bufWriteGhost = Buffer()
            Ghost.encodeToSink(bufWriteGhost, obj)

            val bufWriteKser = Buffer()
            kSerJson.encodeToBufferedSink(
                serializer = CharacterResponse.serializer(),
                value = obj,
                sink = bufWriteKser
            )
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

        val ghostMockClient = HttpClient(
            engine = MockEngine { _ ->
                respond(
                content = networkBytes,
                status = HttpStatusCode.OK,
                    headers = headersOf(
                        name = HttpHeaders.ContentType,
                        value = "application/json"
                    )
                )
            }
        ) {
            install(ContentNegotiation) { ghost() }
        }

        val kSerMockClient = HttpClient(
            engine = MockEngine { _ ->
                respond(
                content = networkBytes,
                status = HttpStatusCode.OK,
                    headers = headersOf(
                        name = HttpHeaders.ContentType,
                        value = "application/json"
                    )
                )
            }
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val ghostNet = BenchmarkEngine.measure("[NETWORK] GHOST (Ktor)", onStatusChange) {
            ghostMockClient.get("https://rickandmortyapi.com/api/character/")
                .body<CharacterResponse>()
        }

        val kSerNet = BenchmarkEngine.measure("[NETWORK] KSER (Ktor)", onStatusChange) {
            kSerMockClient.get("https://rickandmortyapi.com/api/character/")
                .body<CharacterResponse>()
        }

        ghostMockClient.close()
        kSerMockClient.close()
        return listOf(ghostNet, kSerNet)
    }

    // ── Deserialization: STRING Mode ─────────────────────────────────────────────

    private suspend fun benchmarkParseString(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghost = BenchmarkEngine.measure("[PARSE_STRING] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.jsonString)
        }

        val kSer = BenchmarkEngine.measure("[PARSE_STRING] KSER", onStatusChange) {
            kSerJson.decodeFromString<CharacterResponse>(data.jsonString)
        }

        return listOf(ghost, kSer)
    }

    // ── Deserialization: BYTES Mode ──────────────────────────────────────────────

    private suspend fun benchmarkParseBytes(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {

        val ghost = BenchmarkEngine.measure("[PARSE_BYTES] GHOST", onStatusChange) {
            Ghost.deserialize<CharacterResponse>(data.bytes)
        }

        val kSer = BenchmarkEngine.measure("[PARSE_BYTES] KSER", onStatusChange) {
            kSerJson.decodeFromString<CharacterResponse>(data.bytes.decodeToString())
        }

        return listOf(ghost, kSer)
    }

    // ── Serialization: STRING Mode ───────────────────────────────────────────────

    private suspend fun benchmarkWriteString(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)

        val ghost = BenchmarkEngine.measure("[WRITE_STRING] GHOST", onStatusChange) {
            Ghost.serialize(obj)
        }

        val kSer = BenchmarkEngine.measure("[WRITE_STRING] KSER", onStatusChange) {
            kSerJson.encodeToString(CharacterResponse.serializer(), obj)
        }

        return listOf(ghost, kSer)
    }

    // ── Serialization: BYTES Mode ────────────────────────────────────────────────

    private suspend fun benchmarkWriteBytes(
        data: StressData,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {

        val obj = Ghost.deserialize<CharacterResponse>(data.bytes)

        val ghost = BenchmarkEngine.measure("[WRITE_BYTES] GHOST", onStatusChange) {
            Ghost.encodeToBytes(obj)
        }

        val kSer = BenchmarkEngine.measure("[WRITE_BYTES] KSER", onStatusChange) {
            kSerJson
                .encodeToString(CharacterResponse.serializer(), obj)
                .encodeToByteArray()
        }

        return listOf(ghost, kSer)
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

        val ghost = BenchmarkEngine.measure("[PARSE_STREAM] GHOST", onStatusChange) {
            val buf = Buffer().write(data.bytes)
            Ghost.deserialize<CharacterResponse>(buf)
        }

        val kSer = BenchmarkEngine.measure("[PARSE_STREAM] KSER", onStatusChange) {
            val buf = Buffer().write(data.bytes)
            kSerJson.decodeFromBufferedSource(
                deserializer = CharacterResponse.serializer(),
                source = buf
            )
        }

        return listOf(ghost, kSer)
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

        val ghost = BenchmarkEngine.measure("[WRITE_BUFFER] GHOST", onStatusChange) {
            val buf = Buffer()
            Ghost.encodeToSink(buf, obj)
        }

        val kSer = BenchmarkEngine.measure("[WRITE_BUFFER] KSER", onStatusChange) {
            val buf = Buffer()
            kSerJson.encodeToBufferedSink(
                serializer = CharacterResponse.serializer(),
                value = obj,
                sink = buf
            )
        }

        return listOf(ghost, kSer)
    }

    // ── Initial data fetch for UI character list ─────────────────────────────────

    suspend fun fetchCharacters(
        page: Int
    ): CharacterResponse = withContext(Dispatchers.Default) {
        downloadClient.get("https://rickandmortyapi.com/api/character") {
            parameter("page", page)
        }.bodyGhost()
    }

    @Suppress("ArrayInDataClass")
    private data class StressData(
        val jsonString: String,
        val bytes: ByteArray
    )
}
