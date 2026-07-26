package com.ghost.benchmark

/**
 * Engine-relative regression detector.
 *
 * # Why relative to KSER and not absolute ops/s
 *
 * Absolute throughput (ops/s) and latency (ms) scale with the CPU, JVM build, thermal
 * state, and background load of whatever machine runs the benchmark. Comparing a fresh
 * absolute number against a number captured on a different machine (the README baseline)
 * is meaningless — a slower laptop would always "regress".
 *
 * Both Ghost and KSER, however, run in the **same JVM process, back-to-back**, on the
 * same payload. Their ratio `Ghost ÷ KSER` cancels the machine/JIT scaling factor. If a
 * change makes Ghost genuinely slower, its advantage over KSER shrinks on *every* machine;
 * if the machine is simply slow, both engines slow down together and the ratio is stable.
 *
 * This calculator therefore compares the **current Ghost-vs-KSER advantage** against the
 * **README baseline advantage** and flags a regression only when the relative advantage
 * degrades beyond [DEFAULT_TOLERANCE]. That makes the check portable across machines.
 *
 * Memory (allocated bytes per op) is deterministic and already machine-independent, but it
 * is normalized the same way (`KSER ÷ Ghost` leanness) so a single tolerance governs both.
 */
object RegressionCalculator {

    /** Relative degradation of the ratio that is tolerated before flagging a regression. */
    const val DEFAULT_TOLERANCE: Double = 0.10

    /** Raw metric kind for a category (decides how advantage is derived). */
    enum class Metric { THROUGHPUT, LATENCY }

    /**
     * One measured category with Ghost and KSER raw numbers from the current run.
     *
     * @param speed ops/s when [metric] is [Metric.THROUGHPUT]; milliseconds when [Metric.LATENCY].
     * @param memKb allocated KB/op (0.0 when not measured — memory check is then skipped).
     */
    data class Observed(
        val group: String,
        val category: String,
        val metric: Metric,
        val ghostSpeed: Double,
        val kserSpeed: Double,
        val ghostMemKb: Double,
        val kserMemKb: Double,
    )

    private data class Baseline(
        val group: String,
        val category: String,
        val metric: Metric,
        val ghostSpeed: Double,
        val kserSpeed: Double,
        val ghostMemKb: Double,
        val kserMemKb: Double,
    )

    /**
     * Baseline snapshot captured with [BenchmarkStandard] (10k global warmup, 500 local,
     * 500 synthetic sessions × 50 batched samples, Ghost+KSER first per mode, median ratio.
     * Split Gradle tasks: benchmarkSynthetic / benchmarkTwitter. Twitter stores raw ops/s.
     */
    private val BASELINES: List<Baseline> = listOf(
        // Twitter macro dataset — throughput (ops/s).
        // Refreshed 2026-07-25 after single-shot defaults (P1) + CharArray string-channel
        // key match: mean of two full-profile runs on the same machine (Kotlin 2.4.0).
        // Refreshed after in-order field prediction on the string channel:
        // Decode (String) 0.874 → 1.219 GB/s (advantage vs KSER +27.7% → +71.0%),
        // memory unchanged at 361.2 KB/op.
        Baseline(TWITTER, DECODE_STRING, Metric.THROUGHPUT, 1930.3, 1129.0, 361.2, 1337.6),
        // Refreshed after SWAR + in-order field prediction: Decode (Bytes) 0.723 → 1.011 GB/s
        // (advantage vs KSER +74.0% → +144.9%), memory unchanged at 621.2 KB/op.
        Baseline(TWITTER, DECODE_BYTES, Metric.THROUGHPUT, 1601.5, 653.8, 621.2, 4297.0),
        // Refreshed after SWAR whitespace/scanString + field prediction on StreamingGhostSource:
        // Decode (Streaming) 0.434 → 0.525 GB/s (advantage vs KSER +132.7% → +176.8%),
        // memory ~unchanged (1269.7 → 1268.7 KB/op).
        Baseline(TWITTER, DECODE_STREAMING, Metric.THROUGHPUT, 831.3, 300.9, 1268.7, 1904.9),
        // Re-baselined from a 7-run pooled mean (3 runs at the commit that set the previous
        // 4472.5/2869.6 pair, 4 runs after the decode work — the encode path is byte-identical
        // between them). The previous value came from a lucky sample: it sits above every one
        // of those 7 runs, so the gate failed on unchanged code. Encode (String) allocates the
        // most of any category (1074 KB/op) and is the noisiest, ~10% per-run stdev on Ghost
        // and on KSER alike; gating a ratio of two such means at 10% compounds that spread.
        // Observed advantage across the 7 runs: +25.4% … +50.9%, mean +38.8%.
        Baseline(TWITTER, ENCODE_STRING, Metric.THROUGHPUT, 4157.3, 2994.8, 1074.3, 972.1),
        Baseline(TWITTER, ENCODE_BYTES, Metric.THROUGHPUT, 2341.8, 1226.5, 420.2, 2206.8),
        Baseline(TWITTER, ENCODE_STREAMING, Metric.THROUGHPUT, 2325.2, 1436.1, 426.9, 455.0),

        // LIST_MEDIUM — latency advantage (ghost=1.0, kser=median session advantage)
        Baseline(LIST_MEDIUM, MODE_STRING, Metric.LATENCY, 1.0, 1.189, 157.7, 189.7),
        Baseline(LIST_MEDIUM, MODE_BYTES, Metric.LATENCY, 1.0, 2.112, 24.8, 189.7),
        Baseline(LIST_MEDIUM, MODE_STREAMING, Metric.LATENCY, 1.0, 3.648, 24.8, 189.7),

        // SYNC_FULL_LARGE — latency advantage
        Baseline(SYNC_FULL, MODE_STRING, Metric.LATENCY, 1.0, 1.169, 1173.7, 1836.6),
        Baseline(SYNC_FULL, MODE_BYTES, Metric.LATENCY, 1.0, 2.057, 213.4, 1836.6),
        Baseline(SYNC_FULL, MODE_STREAMING, Metric.LATENCY, 1.0, 3.698, 334.2, 1957.5),

        // WRITING — latency advantage
        // kserMemKb refreshed after the Kotlin 2.4.0/coroutines 1.10.2 toolchain bump: KSER's
        // own memory footprint dropped (265.0->202.7 / 326.3->263.9 / 203.5->141.2 KB), which
        // narrowed Ghost's relative advantage even though Ghost's own absolute numbers (185.4 /
        // 92.6 / 32.2 KB) were byte-for-byte unchanged — confirmed via two reproducible runs of
        // benchmarkRegressionFast. Not a Ghost regression; ghostSpeed/ghostMemKb left as-is.
        Baseline(WRITING, MODE_STRING, Metric.LATENCY, 1.0, 0.986, 185.3, 202.7),
        Baseline(WRITING, MODE_BYTES, Metric.LATENCY, 1.0, 1.412, 92.6, 263.9),
        Baseline(WRITING, MODE_STREAMING, Metric.LATENCY, 1.0, 2.560, 32.2, 141.2),
    )

