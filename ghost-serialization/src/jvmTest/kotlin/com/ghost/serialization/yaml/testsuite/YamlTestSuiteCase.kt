package com.ghost.serialization.yaml.testsuite

/**
 * One case from the vendored yaml-test-suite snapshot (see `yaml-test-suite/README.md`).
 *
 * Not a `data class` — [inYamlBytes] is a [ByteArray], and instances here are never compared by
 * value, only looked up/iterated by [id].
 */
class YamlTestSuiteCase(
    val id: String,
    val label: String,
    val inYamlBytes: ByteArray,
    val inJsonText: String?,
    val expectError: Boolean,
)
