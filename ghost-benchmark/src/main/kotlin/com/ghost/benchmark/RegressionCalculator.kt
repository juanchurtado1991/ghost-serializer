package com.ghost.benchmark

import com.ghost.benchmark.RegressionCalculator.DECODE_STRING
import com.ghost.benchmark.RegressionCalculator.DEFAULT_TOLERANCE
import com.ghost.benchmark.RegressionCalculator.LIST_MEDIUM
import com.ghost.benchmark.RegressionCalculator.TWITTER
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant


/**
 * Engine-relative regression detector.
 *
 * ## Why relative to KSER and not absolute ops/s
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

    /** Relative degradation of the Ghost/KSER advantage ratio tolerated before flagging regression. */
    const val DEFAULT_TOLERANCE: Double = 0.10

    /**
     * Where [report] writes its machine-readable JSON snapshot, relative to the JVM's working
     * directory (`ghost-benchmark/` when launched via a Gradle `JavaExec` task). Not read by CI —
     * this is local, pre-PR best-practice tooling, not a CI gate (regression checks stay opt-in,
     * on purpose: they take 1–9 minutes and would make every PR pay for full-machine JIT warmup).
     */
    const val REPORT_JSON_PATH = "build/reports/regression/regression-report.json"

    private val REPORT_JSON = Json { prettyPrint = true }

    @Serializable
    data class ReportCategory(
        val group: String,
        val category: String,
        val baseSpeedAdvantagePct: Double,
        val currentSpeedAdvantagePct: Double,
        val speedDeltaPct: Double,
        val speedRegressed: Boolean,
        val baseMemAdvantagePct: Double?,
        val currentMemAdvantagePct: Double?,
        val memDeltaPct: Double?,
        val memRegressed: Boolean,
    )

    @Serializable
    data class Report(
        val timestamp: String,
        val tolerancePct: Double,
        val passed: Boolean,
        val categories: List<ReportCategory>,
    )

    /** Raw metric kind for a regression category (controls how advantage is derived). */
    enum class Metric { THROUGHPUT, LATENCY }

    /**
     * One measured category with Ghost and KSER raw numbers from the current run.
     *
     * @param group baseline group label (for example [TWITTER] or [LIST_MEDIUM]).
     * @param category operation label within the group (for example [DECODE_STRING]).
     * @param metric whether [ghostSpeed] and [kserSpeed] are ops/s or milliseconds.
     * @param ghostSpeed Ghost throughput (ops/s) or latency (ms), depending on [metric].
     * @param kserSpeed KSER throughput (ops/s) or latency (ms), depending on [metric].
     * @param ghostMemKb allocated KB/op for Ghost (`0.0` skips the memory check).
     * @param kserMemKb allocated KB/op for KSER (`0.0` skips the memory check).
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
     * README baseline snapshot captured with [BenchmarkStandard] under the full profile
     * (10k global warmup, 500 local warmup, 500 synthetic sessions × 50 batched samples,
     * Ghost+KSER measured back-to-back per mode, median ratio). Twitter stores raw ops/s.
     * Split Gradle tasks: `benchmarkSynthetic` / `benchmarkTwitter`.
     */
    private val BASELINES: List<Baseline> = listOf(
        Baseline(TWITTER, DECODE_STRING, Metric.THROUGHPUT, 1930.3, 1129.0, 361.2, 1337.6),

        Baseline(TWITTER, DECODE_BYTES, Metric.THROUGHPUT, 1601.5, 653.8, 621.2, 4297.0),

        Baseline(TWITTER, DECODE_STREAMING, Metric.THROUGHPUT, 831.3, 300.9, 1268.7, 1904.9),

        Baseline(TWITTER, ENCODE_STRING, Metric.THROUGHPUT, 4157.3, 2994.8, 1074.3, 972.1),
        Baseline(TWITTER, ENCODE_BYTES, Metric.THROUGHPUT, 2341.8, 1226.5, 420.2, 2206.8),
        Baseline(TWITTER, ENCODE_STREAMING, Metric.THROUGHPUT, 2325.2, 1436.1, 426.9, 455.0),

        Baseline(LIST_MEDIUM, MODE_STRING, Metric.LATENCY, 1.0, 2.590, 33.9, 113.9),
        Baseline(LIST_MEDIUM, MODE_BYTES, Metric.LATENCY, 1.0, 2.296, 24.5, 113.9),
        Baseline(LIST_MEDIUM, MODE_STREAMING, Metric.LATENCY, 1.0, 3.939, 24.5, 113.9),

        Baseline(SYNC_FULL, MODE_STRING, Metric.LATENCY, 1.0, 2.180, 240.3, 1009.3),
        Baseline(SYNC_FULL, MODE_BYTES, Metric.LATENCY, 1.0, 2.011, 158.3, 1009.3),
        Baseline(SYNC_FULL, MODE_STREAMING, Metric.LATENCY, 1.0, 3.749, 222.7, 1073.8),

        Baseline(WRITING, MODE_STRING, Metric.LATENCY, 1.0, 2.103, 92.7, 202.6),
        Baseline(WRITING, MODE_BYTES, Metric.LATENCY, 1.0, 1.736, 92.7, 263.9),
        Baseline(WRITING, MODE_STREAMING, Metric.LATENCY, 1.0, 2.635, 32.3, 141.2),
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
     * Compares [observed] categories against the README baseline and prints a verdict table.
     *
     * @param observed measured Ghost/KSER pairs grouped by workload and operation.
     * @param tolerance relative degradation of the Ghost/KSER advantage tolerated per metric.
     * @return `true` when no regression was detected; `false` when at least one category regressed.
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

        writeJsonReport(rows, tolerance, regressions == 0)
        return regressions == 0
    }

    /**
     * Durable counterpart to the console table above — a JSON snapshot at [REPORT_JSON_PATH],
     * so a regression run's result survives past the terminal scrollback (e.g. to paste into a
     * PR description, or diff against a previous local run).
     */
    private fun writeJsonReport(rows: List<Row>, tolerance: Double, passed: Boolean) {
        val report = Report(
            timestamp = Instant.now().toString(),
            tolerancePct = tolerance * 100.0,
            passed = passed,
            categories = rows.map { row ->
                ReportCategory(
                    group = row.group,
                    category = row.category,
                    baseSpeedAdvantagePct = (row.baseSpeedAdv - 1.0) * 100.0,
                    currentSpeedAdvantagePct = (row.curSpeedAdv - 1.0) * 100.0,
                    speedDeltaPct = row.speedDeltaRel * 100.0,
                    speedRegressed = row.speedRegressed,
                    baseMemAdvantagePct = row.baseMemAdv?.let { (it - 1.0) * 100.0 },
                    currentMemAdvantagePct = row.curMemAdv?.let { (it - 1.0) * 100.0 },
                    memDeltaPct = row.memDeltaRel?.let { it * 100.0 },
                    memRegressed = row.memRegressed,
                )
            },
        )
        val file = File(REPORT_JSON_PATH)
        file.parentFile?.mkdirs()
        file.writeText(REPORT_JSON.encodeToString(report))
        println("  📄 JSON report written to ${file.path}\n")
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

    /** Baseline group label for the Twitter macro dataset ([BenchmarkThroughput.TWITTER_PAYLOAD_BYTES]). */
    const val TWITTER = "TWITTER MACRO"

    /** Baseline group label for LIST_MEDIUM deserialization (200-item `ComplexResponse` list). */
    const val LIST_MEDIUM = "LIST_MEDIUM (200)"

    /** Baseline group label for SYNC_FULL_LARGE deserialization (2 000 items). */
    const val SYNC_FULL = "SYNC_FULL_LARGE (2000)"

    /** Baseline group label for WRITING serialization (1 000 items). */
    const val WRITING = "WRITING (1000)"

    /** Twitter / synthetic decode category — JSON string input. */
    const val DECODE_STRING = "Decode (String)"

    /** Twitter / synthetic decode category — UTF-8 byte array input. */
    const val DECODE_BYTES = "Decode (Bytes)"

    /** Twitter / synthetic decode category — Okio buffered source input. */
    const val DECODE_STREAMING = "Decode (Streaming)"

    /** Twitter / synthetic encode category — JSON string output. */
    const val ENCODE_STRING = "Encode (String)"

    /** Twitter / synthetic encode category — UTF-8 byte array output. */
    const val ENCODE_BYTES = "Encode (Bytes)"

    /** Twitter / synthetic encode category — Okio buffered sink output. */
    const val ENCODE_STREAMING = "Encode (Streaming)"

    /** Synthetic I/O mode — JSON string channel. */
    const val MODE_STRING = "String"

    /** Synthetic I/O mode — UTF-8 byte array channel. */
    const val MODE_BYTES = "Bytes"

    /** Synthetic I/O mode — Okio streaming channel. */
    const val MODE_STREAMING = "Streaming"

    private const val STATUS_OK = "✅ OK"
    private const val STATUS_SPEED = "❌ SPD"
    private const val STATUS_MEM = "❌ MEM"
    private const val STATUS_BOTH = "❌ S+M"
    private const val NOT_AVAILABLE = "n/a"
}
