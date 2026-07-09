@file:OptIn(InternalGhostApi::class, ExperimentalSerializationApi::class)

package com.ghost.serialization.sample.api

import com.ghost.protobuf.GhostProtobuf
import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.sample.model.BooksVolumeListResponse
import com.ghost.serialization.sample.model.BookVolume
import com.ghost.serialization.sample.util.forceGC
import com.ghost.serialization.sample.util.getCurrentThreadAllocatedBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.time.TimeSource

/**
 * Fetches real data from the Google Books Volumes API and runs a full benchmark
 * comparing GhostProtobuf, Ghost (JSON), and kotlinx-serialization.
 *
 * The benchmark covers two payload variants:
 *   1. Standard JSON  — the real response as downloaded
 *   2. Proto3 JSON    — the same response but with numeric fields (pageCount, totalItems,
 *                       ratingsCount, averageRating) serialized as quoted strings, exactly
 *                       as proto3 JSON encoding mandates for int64/double fields.
 *                       GhostProtobuf handles both variants transparently via coercion.
 */
class GoogleBooksRepository {

    private val downloadClient = HttpClient {
        install(ContentNegotiation) { ghost() }
    }

    private val kSerJson = Json { ignoreUnknownKeys = true }

    suspend fun fetchAndBenchmark(
        query: String,
        onStatusChange: (String) -> Unit
    ): Result<BooksLabResult> = withContext(Dispatchers.Default) {
        try {
            // ── Step 1: Download real bytes from Google Books ─────────────────────
            onStatusChange("Fetching '$query' from Google Books API...")
            val response = downloadClient.get("https://www.googleapis.com/books/v1/volumes") {
                parameter("q", query)
                parameter("maxResults", 20)
                parameter("printType", "books")
                parameter("langRestrict", "en")
            }
            if (!response.status.isSuccess()) {
                return@withContext Result.failure(
                    Exception("Network error: ${response.status}")
                )
            }
            val standardBytes: ByteArray = response.body()
            val standardJson = standardBytes.decodeToString()

            // ── Step 2: Synthesize a proto3 JSON variant ──────────────────────────
            // Proto3 JSON encoding quotes numeric fields (int32/int64/double) as strings.
            // This is what real gRPC-Gateway and Cloud APIs return in proto3 JSON mode.
            onStatusChange("Synthesizing proto3 JSON variant...")
            val proto3Json = toProto3JsonString(standardJson)
            val proto3Bytes = proto3Json.encodeToByteArray()

            // ── Step 3: Parse books for the UI (standard path) ────────────────────
            val books: BooksVolumeListResponse = GhostProtobuf.deserialize(standardBytes)

            // ── Step 4: Warmup all engines ────────────────────────────────────────
            onStatusChange("JIT Warmup (${WARMUP_ITERATIONS}x)...")
            warmUpEngines(standardBytes, standardJson, proto3Bytes)

            // ── Step 5: Benchmark ─────────────────────────────────────────────────
            val results = buildList {
                addAll(benchmarkStandardJson(standardBytes, standardJson, onStatusChange))
                addAll(benchmarkProto3Json(proto3Bytes, proto3Json, onStatusChange))
            }

            onStatusChange("Done!")
            Result.success(
                BooksLabResult(
                    books = books.items,
                    standardJson = standardJson,
                    proto3Json = proto3Json,
                    benchmarkResults = results
                )
            )
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    // ── Proto3 JSON synthesis ─────────────────────────────────────────────────────
    //
    // Converts all numeric JSON primitives to their quoted-string equivalents,
    // simulating the proto3 JSON wire format used by gRPC-Gateway and Google Cloud APIs.
    // Booleans are kept as-is (proto3 maps bool → JSON bool, not string).

    private fun toProto3JsonString(standardJson: String): String {
        val root = kSerJson.parseToJsonElement(standardJson)
        return kSerJson.encodeToString(JsonElement.serializer(), quoteNumbers(root))
    }

    private fun quoteNumbers(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (_, v) -> quoteNumbers(v) })
        is JsonArray -> JsonArray(element.map { quoteNumbers(it) })
        is JsonPrimitive -> {
            when {
                element.isString -> element
                element == JsonNull -> element
                element.booleanOrNull != null -> element  // keep booleans as-is
                else -> JsonPrimitive(element.content)    // quote number → string
            }
        }
        else -> element
    }

    // ── Warmup ────────────────────────────────────────────────────────────────────

