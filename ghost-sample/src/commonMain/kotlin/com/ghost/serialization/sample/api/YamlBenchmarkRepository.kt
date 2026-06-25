package com.ghost.serialization.sample.api

expect class YamlBenchmarkRepository() {
    suspend fun runBenchmark(
        userCount: Int,
        onStatusChange: (String) -> Unit,
        onPreviewReady: (String) -> Unit
    ): Result<YamlBenchmarkResult>
}
