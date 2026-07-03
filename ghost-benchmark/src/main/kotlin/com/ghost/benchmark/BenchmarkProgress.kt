package com.ghost.benchmark

/**
 * Progress logging for long-running benchmark loops.
 *
 * Prints at iteration 1, every [BenchmarkStandard.PROGRESS_INTERVAL] steps, and at completion.
 */
internal object BenchmarkProgress {

    /**
     * Runs [block] [total] times, emitting progress lines for [label].
     */
    inline fun repeatWithProgress(label: String, total: Int, block: (Int) -> Unit) {
        if (total <= 0) {
            return
        }
        repeat(total) { index ->
            val current = index + 1
            if (shouldLog(current, total)) {
                println("  [$label] $current / $total")
            }
            block(index)
        }
    }

    fun logPhase(phase: Int, totalPhases: Int, title: String) {
        println("\n--- Phase $phase/$totalPhases: $title ---")
    }

    fun logStep(label: String) {
        println("  → $label")
    }

    private fun shouldLog(current: Int, total: Int): Boolean {
        if (current == 1 || current == total) {
            return true
        }
        val interval = minOf(BenchmarkStandard.PROGRESS_INTERVAL, maxOf(1, total / 5))
        return current % interval == 0
    }
}