    private data class Row(
        val group: String,
        val category: String,
        val baseSpeedAdv: Double,
        val curSpeedAdv: Double,
        val speedDeltaRel: Double,
        val speedRegressed: Boolean,
        val baseMemAdv: Double?,
        val curMemAdv: Double?,
        val memDeltaRel: Double?,
        val memRegressed: Boolean,
    )

    /**
     * Compares [observed] categories against the README baseline and prints a verdict.
     *
     * @param tolerance relative degradation of the Ghost/KSER advantage tolerated per metric.
     * @return `true` when no regression was detected, `false` otherwise.
     */
    fun report(observed: List<Observed>, tolerance: Double = DEFAULT_TOLERANCE): Boolean {
        val rows = observed.mapNotNull { obs -> buildRow(obs, tolerance) }

        println("\n════════════════════════════════════════════════════════════════")
        println("  📊 REGRESSION CALCULATOR — engine-relative vs README baseline")
        println("════════════════════════════════════════════════════════════════")
        println("  Method: Ghost÷KSER advantage (same-process ratio, machine-independent).")
        println("  Tolerance: ${"%.1f".format(tolerance * 100.0)}% relative degradation on the ratio.")
        println("  Speed adv = how much faster Ghost is than KSER; Mem adv = how much leaner.")

        if (rows.isEmpty()) {
            println("\n  ⚠️  No categories matched the baseline — nothing to compare.")
            println("════════════════════════════════════════════════════════════════\n")
            return true
        }

        var lastGroup = ""
        for (row in rows) {
            if (row.group != lastGroup) {
                printGroupHeader(row.group)
                lastGroup = row.group
            }
            printRow(row)
        }

        val regressions = rows.count { it.speedRegressed || it.memRegressed }
        println("\n  ────────────────────────────────────────────────────────────")
        if (regressions == 0) {
            println("  RESULT: ✅ NO REGRESSION — Ghost holds its advantage vs KSER on all categories.")
        } else {
            println("  RESULT: ❌ $regressions REGRESSION(S) DETECTED vs README baseline.")
            for (row in rows.filter { it.speedRegressed || it.memRegressed }) {
                val what = buildString {
                    if (row.speedRegressed) {
                        append("speed ${signed(row.speedDeltaRel)}")
                    }
                    if (row.memRegressed) {
                        if (isNotEmpty()) {
                            append(", ")
                        }
                        append("memory ${signed(row.memDeltaRel ?: 0.0)}")
                    }
                }
                println("     • ${row.group} / ${row.category}: $what")
            }
        }
        println("════════════════════════════════════════════════════════════════\n")
        return regressions == 0
    }

