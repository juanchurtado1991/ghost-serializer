package com.ghost.benchmark

/**
 * Named benchmark suites — each Gradle task runs one suite in its own JVM process.
 *
 * @property cliName first CLI argument passed to [main].
 * @property regressionGate when `true`, the suite feeds [RegressionCalculator.report] and
 *   fails the process on regression; Ghost-only suites set this to `false`.
 */
internal enum class BenchmarkSuite(
    val cliName: String,
    val regressionGate: Boolean,
) {
    /** Full README suite: cold start, synthetic, special, rawjson, twitter, and regression. */
    FULL("full", regressionGate = true),

    /** LIST / SYNC / WRITING synthetic harness with partial regression gate. */
    SYNTHETIC("synthetic", regressionGate = true),

    /** Twitter macro Ghost vs Moshi vs KSER with partial regression gate. */
    TWITTER("twitter", regressionGate = true),

    /** Ghost-only special features (polymorphism, RawJson envelope, protobuf WKTs, etc.). */
    SPECIAL("special", regressionGate = false),

    /** Ghost-only [com.ghost.serialization.types.RawJson] byte vs string channels. */
    RAWJSON("rawjson", regressionGate = false),

    /** Ghost-only YAML round-trip on KSP-generated [com.ghost.serialization.yaml.contract.GhostYamlSerializer]. */
    YAML("yaml", regressionGate = false),

    /** Ghost-only proto3 JSON round-trip via [com.ghost.serialization.proto.GhostProto]. */
    PROTO("proto", regressionGate = false),
    ;

    companion object {
        /** Resolves a suite from its [cliName], or throws if the name is unknown. */
        fun fromCliName(name: String): BenchmarkSuite {
            return entries.firstOrNull { it.cliName == name }
                ?: error(
                    "Unknown benchmark suite '$name'. " +
                            "Use: ${entries.joinToString { it.cliName }}"
                )
        }
    }
}
