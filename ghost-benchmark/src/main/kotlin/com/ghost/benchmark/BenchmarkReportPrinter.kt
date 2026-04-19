package com.ghost.benchmark

import com.ghost.serialization.integration.model.BenchmarkMetrics
import com.ghost.serialization.integration.model.GhostMetrics
import com.ghost.serialization.integration.model.StressMetrics

private const val W = 100

internal fun printBenchmarkReport(
    count: Int,
    payloadMb: String,
    metrics: GhostMetrics
) {
    val line = { println("─".repeat(W)) }
    val thick = { println("═".repeat(W)) }
    val title = { t: String -> thick(); println("  $t"); thick() }

    println()
    title("GHOST SERIALIZATION ENGINE — PERFORMANCE AUDIT REPORT")

    printEnvironment(count, payloadMb)

    printDeserialization(
        metrics.steady,
        count,
        payloadMb,
        line
    )
    printSerialization(
        metrics.serialization,
        count,
        line
    )

    printMemory(metrics.steady, line)

    printReliability(
        metrics.cold,
        metrics.failure,
        line
    )
    printStress(
        metrics.stress,
        line
    )

    printArchMatrix(line)

    printTradeOffs(
        metrics.steady,
        thick
    )
}

private fun printEnvironment(count: Int, payloadMb: String) {
    println("\n  TEST ENVIRONMENT")
    println("  ├─ Runtime     : ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
    println("  ├─ OS          : ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println("  ├─ Payload     : %,d objects | ~$payloadMb MB compact JSON | No whitespace".format(count))
    println("  ├─ Warmup      : 50 JIT iterations before measurement")
    println("  ├─ Heap Method : ThreadMXBean.getThreadAllocatedBytes() (per-thread)")
    println("  └─ Methodology : Single-shot post-JIT steady state, fair cold boot")
}

private fun delta(ghost: Long, other: Long): String {
    if (other == 0L || ghost <= 0L) return "N/A"
    val speedup = other.toDouble() / ghost.toDouble()
    val improvement = ((speedup - 1.0) * 100).toInt()
    return when {
        improvement > 0 -> "▲ $improvement%% faster (%.2fx speedup)".format(speedup)
        improvement < 0 -> "▼ ${-improvement}%% slower"
        else -> "≈ tied"
    }
}

private fun badge(ghost: Long, others: List<Long>, lowerBetter: Boolean): String {
    val best = if (lowerBetter) others.min() else others.max()
    val isBest = if (lowerBetter) ghost <= best else ghost >= best
    return if (isBest) "★ GHOST" else {
        val names = listOf("GSON", "Moshi", "K-Ser")
        val idx = others.indexOf(best)
        names.getOrElse(idx) { "—" }
    }
}

private fun printDeserialization(m: BenchmarkMetrics, count: Int, payloadMb: String, line: () -> Unit) {
    val ops = { ns: Long -> (count.toLong() * 1_000_000_000L) / ns.coerceAtLeast(1) }
    println("\n  1. DESERIALIZATION PERFORMANCE (JSON → Objects)")
    println("     Scenario: Parse %,d user objects from ~$payloadMb MB JSON".format(count))
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Latency (lower = better)", m.gson.nanos / 1_000_000, m.moshi.nanos / 1_000_000, m.kser.nanos / 1_000_000, m.ghost.nanos / 1_000_000,
        badge(m.ghost.nanos, listOf(m.gson.nanos, m.moshi.nanos, m.kser.nanos), true)))
    println("  %-28s %10d %10d %10d %10d   %s".format(
        "Throughput (ops/s, higher)", ops(m.gson.nanos), ops(m.moshi.nanos), ops(m.kser.nanos), ops(m.ghost.nanos),
        badge(ops(m.ghost.nanos), listOf(ops(m.gson.nanos), ops(m.moshi.nanos), ops(m.kser.nanos)), false)))
    line()
    println("  Ghost vs GSON     : ${delta(m.ghost.nanos, m.gson.nanos)}")
    println("  Ghost vs Moshi    : ${delta(m.ghost.nanos, m.moshi.nanos)}")
    println("  Ghost vs K-Ser    : ${delta(m.ghost.nanos, m.kser.nanos)}")
}

private fun printSerialization(ser: BenchmarkMetrics, count: Int, line: () -> Unit) {
    println("\n  2. SERIALIZATION PERFORMANCE (Objects → JSON)")
    println("     Scenario: Serialize %,d user objects to JSON string".format(count))
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Latency (lower = better)", ser.gson.nanos / 1_000_000, ser.moshi.nanos / 1_000_000, ser.kser.nanos / 1_000_000, ser.ghost.nanos / 1_000_000,
        badge(ser.ghost.nanos, listOf(ser.gson.nanos, ser.moshi.nanos, ser.kser.nanos), true)))
    line()
    println("  Ghost vs GSON     : ${delta(ser.ghost.nanos, ser.gson.nanos)}")
    println("  Ghost vs Moshi    : ${delta(ser.ghost.nanos, ser.moshi.nanos)}")
    println("  Ghost vs K-Ser    : ${delta(ser.ghost.nanos, ser.kser.nanos)}")
}

