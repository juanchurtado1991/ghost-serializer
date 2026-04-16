package com.ghost.benchmark

private const val W = 100

internal fun printBenchmarkReport(
    cold: BenchmarkMetrics,
    m: BenchmarkMetrics,
    ser: BenchmarkMetrics,
    s: StressMetrics,
    f: BenchmarkMetrics
) {
    val line = { println("─".repeat(W)) }
    val thick = { println("═".repeat(W)) }
    val title = { t: String -> thick(); println("  $t"); thick() }

    println()
    title("GHOST SERIALIZATION ENGINE — PERFORMANCE AUDIT REPORT")

    printEnvironment()
    printDeserialization(m, line)
    printSerialization(ser, line)
    printMemory(m, line)
    printReliability(cold, f, line)
    printStress(s, line)
    printArchMatrix(line)
    printTradeOffs(m, thick)
}

private fun printEnvironment() {
    println("\n  TEST ENVIRONMENT")
    println("  ├─ Runtime     : ${System.getProperty("java.vm.name")} ${System.getProperty("java.vm.version")}")
    println("  ├─ OS          : ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println("  ├─ Payload     : 60,000 objects | ~4.5 MB compact JSON | No whitespace")
    println("  ├─ Warmup      : 15 JIT iterations before measurement")
    println("  ├─ Heap Method : ThreadMXBean.getThreadAllocatedBytes() (per-thread)")
    println("  └─ Methodology : Single-shot post-JIT steady state, fair cold boot")
}

private fun delta(ghost: Long, other: Long): String {
    if (other == 0L) return "N/A"
    val pct = ((other.toDouble() - ghost) / other * 100).toInt()
    return when {
        pct > 0 -> "▲ $pct% faster"
        pct < 0 -> "▼ ${-pct}% slower"
        else -> "≈ tied"
    }
}

private fun badge(ghost: Long, others: List<Long>, lowerBetter: Boolean): String {
    val best = if (lowerBetter) others.min() else others.max()
    val isBest = if (lowerBetter) ghost <= best else ghost >= best
    return if (isBest) "★ GHOST" else {
        val names = listOf("GSON", "Moshi", "K-Ser")
        val idx = if (lowerBetter) others.indexOf(others.min()) else others.indexOf(others.max())
        names.getOrElse(idx) { "—" }
    }
}

private fun printDeserialization(m: BenchmarkMetrics, line: () -> Unit) {
    val ops = { ms: Long -> 60_000_000 / ms.coerceAtLeast(1) }
    println("\n  1. DESERIALIZATION PERFORMANCE (JSON → Objects)")
    println("     Scenario: Parse 60,000 user objects from ~4.5 MB JSON")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Latency (lower = better)", m.gson.ms, m.moshi.ms, m.kser.ms, m.ghost.ms,
        badge(m.ghost.ms, listOf(m.gson.ms, m.moshi.ms, m.kser.ms), true)))
    println("  %-28s %10d %10d %10d %10d   %s".format(
        "Throughput (ops/s, higher)", ops(m.gson.ms), ops(m.moshi.ms), ops(m.kser.ms), ops(m.ghost.ms),
        badge(ops(m.ghost.ms), listOf(ops(m.gson.ms), ops(m.moshi.ms), ops(m.kser.ms)), false)))
    line()
    println("  Ghost vs GSON     : ${delta(m.ghost.ms, m.gson.ms)}")
    println("  Ghost vs Moshi    : ${delta(m.ghost.ms, m.moshi.ms)}")
    println("  Ghost vs K-Ser    : ${delta(m.ghost.ms, m.kser.ms)}")
}

private fun printSerialization(ser: BenchmarkMetrics, line: () -> Unit) {
    println("\n  2. SERIALIZATION PERFORMANCE (Objects → JSON)")
    println("     Scenario: Serialize 60,000 user objects to JSON string")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Latency (lower = better)", ser.gson.ms, ser.moshi.ms, ser.kser.ms, ser.ghost.ms,
        badge(ser.ghost.ms, listOf(ser.gson.ms, ser.moshi.ms, ser.kser.ms), true)))
    line()
    println("  Ghost vs GSON     : ${delta(ser.ghost.ms, ser.gson.ms)}")
    println("  Ghost vs Moshi    : ${delta(ser.ghost.ms, ser.moshi.ms)}")
    println("  Ghost vs K-Ser    : ${delta(ser.ghost.ms, ser.kser.ms)}")
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
        "Heap Allocated (lower=better)", m.gson.alloc, m.moshi.alloc, m.kser.alloc, m.ghost.alloc,
        badge(m.ghost.alloc, listOf(m.gson.alloc, m.moshi.alloc, m.kser.alloc), true)))
    line()
    println("  Ghost vs GSON     : %d%% less heap → %,d KB saved per call".format(
        pct(m.gson.alloc, m.ghost.alloc), m.gson.alloc - m.ghost.alloc))
    println("  Ghost vs Moshi    : %d%% less heap → %,d KB saved per call".format(
        pct(m.moshi.alloc, m.ghost.alloc), m.moshi.alloc - m.ghost.alloc))
    println("  Ghost vs K-Ser    : %d%% less heap → %,d KB saved per call".format(
        pct(m.kser.alloc, m.ghost.alloc), m.kser.alloc - m.ghost.alloc))
    println("  Avg GC Reduction  : ~%d%% less garbage collection pressure".format(
        (pct(m.gson.alloc, m.ghost.alloc) + pct(m.moshi.alloc, m.ghost.alloc) + pct(m.kser.alloc, m.ghost.alloc)) / 3))
}

private fun printReliability(cold: BenchmarkMetrics, f: BenchmarkMetrics, line: () -> Unit) {
    println("\n  4. RELIABILITY & EDGE CASES")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Cold Start (1st call)", cold.gson.ms, cold.moshi.ms, cold.kser.ms, cold.ghost.ms,
        badge(cold.ghost.ms, listOf(cold.gson.ms, cold.moshi.ms, cold.kser.ms), true)))
    println("  %-28s %8dns %8dns %8dns %8dns   %s".format(
        "Fail Detection (avg of 100)", f.gson.ms, f.moshi.ms, f.kser.ms, f.ghost.ms,
        badge(f.ghost.ms, listOf(f.gson.ms, f.moshi.ms, f.kser.ms), true)))
    line()
    val bestCold = minOf(cold.gson.ms, cold.moshi.ms, cold.kser.ms)
    println("  Cold Start    : ${delta(cold.ghost.ms, bestCold)} than next fastest")
    val bestFail = minOf(f.gson.ms, f.moshi.ms, f.kser.ms)
    println("  Fail Detect   : ${delta(f.ghost.ms, bestFail)} than next fastest")
}

private fun printStress(s: StressMetrics, line: () -> Unit) {
    println("\n  5. STRESS TEST (Recursive tree depth=20, sealed class nesting)")
    line()
    println("  %-28s %10s %10s %10s %10s   %-10s".format(
        "", "GSON", "Moshi", "K-Ser", "Ghost", "Winner"))
    line()
    println("  %-28s %9dms %9dms %9dms %9dms   %s".format(
        "Deep Nesting Parse", s.nesting.gson.ms, s.nesting.moshi.ms, s.nesting.kser.ms, s.nesting.ghost.ms,
        badge(s.nesting.ghost.ms, listOf(s.nesting.gson.ms, s.nesting.moshi.ms, s.nesting.kser.ms), true)))
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
