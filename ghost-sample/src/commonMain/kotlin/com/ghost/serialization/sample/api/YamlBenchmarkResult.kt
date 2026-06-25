package com.ghost.serialization.sample.api

/**
 * Wraps the results of a full YAML benchmark run.
 * [engineResults] contains timing/memory for each engine × scenario.
 * [previewYaml] is a trimmed sample of the generated YAML for display.
 */
data class YamlBenchmarkResult(
    val engineResults: List<EngineResult>,
    val previewYaml: String
)