    private fun warmUpEngines(
        standardBytes: ByteArray,
        standardJson: String,
        proto3Bytes: ByteArray
    ) {
        repeat(WARMUP_ITERATIONS) {
            // Standard JSON paths
            GhostProtobuf.deserialize<BooksVolumeListResponse>(standardBytes)
            Ghost.deserialize<BooksVolumeListResponse>(standardBytes)
            kSerJson.decodeFromString(BooksVolumeListResponse.serializer(), standardJson)

            // Proto3 JSON path (coercion — GhostProtobuf only)
            GhostProtobuf.deserialize<BooksVolumeListResponse>(proto3Bytes)

            // Serialize
            val obj: BooksVolumeListResponse = GhostProtobuf.deserialize(standardBytes)
            Ghost.serialize(obj)
            GhostProtobuf.encodeToString(obj)
            kSerJson.encodeToString(BooksVolumeListResponse.serializer(), obj)
        }
        forceGC()
    }

    // ── Standard JSON benchmarks ─────────────────────────────────────────────────

    private suspend fun benchmarkStandardJson(
        bytes: ByteArray,
        json: String,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghostProto = measureEngine("[PARSE/JSON] Ghost Proto", onStatusChange) {
            GhostProtobuf.deserialize<BooksVolumeListResponse>(bytes)
        }
        val ghostJson = measureEngine("[PARSE/JSON] Ghost JSON", onStatusChange) {
            Ghost.deserialize<BooksVolumeListResponse>(bytes)
        }
        val kSer = measureEngine("[PARSE/JSON] KotlinX-Ser", onStatusChange) {
            kSerJson.decodeFromString(BooksVolumeListResponse.serializer(), json)
        }
        val obj: BooksVolumeListResponse = GhostProtobuf.deserialize(bytes)
        val ghostSerialize = measureEngine("[SERIALIZE] Ghost Proto", onStatusChange) {
            GhostProtobuf.encodeToString(obj)
        }
        val kSerSerialize = measureEngine("[SERIALIZE] KotlinX-Ser", onStatusChange) {
            kSerJson.encodeToString(BooksVolumeListResponse.serializer(), obj)
        }
        return listOf(ghostProto, ghostJson, kSer, ghostSerialize, kSerSerialize)
    }

    // ── Proto3 JSON benchmarks ────────────────────────────────────────────────────
    //
    // Only GhostProtobuf can handle proto3 JSON (quoted numbers).
    // Ghost JSON and kotlinx-serialization fail/misparse — measured to show the gap.

    private suspend fun benchmarkProto3Json(
        proto3Bytes: ByteArray,
        proto3Json: String,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        // GhostProtobuf: coerces quoted numbers transparently
        val ghostProto = measureEngine("[PARSE/PROTO3] Ghost Proto", onStatusChange) {
            GhostProtobuf.deserialize<BooksVolumeListResponse>(proto3Bytes)
        }
        // Ghost JSON: no proto3 coercion — numbers-as-strings resolve to 0
        val ghostJson = measureEngine("[PARSE/PROTO3] Ghost JSON", onStatusChange) {
            Ghost.deserialize<BooksVolumeListResponse>(proto3Bytes)
        }
        // kotlinx-serialization: fails on quoted numbers — captured as failure result
        val kSerResult = runCatching {
            measureEngine("[PARSE/PROTO3] KotlinX-Ser", onStatusChange) {
                kSerJson.decodeFromString(BooksVolumeListResponse.serializer(), proto3Json)
            }
        }.getOrElse {
            EngineResult(name = "[PARSE/PROTO3] KotlinX-Ser ❌", timeMs = -1.0, memoryBytes = 0)
        }
        return listOf(ghostProto, ghostJson, kSerResult)
    }

    // ── Measurement utility ───────────────────────────────────────────────────────

    private suspend fun measureEngine(
        name: String,
        onStatusChange: (String) -> Unit,
        block: suspend () -> Unit
    ): EngineResult {
        onStatusChange("Benchmarking $name...")
        forceGC()

        var totalTimeNs = 0L
        var totalMemoryBytes = 0L
        repeat(BENCHMARK_ITERATIONS) {
            val memStart = getCurrentThreadAllocatedBytes()
            val mark = TimeSource.Monotonic.markNow()

            block()

            val durationNs = mark.elapsedNow().inWholeNanoseconds
            val memEnd = getCurrentThreadAllocatedBytes()

            totalTimeNs += durationNs
            totalMemoryBytes += if (memEnd >= memStart) memEnd - memStart else 0L
        }

        return EngineResult(
            name = name,
            timeMs = (totalTimeNs / BENCHMARK_ITERATIONS.toDouble()) / NANOS_PER_MILLI,
            memoryBytes = totalMemoryBytes / BENCHMARK_ITERATIONS
        )
    }

    companion object {
        private const val WARMUP_ITERATIONS = 200
        private const val BENCHMARK_ITERATIONS = 100
        private const val NANOS_PER_MILLI = 1_000_000.0
    }
}

data class BooksLabResult(
    val books: List<BookVolume>,
    val standardJson: String,
    val proto3Json: String,
    val benchmarkResults: List<EngineResult>
)
