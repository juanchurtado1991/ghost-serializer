package com.ghost.benchmark

import com.ghost.serialization.integration.model.ComplexResponse
import com.squareup.moshi.Moshi
import kotlinx.serialization.json.Json

/** Shared JSON engine instances (KotlinX Serialization, Moshi) for synthetic and Twitter suites. */
internal class BenchmarkEngines {
    val kJson = Json { ignoreUnknownKeys = true }
    val moshi: Moshi = createBenchmarkMoshi()
    val complexResponseAdapter = moshi.adapter(ComplexResponse::class.java)!!
}
