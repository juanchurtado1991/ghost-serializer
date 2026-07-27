package com.ghost.playground.ui

enum class PlaygroundDest {
    SpeedTest,
    Studio,
    UnderHood,
    LearnMore,
}

/** Absolute GitHub URLs for docs opened from the playground (Wasm + JVM). */
object PlaygroundLinks {
    private const val REPO = "https://github.com/juanchurtado1991/ghost-serializer"
    private const val DOCS_BLOB = "$REPO/blob/main/docs"

    const val WIKI_QUICK_START = "$DOCS_BLOB/wiki/quick-start.md"
    const val WIKI_ADVANCED = "$DOCS_BLOB/wiki/advanced-features.md"
    const val WIKI_ARCHITECTURE = "$DOCS_BLOB/wiki/architecture.md"
    const val WIKI_BENCHMARKS = "$DOCS_BLOB/wiki/benchmarks.md"
    const val MANUAL_MD = "$DOCS_BLOB/GHOST_MANUAL_EN.md"
    const val MANUAL_PDF = "$DOCS_BLOB/Ghost-Serialization-Manual-1.3.0.pdf"
}
