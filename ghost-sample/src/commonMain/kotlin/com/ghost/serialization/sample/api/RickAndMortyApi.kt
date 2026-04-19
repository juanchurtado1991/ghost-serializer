package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.sample.domain.CharacterResponse
import com.ghost.serialization.sample.domain.GhostCharacter
import com.ghost.serialization.sample.ui.JankTracker
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlin.time.TimeSource


class RickAndMortyApi {
    private val client = HttpClient {
        install(ContentNegotiation) { ghost() }
    }

    suspend fun getCharacters(
        pageCount: Int = 1, 
        jankTracker: JankTracker? = null,
        onStatusChange: (String) -> Unit = {}
    ): Result<GhostResult<List<GhostCharacter>>> {
        return try {
            var totalNetworkTime = 0.0
            var totalGhostTime = 0.0
            var totalGhostMem = 0L
            var totalGhostJank = 0
            
            val engineAggregates = mutableMapOf<String, Triple<Double, Long, Int>>()

            val allCharacters = mutableListOf<GhostCharacter>()

            for (p in 1..pageCount) {
                val netStart = TimeSource.Monotonic.markNow()
                val response: HttpResponse =
                    client.get("https://rickandmortyapi.com/api/character/?page=$p")
                
                onStatusChange("Processing Page $p...")

                if (!response.status.isSuccess()) {
                    throw Exception("API Error (${response.status.value}) on page $p: ${response.status.description}")
                }

                val rawBytes = response.body<ByteArray>()
                
                // NORMALIZATION: Decode String ONCE outside the benchmark blocks
                // This ensures KSer/Moshi/Gson don't pay for decodeToString() during the timing.
                val jsonString = rawBytes.decodeToString()
                
                val netEnd = TimeSource.Monotonic.markNow()
                totalNetworkTime += (netEnd - netStart).inWholeMicroseconds / 1000.0

                // Silent Warm-up (20 iterations)
                    onStatusChange("Warming up engines...")
                    repeat(20) {
                        Ghost.deserialize<CharacterResponse>(rawBytes)
                        parseWithMoshi(jsonString)
                        parseWithKSer(jsonString)
                        parseWithGson(jsonString)
                    }

                // GHOST Benchmark (Still uses rawBytes because it's its native strength)
                onStatusChange("Benchmarking GHOST...")
                jankTracker?.startTracking("GHOST")
                val ghostStart = TimeSource.Monotonic.markNow()
                val ghostMemStart = getCurrentThreadAllocatedBytes()
                val ghostData = Ghost.deserialize<CharacterResponse>(rawBytes)
                val ghostMemEnd = getCurrentThreadAllocatedBytes()
                val ghostEnd = TimeSource.Monotonic.markNow()
                delay(100)
                totalGhostJank += jankTracker?.stopTracking() ?: 0

                totalGhostTime += (ghostEnd - ghostStart).inWholeMicroseconds / 1000.0
                totalGhostMem += if (ghostMemStart >= 0 && ghostMemEnd >= 0) ghostMemEnd - ghostMemStart else 0L
                allCharacters.addAll(ghostData.results)

                // Benchmark other engines (Using the pre-decoded jsonString)
                val engines = mapOf(
                    "MOSHI" to { parseWithMoshi(jsonString) },
                    "K-SER" to { parseWithKSer(jsonString) },
                    "GSON"  to { parseWithGson(jsonString) }
                )

                engines.forEach { (name, benchmark) ->
                    onStatusChange("Benchmarking $name...")
                    jankTracker?.startTracking(name)
                    val res = benchmark()
                    delay(100)
                    val jank = jankTracker?.stopTracking() ?: 0
                    
                    if (res.isSupported) {
                        val current = engineAggregates.getOrPut(name) { Triple(0.0, 0L, 0) }
                        engineAggregates[name] = Triple(current.first + res.timeMs, current.second + res.allocatedBytes, current.third + jank)
                    }
                }
            }

            val engineResults = engineAggregates.map { (name, data) ->
                EngineResult(name, data.first, data.second, true, data.third)
            }

            Result.success(
                GhostResult(
                    data = allCharacters,
                    networkTimeMs = totalNetworkTime,
                    parseTimeMs = totalGhostTime,
                    ghostMemoryBytes = totalGhostMem,
                    ghostJankCount = totalGhostJank,
                    engineResults = engineResults
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