    private fun buildRow(obs: Observed, tolerance: Double): Row? {
        val baseline = BASELINES.firstOrNull {
            it.group == obs.group && it.category == obs.category
        } ?: return null

        val baseSpeedAdv = speedAdvantage(baseline.metric, baseline.ghostSpeed, baseline.kserSpeed)
        val curSpeedAdv = speedAdvantage(obs.metric, obs.ghostSpeed, obs.kserSpeed)
        val speedDeltaRel = relativeDelta(baseSpeedAdv, curSpeedAdv)
        val speedRegressed = speedDeltaRel < -tolerance

        val baseMemAdv = leannessAdvantage(baseline.ghostMemKb, baseline.kserMemKb)
        val curMemAdv = leannessAdvantage(obs.ghostMemKb, obs.kserMemKb)
        val memDeltaRel = if (baseMemAdv != null && curMemAdv != null) {
            relativeDelta(baseMemAdv, curMemAdv)
        } else {
            null
        }
        val memRegressed = memDeltaRel != null && memDeltaRel < -tolerance

        return Row(
            group = obs.group,
            category = obs.category,
            baseSpeedAdv = baseSpeedAdv,
            curSpeedAdv = curSpeedAdv,
            speedDeltaRel = speedDeltaRel,
            speedRegressed = speedRegressed,
            baseMemAdv = baseMemAdv,
            curMemAdv = curMemAdv,
            memDeltaRel = memDeltaRel,
            memRegressed = memRegressed,
        )
    }

    /** Advantage where higher = Ghost faster, regardless of raw metric direction. */
    private fun speedAdvantage(metric: Metric, ghost: Double, kser: Double): Double {
        if (ghost <= 0.0 || kser <= 0.0) {
            return 0.0
        }
        return when (metric) {
            Metric.THROUGHPUT -> ghost / kser
            Metric.LATENCY -> kser / ghost
        }
    }

    /** Leanness advantage: higher = Ghost allocates less than KSER. Null when unmeasured. */
    private fun leannessAdvantage(ghostMemKb: Double, kserMemKb: Double): Double? {
        if (ghostMemKb <= 0.0 || kserMemKb <= 0.0) {
            return null
        }
        return kserMemKb / ghostMemKb
    }

    private fun relativeDelta(baseline: Double, current: Double): Double {
        if (baseline <= 0.0) {
            return 0.0
        }
        return current / baseline - 1.0
    }

    private fun printGroupHeader(group: String) {
        println("\n  $group")
        println("  | Category           | Base adv | Cur adv | Δrel(spd) | Base mem | Cur mem | Δrel(mem) | Status |")
        println("  |--------------------|----------|---------|-----------|----------|---------|-----------|--------|")
    }

    private fun printRow(row: Row) {
        val status = when {
            row.speedRegressed && row.memRegressed -> STATUS_BOTH
            row.speedRegressed -> STATUS_SPEED
            row.memRegressed -> STATUS_MEM
            else -> STATUS_OK
        }
        println(
            "  | %-18s | %8s | %7s | %9s | %8s | %7s | %9s | %-6s |".format(
                row.category,
                advantagePct(row.baseSpeedAdv),
                advantagePct(row.curSpeedAdv),
                signed(row.speedDeltaRel),
                row.baseMemAdv?.let { advantagePct(it) } ?: NOT_AVAILABLE,
                row.curMemAdv?.let { advantagePct(it) } ?: NOT_AVAILABLE,
                row.memDeltaRel?.let { signed(it) } ?: NOT_AVAILABLE,
                status,
            )
        )
    }

    /** Renders an advantage ratio as a signed percentage, e.g. 1.267 → "+26.7%". */
    private fun advantagePct(advantage: Double): String {
        return "%+.1f%%".format((advantage - 1.0) * 100.0)
    }

    private fun signed(deltaRel: Double): String {
        return "%+.1f%%".format(deltaRel * 100.0)
    }

    const val TWITTER = "TWITTER MACRO"
    const val LIST_MEDIUM = "LIST_MEDIUM (200)"
    const val SYNC_FULL = "SYNC_FULL_LARGE (2000)"
    const val WRITING = "WRITING (1000)"

    const val DECODE_STRING = "Decode (String)"
    const val DECODE_BYTES = "Decode (Bytes)"
    const val DECODE_STREAMING = "Decode (Streaming)"
    const val ENCODE_STRING = "Encode (String)"
    const val ENCODE_BYTES = "Encode (Bytes)"
    const val ENCODE_STREAMING = "Encode (Streaming)"

    const val MODE_STRING = "String"
    const val MODE_BYTES = "Bytes"
    const val MODE_STREAMING = "Streaming"

    private const val STATUS_OK = "✅ OK"
    private const val STATUS_SPEED = "❌ SPD"
    private const val STATUS_MEM = "❌ MEM"
    private const val STATUS_BOTH = "❌ S+M"
    private const val NOT_AVAILABLE = "n/a"
}
