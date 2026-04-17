package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.ktor.ghost
import com.ghost.serialization.sample.domain.CharacterResponse
import com.ghost.serialization.sample.domain.GhostCharacter
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
            var totalMoshiTime = 0.0
            var totalKserTime = 0.0

            var totalGhostMem = 0L
            var totalMoshiMem = 0L
            var totalKserMem = 0L

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

                // Silent Warm-up (5 iterations to induce JIT)
                repeat(5) {
                    Ghost.deserialize<CharacterResponse>(rawBytes)
                    parseWithMoshi(rawBytes)
                    parseWithKSer(rawBytes)
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

                // MOSHI Benchmark
                val moshiRes = parseWithMoshi(rawBytes)
                totalMoshiTime += moshiRes.timeMs
                totalMoshiMem += moshiRes.allocatedBytes

                // KSER Benchmark
                val kserRes = parseWithKSer(rawBytes)
                totalKserTime += kserRes.timeMs
                totalKserMem += kserRes.allocatedBytes
            }

            Result.success(
                GhostResult(
                    data = allCharacters,
                    networkTimeMs = totalNetworkTime,
                    parseTimeMs = totalGhostTime,
                    moshiTimeMs = totalMoshiTime,
                    kserTimeMs = totalKserTime,
                    ghostMemoryBytes = totalGhostMem,
                    moshiMemoryBytes = totalMoshiMem,
                    kserMemoryBytes = totalKserMem
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
