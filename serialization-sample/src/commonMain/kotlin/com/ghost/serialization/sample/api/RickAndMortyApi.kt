package com.ghostserializer.sample.api

import com.ghostserializer.Ghost
import com.ghostserializer.ktor.ghost
import com.ghostserializer.sample.domain.CharacterResponse
import com.ghostserializer.sample.domain.GhostCharacter
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.time.TimeSource


class RickAndMortyApi {
    private val client = HttpClient {
        install(ContentNegotiation) { ghost() }
    }

    suspend fun getCharacters(pageCount: Int = 1): Result<GhostResult<List<GhostCharacter>>> {
        return try {
            var totalNetworkTime = 0.0
            var totalGhostTime = 0.0
            var totalGhostMem = 0L
            
            val engineAggregates = mutableMapOf<String, Pair<Double, Long>>()

            val allCharacters = mutableListOf<GhostCharacter>()

            for (p in 1..pageCount) {
                val netStart = TimeSource.Monotonic.markNow()
                val response: HttpResponse =
                    client.get("https://rickandmortyapi.com/api/character/?page=$p")

                if (!response.status.isSuccess()) {
                    throw Exception("API Error (${response.status.value}) on page $p: ${response.status.description}")
                }

                val rawBytes = response.body<ByteArray>()
                val netEnd = TimeSource.Monotonic.markNow()
                totalNetworkTime += (netEnd - netStart).inWholeMicroseconds / 1000.0

                // Silent Warm-up (20 iterations)
                    repeat(20) {
                        Ghost.deserialize<CharacterResponse>(rawBytes)
                        parseWithMoshi(rawBytes)
                        parseWithKSer(rawBytes)
                        parseWithGson(rawBytes)
                    }

                // GHOST Benchmark
                val ghostStart = TimeSource.Monotonic.markNow()
                val ghostMemStart = getCurrentThreadAllocatedBytes()
                val ghostData = Ghost.deserialize<CharacterResponse>(rawBytes)
                val ghostMemEnd = getCurrentThreadAllocatedBytes()
                val ghostEnd = TimeSource.Monotonic.markNow()

                totalGhostTime += (ghostEnd - ghostStart).inWholeMicroseconds / 1000.0
                totalGhostMem += if (ghostMemStart >= 0 && ghostMemEnd >= 0) ghostMemEnd - ghostMemStart else 0L
                allCharacters.addAll(ghostData.results)

                // Benchmark other engines
                val engines = mapOf(
                    "MOSHI" to { parseWithMoshi(rawBytes) },
                    "K-SER" to { parseWithKSer(rawBytes) },
                    "GSON"  to { parseWithGson(rawBytes) }
                )

                engines.forEach { (name, benchmark) ->
                    val res = benchmark()
                    if (res.isSupported) {
                        val current = engineAggregates.getOrPut(name) { 0.0 to 0L }
                        engineAggregates[name] = (current.first + res.timeMs) to (current.second + res.allocatedBytes)
                    }
                }
            }

            val engineResults = engineAggregates.map { (name, data) ->
                EngineResult(name, data.first, data.second, true)
            }

            Result.success(
                GhostResult(
                    data = allCharacters,
                    networkTimeMs = totalNetworkTime,
                    parseTimeMs = totalGhostTime,
                    ghostMemoryBytes = totalGhostMem,
                    engineResults = engineResults
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
