package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.benchmark.CharacterResponse
import com.ghost.serialization.benchmark.GhostCharacter
import com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization
import com.ghost.serialization.sample.ui.JankTracker
import com.ghost.serialization.sample.ui.model.NetworkStack
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
        Ghost.addRegistry(GhostModuleRegistry_ghost_serialization.INSTANCE)
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
     * Fetches characters using the specified network stack.
     */
    suspend fun fetchCharacters(
        stack: NetworkStack, page: Int
    ): CharacterResponse = withContext(Dispatchers.Default) {
        when (stack) {
            NetworkStack.GHOST_KTOR -> {
                ghostKtorClient.get("https://rickandmortyapi.com/api/character") {
                    parameter("page", page)
                }.body()
            }
            NetworkStack.KTOR_KOTLINX -> {
                kserKtorClient.get("https://rickandmortyapi.com/api/character") {
                    parameter("page", page)
                }.body()
            }
            NetworkStack.KTORFIT_KOTLINX -> {
                KtorfitClient.service.getCharacters(page)
            }
        }
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
            }

            // We simulate a large contiguous payload by joining results
            // This ensures we keep the CharacterResponse schema valid
            val jsonString = if (pageCount == 1) {
                allBytes[0].decodeToString()
            } else {
                val firstPage = allBytes[0].decodeToString()
                val infoPart = firstPage.substringBefore("\"results\":")
                val resultsList = allBytes.joinToString(",") { 
                    val s = it.decodeToString()
                    s.substringAfter("\"results\":")
                     .substringBeforeLast("}")
                     .trim()
                     .removePrefix("[")
                     .removeSuffix("]")
                }
                "$infoPart\"results\": [$resultsList]}"
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
            
            // 2. MEASURE GHOST
            val ghostResult = measureEngine("GHOST", jankTracker, onStatusChange) {
                Ghost.deserialize<CharacterResponse>(rawBytes)
            }

            // 3. MEASURE KSER
            val kserResult = measureEngine("KSER", jankTracker, onStatusChange) {
                kserJson.decodeFromString<CharacterResponse>(rawBytes.decodeToString())
            }

            // 4. MEASURE BASELINE (Blank block)
            val baselineResult = measureEngine("BLANK", jankTracker, onStatusChange) {
                // Empty block
            }

            val serializer = Ghost.getSerializer(CharacterResponse::class)
            val serializerName = serializer?.let { it::class.simpleName } ?: "None"
            
            // This will show up in Logcat under "System.out" tag
            println(">>> [GHOST_DEBUG] Active Serializer: $serializerName")
            println(">>> [GHOST_DEBUG] Registry Instance: ${GhostModuleRegistry_ghost_serialization.INSTANCE}")
            
            onStatusChange("Finalizing... Serializer: $serializerName")

            val allCharacters = Ghost.deserialize<CharacterResponse>(rawBytes).results

            Result.success(
                GhostResult(
                    data = allCharacters,
                    networkTimeMs = 0.0, // Network is not part of this benchmark
                    parseTimeMs = ghostResult.timeMs,
                    ghostMemoryBytes = ghostResult.memoryBytes,
                    ghostJankCount = ghostResult.jankCount,
                    engineResults = listOf(kserResult, baselineResult)
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
        crossinline block: () -> T
    ): EngineResult {
        val iterations = 100
        var totalTimeMs = 0.0
        var totalMemBytes = 0L
        var totalJank = 0

        // Stabilization
        forceGC()
        delay(500)

        onStatusChange("Benchmarking $name (100 iterations)...")
        repeat(iterations) {
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
