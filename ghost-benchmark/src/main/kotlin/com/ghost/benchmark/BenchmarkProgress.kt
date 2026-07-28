package com.ghost.benchmark

/**
 * Progress logging for long-running benchmark loops.
 *
 * Emits a line at iteration 1, every [BenchmarkStandard.PROGRESS_INTERVAL] steps,
 * and on the final iteration so silent multi-minute runs are easier to monitor.
 */
internal object BenchmarkProgress {

    /**
     * Runs [block] [total] times, emitting progress lines for [label].
     *
     * @param label short identifier printed in brackets (for example `"Global Twitter"`).
     * @param total number of iterations; no-op when `total <= 0`.
     * @param block callback receiving the zero-based iteration index.
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

    /** Logs a numbered phase header such as `Phase 2/5: Global JIT warmup`. */
    fun logPhase(phase: Int, totalPhases: Int, title: String) {
        println("\n--- Phase $phase/$totalPhases: $title ---")
    }

    /** Logs a single indented step within the current phase. */
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
