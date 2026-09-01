package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import kotlinx.serialization.json.Json

/**
 * Moshi is JVM-only, so on Wasm this actual substitutes kotlinx.serialization to preserve
 * round-trip semantics and keep the browser lab's three-phase timing (KSER → Moshi slot → Ghost).
 */
actual object MoshiBench {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    actual fun roundTrip(payload: String) {
        json.encodeToString(json.decodeFromString<TwitterResponse>(payload))
    }
}