private fun printMemory(m: BenchmarkMetrics, line: () -> Unit) {
    val pct = { base: Long, target: Long ->
        if (base > 0) ((base - target).toDouble() / base * 100).toInt() else 0
    }
    println("\n  3. MEMORY EFFICIENCY (Heap allocated per deserialization call)")
    println("     Measured via ThreadMXBean — includes all intermediate objects")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %8d KB %8d KB %8d KB %8d KB   %s".format(
        "Heap Allocated (lower=better)", m.gson.allocBytes / 1024, m.moshi.allocBytes / 1024, m.kser.allocBytes / 1024, m.ghost.allocBytes / 1024,
        badge(m.ghost.allocBytes, listOf(m.gson.allocBytes, m.moshi.allocBytes, m.kser.allocBytes), true)))
    line()
    println("  Ghost vs GSON     : %d%% less heap → %,d KB saved per call".format(
        pct(m.gson.allocBytes, m.ghost.allocBytes), (m.gson.allocBytes - m.ghost.allocBytes) / 1024))
    println("  Ghost vs Moshi    : %d%% less heap → %,d KB saved per call".format(
        pct(m.moshi.allocBytes, m.ghost.allocBytes), (m.moshi.allocBytes - m.ghost.allocBytes) / 1024))
    println("  Ghost vs K-Ser    : %d%% less heap → %,d KB saved per call".format(
        pct(m.kser.allocBytes, m.ghost.allocBytes), (m.kser.allocBytes - m.ghost.allocBytes) / 1024))
    println("  Avg GC Reduction  : ~%d%% less garbage collection pressure".format(
        (pct(m.gson.allocBytes, m.ghost.allocBytes) + pct(m.moshi.allocBytes, m.ghost.allocBytes) + pct(m.kser.allocBytes, m.ghost.allocBytes)) / 3))
}

private fun printReliability(cold: BenchmarkMetrics, f: BenchmarkMetrics, line: () -> Unit) {
    println("\n  4. RELIABILITY & EDGE CASES")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Cold Start (1st call)", cold.gson.nanos / 1_000_000, cold.moshi.nanos / 1_000_000, cold.kser.nanos / 1_000_000, cold.ghost.nanos / 1_000_000,
        badge(cold.ghost.nanos, listOf(cold.gson.nanos, cold.moshi.nanos, cold.kser.nanos), true)))
    println("  %-28s %8dns %8dns %8dns %8dns   %s".format(
        "Fail Detection (avg of 100)", f.gson.nanos, f.moshi.nanos, f.kser.nanos, f.ghost.nanos,
        badge(f.ghost.nanos, listOf(f.gson.nanos, f.moshi.nanos, f.kser.nanos), true)))
    line()
    val bestCold = minOf(cold.gson.nanos, cold.moshi.nanos, cold.kser.nanos)
    println("  Cold Start    : ${delta(cold.ghost.nanos, bestCold)} than next fastest")
    val bestFail = minOf(f.gson.nanos, f.moshi.nanos, f.kser.nanos)
    println("  Fail Detect   : ${delta(f.ghost.nanos, bestFail)} than next fastest")
}

private fun printStress(s: StressMetrics, line: () -> Unit) {
    println("\n  5. STRESS TEST (Recursive tree depth=20, sealed class nesting)")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Deep Nesting Parse", s.nesting.gson.nanos / 1_000_000, s.nesting.moshi.nanos / 1_000_000, s.nesting.kser.nanos / 1_000_000, s.nesting.ghost.nanos / 1_000_000,
        badge(s.nesting.ghost.nanos, listOf(s.nesting.gson.nanos, s.nesting.moshi.nanos, s.nesting.kser.nanos), true)))
    line()
}

private fun printArchMatrix(line: () -> Unit) {
    println("\n  6. ARCHITECTURE DECISION MATRIX")
    line()
    println("  %-28s %-14s %-14s %-14s %-14s".format("Capability", "GSON", "Moshi", "K-Ser", "Ghost"))
    line()
    val row = { cap: String, g: String, m: String, k: String, gh: String ->
        println("  %-28s %-14s %-14s %-14s %-14s".format(cap, g, m, k, gh))
    }
    row("Kotlin Multiplatform",     "No",       "No",       "Yes",      "Yes")
    row("Android / iOS / Desktop",  "Android",  "Android",  "All",      "All")
    row("Sealed Class Support",     "Manual",   "Manual",   "Native",   "Native")
    row("Value/Inline Classes",     "No",       "No",       "Native",   "Native")
    row("Default Value Handling",   "No",       "No",       "Yes",      "Yes")
    row("Enum Serialization",       "Name only","Name only","Flexible", "Flexible")
    row("Runtime Reflection",       "Heavy",    "Moderate", "None",     "None")
    row("R8/ProGuard Configuration","Complex",  "Required", "Minimal",  "None")
    row("Code Generation Strategy", "None",     "KSP/kapt", "Compiler", "KSP")
    row("I/O Model",               "Streaming", "Buffered", "Streaming","Buffered")
    line()
}

private fun printTradeOffs(m: BenchmarkMetrics, thick: () -> Unit) {
    println("\n  7. TRADE-OFFS & LIMITATIONS (Honest Assessment)")
    thick()

    println("  [ADVANTAGE] Zero-Copy ByteArray Parser")
    println("      Ghost parses directly from a contiguous ByteArray using raw")
    println("      index access (data[pos]). This eliminates all Okio/Stream")
    println("      abstraction overhead and enables cache-friendly sequential reads.")

    println("\n  [TRADE-OFF] Buffered I/O Model")
    println("      Ghost loads the full JSON payload into memory before parsing.")
    println("      This is optimal for API responses (<50 MB) but not ideal for")
    println("      streaming very large files. Use deserialize(BufferedSource)")
    println("      for payloads that exceed available heap.")

    println("\n  [INFO] Build Impact")
    println("      KSP generates 1 serializer class per @GhostSerialization model.")
    println("      Zero runtime reflection. Zero ProGuard/R8 rules required.")
    thick()
}
