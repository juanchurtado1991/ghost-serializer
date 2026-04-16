package com.ghost.serialization.sample.api

import com.ghost.serialization.Ghost
import com.ghost.serialization.sample.domain.CharacterResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readBytes

class RickAndMortyApi(private val client: HttpClient = HttpClient()) {

    suspend fun getCharacters(page: Int = 1): Result<CharacterResponse> {
        return try {
            val response: HttpResponse = client.get("https://rickandmortyapi.com/api/character/?page=$page")
            val bytes = response.readRawBytes()
            
            // This is the Ghost Serialization action. Pure native performance over ByteArray.
            val result = Ghost.deserialize<CharacterResponse>(bytes)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
