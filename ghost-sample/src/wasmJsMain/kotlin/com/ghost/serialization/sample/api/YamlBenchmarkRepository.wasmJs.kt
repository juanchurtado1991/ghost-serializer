package com.ghost.serialization.sample.api

actual class YamlBenchmarkRepository actual constructor() {
    actual suspend fun runBenchmark(
        userCount: Int,
        onStatusChange: (String) -> Unit,
        onPreviewReady: (String) -> Unit
    ): Result<YamlBenchmarkResult> {
        return Result.failure(
            UnsupportedOperationException("YAML benchmark not available on Wasm/JS")
        )
    }
}
