package com.ghost.serialization.sample.api

import com.ghost.protobuf.GhostProtobuf
import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.model.OpenLibraryResponse
import com.ghost.serialization.sample.util.forceGC
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

class OpenLibraryRepository {

    private val downloadClient = HttpClient {
        expectSuccess = false
    }

    private val kSerJson = Json {
        ignoreUnknownKeys = true
    }

    suspend fun fetchAndBenchmark(
        query: String,
        onStatusChange: (String) -> Unit
    ): Result<BooksLabResult> = withContext(Dispatchers.Default) {
        try {
            // ── Step 1: Download real bytes from OpenLibrary ──────────────────────────
            onStatusChange("Fetching '$query' from OpenLibrary API...")
            val response: HttpResponse = downloadClient.get("https://openlibrary.org/search.json") {
                parameter("q", query)
                parameter("limit", 40)
            }

            if (!response.status.isSuccess()) {
                throw Exception("OpenLibrary API Error: ${response.status}")
            }
            
            val standardJson = response.bodyAsText()
            val standardBytes = standardJson.encodeToByteArray()

            // ── Step 2: Synthesize proto3 JSON ────────────────────────────────────
            // Proto3 JSON encoding quotes numeric fields (int32/int64/double) as strings.
            onStatusChange("Synthesizing proto3 JSON variant...")
            val proto3Json = toProto3JsonString(standardJson)
            val proto3Bytes = proto3Json.encodeToByteArray()

            // ── Step 3: Parse books for the UI (standard path) ────────────────────
            val responseObj: OpenLibraryResponse = GhostProtobuf.deserialize(standardBytes)

            // ── Step 4: Warmup all engines ────────────────────────────────────────
            onStatusChange("JIT Warmup (${BenchmarkEngine.WARMUP_ITERATIONS}x)...")
            warmUpEngines(standardBytes, standardJson, proto3Bytes)

            // ── Step 5: Benchmark ─────────────────────────────────────────────────
            val results = buildList {
                addAll(benchmarkStandardJson(standardBytes, standardJson, onStatusChange))
                addAll(benchmarkProto3Json(proto3Bytes, proto3Json, onStatusChange))
            }

            onStatusChange("Done!")
            Result.success(
                BooksLabResult(
                    books = responseObj.docs,
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
                element.booleanOrNull != null -> element
                else -> JsonPrimitive(element.content)
            }
        }
        else -> element
    }

    // ── Benchmarks ────────────────────────────────────────────────────────────────

    private suspend fun benchmarkStandardJson(
        standardBytes: ByteArray,
        standardJson: String,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghost = BenchmarkEngine.measure("[Standard] Ghost (byte[])", onStatusChange) {
            Ghost.deserialize<OpenLibraryResponse>(standardBytes)
        }
        val ghostProto = BenchmarkEngine.measure("[Standard] GhostProto (byte[])", onStatusChange) {
            GhostProtobuf.deserialize<OpenLibraryResponse>(standardBytes)
        }
        val kSer = BenchmarkEngine.measure("[Standard] KotlinX-Ser (String)", onStatusChange) {
            kSerJson.decodeFromString<OpenLibraryResponse>(standardJson)
        }
        return listOf(ghost, ghostProto, kSer)
    }

    private suspend fun benchmarkProto3Json(
        proto3Bytes: ByteArray,
        proto3Json: String,
        onStatusChange: (String) -> Unit
    ): List<EngineResult> {
        val ghostProto = BenchmarkEngine.measure("[Proto3] GhostProto (byte[])", onStatusChange) {
            GhostProtobuf.deserialize<OpenLibraryResponse>(proto3Bytes)
        }

        val ghost = try {
            BenchmarkEngine.measure("[Proto3] Ghost (byte[])", onStatusChange) {
                Ghost.deserialize<OpenLibraryResponse>(proto3Bytes)
            }
        } catch (e: Exception) {
            EngineResult("[Proto3] Ghost (byte[])", -1.0, -1L)
        }

        val kSer = try {
            BenchmarkEngine.measure("[Proto3] KotlinX-Ser (String)", onStatusChange) {
                kSerJson.decodeFromString<OpenLibraryResponse>(proto3Json)
            }
        } catch (e: Exception) {
            EngineResult("[Proto3] KotlinX-Ser (String)", -1.0, -1L)
        }

        return listOf(ghostProto, ghost, kSer)
    }

    // ── Warm-Up ───────────────────────────────────────────────────────────────────

    private fun warmUpEngines(
        standardBytes: ByteArray,
        standardJson: String,
        proto3Bytes: ByteArray
    ) {
        repeat(BenchmarkEngine.WARMUP_ITERATIONS) {
            Ghost.deserialize<OpenLibraryResponse>(standardBytes)
            GhostProtobuf.deserialize<OpenLibraryResponse>(standardBytes)
            kSerJson.decodeFromString<OpenLibraryResponse>(standardJson)

            GhostProtobuf.deserialize<OpenLibraryResponse>(proto3Bytes)
        }
        forceGC()
    }
}

data class BooksLabResult(
    val books: List<com.ghost.serialization.sample.model.OpenLibraryBook>,
    val standardJson: String,
    val proto3Json: String,
    val benchmarkResults: List<EngineResult>
)
