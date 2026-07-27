package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Moshi is JVM-only; on Wasm we keep the same round-trip semantics with kotlinx.serialization
 * so the browser lab still runs three timed phases (KSER → Moshi slot → Ghost).
 * JVM/desktop uses real Moshi codegen adapters via [MoshiBench.jvm.kt].
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
