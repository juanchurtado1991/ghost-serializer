package com.ghost.serialization.sample.api

// Ktorfit is temporarily disabled due to Kotlin 2.3.10 compatibility issues
import com.ghost.serialization.benchmark.CharacterResponse
import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

interface RickAndMortyKtorfitService {
    @GET("character")
    suspend fun getCharacters(@Query("page") page: Int): CharacterResponse
}

object KtorfitClient {
    private val ktorfitJson = Json { ignoreUnknownKeys = true }
    
    private val ktorClient = HttpClient {
        install(ContentNegotiation) {
            json(ktorfitJson)
        }
    }

    private val ktorfit = Ktorfit.Builder()
        .baseUrl("https://rickandmortyapi.com/api/")
        .httpClient(ktorClient)
        .build()

    val service: RickAndMortyKtorfitService = ktorfit.createRickAndMortyKtorfitService()
}
