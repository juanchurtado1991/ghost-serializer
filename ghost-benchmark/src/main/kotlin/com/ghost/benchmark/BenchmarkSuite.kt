package com.ghost.benchmark

/**
 * Independent benchmark entry points — each suite runs in its own JVM process via Gradle.
 */
internal enum class BenchmarkSuite(
    val cliName: String,
    val regressionGate: Boolean,
) {
    /** Full README suite: cold start, synthetic, special, rawjson, twitter + regression. */
    FULL("full", regressionGate = true),

    /** LIST / SYNC / WRITING synthetic harness + partial regression gate. */
    SYNTHETIC("synthetic", regressionGate = true),

    /** Twitter macro Ghost vs KSER + partial regression gate. */
    TWITTER("twitter", regressionGate = true),

    /** Ghost-only special features (polymorphism, RawJson envelope, etc.). */
    SPECIAL("special", regressionGate = false),

    /** Ghost-only RawJson byte vs string channels. */
    RAWJSON("rawjson", regressionGate = false),
    ;

    companion object {
        fun fromCliName(name: String): BenchmarkSuite {
            return entries.firstOrNull { it.cliName == name }
                ?: error(
                    "Unknown benchmark suite '$name'. " +
                        "Use: ${entries.joinToString { it.cliName }}"
                )
        }
    }
}
