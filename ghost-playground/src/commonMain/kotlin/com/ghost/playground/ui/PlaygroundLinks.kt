package com.ghost.playground.ui

/** Absolute GitHub URLs for documentation opened from the playground on Wasm and JVM targets. */
object PlaygroundLinks {
    private const val REPO = "https://github.com/juanchurtado1991/ghost-serializer"
    private const val DOCS_BLOB = "$REPO/blob/main/docs"

    const val WIKI_QUICK_START = "$DOCS_BLOB/wiki/quick-start.md"
    const val WIKI_ADVANCED = "$DOCS_BLOB/wiki/advanced-features.md"
    const val WIKI_ARCHITECTURE = "$DOCS_BLOB/wiki/architecture.md"
    const val WIKI_BENCHMARKS = "$DOCS_BLOB/wiki/benchmarks.md"
    const val WIKI_USAGE_YAML =
        "$DOCS_BLOB/wiki/usage-yaml.md#2-supported-annotations-on-yaml-paths"
    const val WIKI_USAGE_PROTOBUF =
        "$DOCS_BLOB/wiki/usage-protobuf.md#2-supported-annotations-on-proto3-json-paths"
    const val MANUAL_MD = "$DOCS_BLOB/GHOST_MANUAL_EN.md"
    const val MANUAL_PDF = "$DOCS_BLOB/Ghost-Serialization-Manual-1.3.0.pdf"
}
