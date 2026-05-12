@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.sample.model.CharacterResponse
import com.ghost.serialization.sample.model.GhostCharacter
import com.ghost.serialization.sample.model.PageInfo
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import com.ghost.serialization.sample.ui.JankTracker
import com.ghost.serialization.sample.util.forceGC
import com.ghost.serialization.sample.util.getCurrentThreadAllocatedBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

class RickAndMortyRepository {

    init {
        Ghost.addRegistry(GhostModuleRegistry_serialization_sample.INSTANCE)
    }

    private val kserJson = Json { ignoreUnknownKeys = true }
    
    private val ghostKtorClient = HttpClient {
        install(ContentNegotiation) { ghost() }
    }

    private val kserKtorClient = HttpClient {
        install(ContentNegotiation) { 
            json(Json { ignoreUnknownKeys = true }) 
        }
    }

    /**
     * Fetches characters using Ghost + Ktor.
     */
    suspend fun fetchCharacters(
        page: Int
    ): CharacterResponse = withContext(Dispatchers.Default) {
        ghostKtorClient.get("https://rickandmortyapi.com/api/character") {
            parameter("page", page)
        }.body()
    }

    /**
     * Runs a professional head-to-head benchmark.
     */
    suspend fun runBenchmark(
        pageCount: Int,
        jankTracker: JankTracker?,
        onStatusChange: (String) -> Unit
    ): Result<GhostResult<List<GhostCharacter>>> = withContext(Dispatchers.Default) {
        try {
            onStatusChange("Downloading Stress Data ($pageCount pages)...")
            
            val allBytes = mutableListOf<ByteArray>()
            for (i in 1..pageCount) {
                onStatusChange("Downloading Page $i/$pageCount...")
                val response: HttpResponse = ghostKtorClient.get("https://rickandmortyapi.com/api/character/") {
                    parameter("page", i)
                }
                if (!response.status.isSuccess()) throw Exception("Network Error on Page $i")
                allBytes.add(response.body<ByteArray>())
                delay(200) // Respect the API during setup
            }

            // We simulate a large contiguous payload by joining results correctly
            val jsonString = try {
                if (pageCount == 1) {
                    allBytes[0].decodeToString()
                } else {
                    onStatusChange("Merging $pageCount pages...")
                    val responses = allBytes.mapIndexed { index, bytes ->
                        try {
                            kserJson.decodeFromString<CharacterResponse>(bytes.decodeToString())
                        } catch (e: Exception) {
                            throw Exception("Error parsing page ${index + 1}: ${e.message}")
                        }
                    }
                    val mergedResults = responses.flatMap { it.results }
                    val mergedResponse = CharacterResponse(
                        info = PageInfo(
                            count = mergedResults.size,
                            pages = responses.size,
                            next = null,
                            prev = null
                        ),
                        results = mergedResults
                    )
                    kserJson.encodeToString(CharacterResponse.serializer(), mergedResponse)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Benchmark Error: ${e.message}"))
            }
            val rawBytes = jsonString.encodeToByteArray()

            // 1. INTENSIVE WARM-UP (50 iterations)
            // Ensures JIT is hot and Ghost's internal buffers are ready
            onStatusChange("Warming up engines (50x)...")
            repeat(50) {
                Ghost.deserialize<CharacterResponse>(rawBytes)
                kserJson.decodeFromString<CharacterResponse>(jsonString)
            }
            forceGC()
            
            // ─── SUITE 1: PURE ENGINE BATTLE (In-Memory Parsing) ───
            onStatusChange("Engine Battle: GHOST vs KSER (Pure Parsing)...")
            
            val ghostPure = measureEngine("GHOST PURE", jankTracker, onStatusChange) {
                Ghost.deserialize<CharacterResponse>(rawBytes)
            }

            val kserPure = measureEngine("KSER PURE", jankTracker, onStatusChange) {
                kserJson.decodeFromString<CharacterResponse>(jsonString)
            }

            // ─── SUITE 2: FULL STACK BATTLE (Network + Integration) ───
            // We use a smaller loop (10 iterations) to avoid network ban but get a real average
            onStatusChange("Full Stack: GHOST+KTOR vs KSER+KTOR (Real Network)...")
            
            val ghostFull = measureEngine("GHOST + KTOR", jankTracker, onStatusChange, iterations = 3) {
                for (i in 1..pageCount) {
                    val response = ghostKtorClient.get("https://rickandmortyapi.com/api/character") {
                        parameter("page", i)
                    }
                    if (!response.status.isSuccess()) {
                        throw Exception("API Error ${response.status.value}: ${response.status.description}")
                    }
                    response.body<CharacterResponse>()
                    delay(50) // Respectful delay
                }
            }

            val kserFull = measureEngine("KTOR + KSER", jankTracker, onStatusChange, iterations = 3) {
                for (p in 1..pageCount) {
                    val response = kserKtorClient.get("https://rickandmortyapi.com/api/character") {
                        parameter("page", p)
                    }
                    if (!response.status.isSuccess()) {
                        throw Exception("API Error ${response.status.value}: ${response.status.description}")
                    }
                    response.body<CharacterResponse>()
                    delay(50) // Respectful delay
                }
            }


            val serializer = Ghost.getSerializer(CharacterResponse::class)
            val serializerName = serializer?.let { it::class.simpleName } ?: "None"
            
            // This will show up in Logcat under "System.out" tag
            println(">>> [GHOST_DEBUG] Active Serializer: $serializerName")
            println(">>> [GHOST_DEBUG] Registry Instance: ${GhostModuleRegistry_serialization_sample.INSTANCE}")
            
            onStatusChange("Finalizing... Serializer: $serializerName")

            val allCharacters = Ghost.deserialize<CharacterResponse>(rawBytes).results

            Result.success(
                GhostResult(
                    data = allCharacters,
                    networkTimeMs = ghostFull.timeMs, 
                    parseTimeMs = ghostPure.timeMs,
                    ghostMemoryBytes = ghostPure.memoryBytes,
                    ghostJankCount = ghostPure.jankCount,
                    engineResults = listOf(kserPure, ghostFull, kserFull)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <T> measureEngine(
        name: String,
        jankTracker: JankTracker?,
        onStatusChange: (String) -> Unit,
        iterations: Int = 100,
        crossinline block: suspend () -> T
    ): EngineResult {
        var totalTimeMs = 0.0
        var totalMemBytes = 0L
        var totalJank = 0

        // Stabilization
        forceGC()
        delay(500)

        onStatusChange("Benchmarking $name ($iterations iterations)...")
        for (i in 0 until iterations) {
            jankTracker?.startTracking(name)
            
            // 1. Precise Memory Start
            val memStart = getCurrentThreadAllocatedBytes()
            
            // 2. High Resolution Time Start
            val start = TimeSource.Monotonic.markNow()
            
            block()
            
            val end = TimeSource.Monotonic.markNow()
            val memEnd = getCurrentThreadAllocatedBytes()
            
            totalTimeMs += (end - start).inWholeMicroseconds / 1000.0
            totalMemBytes += if (memEnd >= memStart) memEnd - memStart else 0L
            
            delay(50)
            totalJank += jankTracker?.stopTracking() ?: 0
        }

        return EngineResult(
            name = name,
            timeMs = totalTimeMs / iterations,
            memoryBytes = totalMemBytes / iterations,
            isSupported = true,
            jankCount = totalJank
        )
    }
}
