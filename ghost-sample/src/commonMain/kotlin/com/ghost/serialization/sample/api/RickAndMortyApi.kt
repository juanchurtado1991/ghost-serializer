package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.domain.CharacterResponse
import com.ghost.serialization.ktor.ghost
import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlin.time.TimeSource

/**
 * Industrial Ktorfit Service definition.
 * This interface is processed by KSP to generate the native implementation.
 */
interface RickAndMortyService {
    @GET("character/")
    suspend fun getCharacters(@Query("page") page: Int): CharacterResponse
}

class RickAndMortyApi {
    private val client = HttpClient {
        install(ContentNegotiation) {
            ghost()
        }
    }

    private val ktorfit = Ktorfit.Builder()
        .baseUrl("https://rickandmortyapi.com/api/")
        .httpClient(client)
        .build()

    private val service = ktorfit.createRickAndMortyService()

    suspend fun getCharacters(page: Int = 1): Result<GhostResult<CharacterResponse>> {
        return try {
            // MEASUREMENT: Isolating Network Fetch from Ghost Parsing
            val netStart = TimeSource.Monotonic.markNow()
            val response: HttpResponse = client.get("https://rickandmortyapi.com/api/character/?page=$page")
            if (!response.status.isSuccess()) {
                throw Exception("API Error (${response.status.value}): ${response.status.description}")
            }
            val rawBytes = response.body<ByteArray>()
            val netEnd = TimeSource.Monotonic.markNow()
            
            val parseStart = TimeSource.Monotonic.markNow()
            val startMem = getCurrentThreadAllocatedBytes()
            
            val result = Ghost.deserialize<CharacterResponse>(rawBytes)
            
            val parseEnd = TimeSource.Monotonic.markNow()
            val endMem = getCurrentThreadAllocatedBytes()
            
            val networkTime = (netEnd - netStart).inWholeMicroseconds / 1000.0
            val parseTime = (parseEnd - parseStart).inWholeMicroseconds / 1000.0
            val totalMem = if (startMem >= 0 && endMem >= 0) endMem - startMem else 0L
            
            // For the comparison dashboard
            val moshiResult = parseWithMoshi(rawBytes)
            
            Result.success(GhostResult(
                data = result,
                networkTimeMs = networkTime,
                parseTimeMs = parseTime,
                moshiTimeMs = moshiResult.moshiTimeMs,
                ghostMemoryBytes = totalMem,
                moshiMemoryBytes = moshiResult.moshiAllocatedBytes
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
