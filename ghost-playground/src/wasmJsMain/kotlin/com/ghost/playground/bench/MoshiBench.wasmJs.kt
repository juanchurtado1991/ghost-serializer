package com.ghost.playground.bench

import com.ghost.playground.bench.model.TwitterResponse
import kotlinx.serialization.json.Json

/**
 * Moshi is JVM-only. On Wasm, this actual preserves round-trip semantics with
 * kotlinx.serialization so the browser lab still runs three timed phases
 * (KSER → Moshi slot → Ghost). On JVM and desktop, the JVM `MoshiBench` actual uses Moshi
 * codegen adapters.
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
