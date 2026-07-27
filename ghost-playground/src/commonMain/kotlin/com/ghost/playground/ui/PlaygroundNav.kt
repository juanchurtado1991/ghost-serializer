package com.ghost.playground.ui

enum class PlaygroundDest {
    SpeedTest,
    Studio,
    UnderHood,
    LearnMore,
}

/** Absolute GitHub Pages paths under /ghost-serializer/. */
object PlaygroundLinks {
    const val BASE = "/ghost-serializer"
    const val WIKI_QUICK_START = "$BASE/wiki/quick-start.md"
    const val WIKI_ADVANCED = "$BASE/wiki/advanced-features.md"
    const val WIKI_ARCHITECTURE = "$BASE/wiki/architecture.md"
    const val WIKI_BENCHMARKS = "$BASE/wiki/benchmarks.md"
    const val MANUAL_MD = "$BASE/GHOST_MANUAL_EN.md"
    const val MANUAL_PDF = "$BASE/Ghost-Serialization-Manual-1.3.0.pdf"
}
