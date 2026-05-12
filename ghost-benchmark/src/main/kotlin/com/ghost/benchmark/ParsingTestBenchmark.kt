package com.ghost.benchmark

import java.io.File

// ─── Data model ───────────────────────────────────────────────────────────────

data class TestResult(
    val name: String,
    val category: String,
    val passed: Boolean,
    val error: String? = null
)

/**
 * Unified test summary for all Ghost modules.
 *
 * Aggregates JUnit XML emitted by Gradle `test` / `jvmTest` tasks so the benchmark run matches
 * a full `gradle test` without embedding JUnit Platform (which broke after Vintage removal /
 * JUnit 6 classpath quirks).
 */
object ParsingTestBenchmark {

    private const val W = 93

    fun runAllTests(): List<TestResult> = readJUnitXmlResults()

    /**
     * Reads JUnit XML from each module under `build/test-results/` (same layout Gradle writes).
     */
    private fun readJUnitXmlResults(): List<TestResult> {
        val rootDir = File(System.getProperty("user.dir")).let {
            if (File(it, "settings.gradle.kts").exists()) it
            else it.parentFile
        }
        val reportRoots = listOf(
            "Serialization (JVM)" to File(rootDir, "ghost-serialization/build/test-results/jvmTest"),
            "Integration" to File(rootDir, "ghost-integration-test/build/test-results/test"),
            "Gradle Plugin" to File(rootDir, "ghost-gradle-plugin/build/test-results/test"),
            "Ktor Adapter" to File(rootDir, "ghost-ktor/build/test-results/jvmTest"),
            "Retrofit Adapter" to File(rootDir, "ghost-retrofit/build/test-results/test"),
        )
        val results = mutableListOf<TestResult>()
        for ((label, dir) in reportRoots) {
            if (!dir.exists()) {
                println(
                    "  ⚠️  No XML reports for $label at ${dir.path} — run the module's test task first."
                )
                continue
            }
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".xml") }.forEach { file ->
                parseJUnitXml(file, results)
            }
        }
        return results
    }

    private fun parseJUnitXml(file: File, results: MutableList<TestResult>) {
        val text = file.readText()
        val testcaseTag =
            Regex("""<testcase\s+([^/>]+?)(?:/>|>([\s\S]*?)</testcase>)""")
        for (match in testcaseTag.findAll(text)) {
            val attrs = match.groupValues[1]
            val body = match.groupValues[2]
            val name = attr(attrs, "name") ?: continue
            val className = attr(attrs, "classname").orEmpty()
            val failed = body.contains("<failure") || body.contains("<error")
            val displayName =
                if (className.isNotEmpty()) {
                    val short = className.substringAfterLast('.')
                    "$short › $name"
                } else {
                    name
                }
            results += TestResult(
                name = displayName,
                category = resolveCategory(className),
                passed = !failed,
                error = if (failed) Regex("""message="([^"]*)""").find(body)?.groupValues?.get(1) else null
            )
        }
    }

    private fun attr(attrs: String, key: String): String? =
        Regex("""\b$key="([^"]*)"""").find(attrs)?.groupValues?.get(1)

    /**
     * Mirrors the old in-process categorization, using the XML `classname` attribute (fully qualified).
     */
    private fun resolveCategory(className: String): String {
        val uid = className
        return when {
            uid.contains("GhostPluginTest") ||
                uid.contains("GhostPluginFunctionalTest") -> "Gradle Plugin"
            uid.contains("GhostKtorTest") -> "Ktor Adapter"
            uid.contains("GhostRetrofitTest") -> "Retrofit Adapter"
            uid.contains("integration") -> "Integration"
            uid.contains("GhostChaosTest")
                || uid.contains("GhostCrashProof")
                || uid.contains("GhostHardening")
                || uid.contains("GhostMalice")
                || uid.contains("GhostRobustness") -> "Resilience"
            uid.contains("GhostReader")
                || uid.contains("GhostWriter")
                || uid.contains("FieldTrie")
                || uid.contains("OkioTest")
                || uid.contains("PrimitiveArray") -> "Parser / Writer"
            uid.contains("GhostMemory")
                || uid.contains("GhostPerformance")
                || uid.contains("GhostStressAudit") -> "Performance"
            uid.contains("GhostPrewarm")
                || uid.contains("DeepPrewarm") -> "Prewarm"
            uid.contains("GhostGeneric")
                || uid.contains("GhostAdvancedTypes")
                || uid.contains("GhostValueClass")
                || uid.contains("GhostEnum")
                || uid.contains("GhostCoercion")
                || uid.contains("GhostCustomDiscriminator") -> "Type System"
            uid.contains("GhostFuture") -> "Future / Discovery"
            uid.contains("GhostConcurrency") -> "Concurrency"
            uid.contains("GhostException") -> "Exceptions"
            uid.contains("GhostGodObject") -> "God Object"
            uid.contains("GhostResilience") -> "Resilience"
            else -> "Core"
        }
    }

    fun printUnifiedSummaryTable(results: List<TestResult>) {
        val categories = results.groupBy { it.category }.toSortedMap()

        val totalPassed = results.count { it.passed }
        val totalFailed = results.count { !it.passed }
        val total = results.size

        println("\n" + "═".repeat(W))
        println("  UNIFIED TEST RESULTS — ALL MODULES (from Gradle JUnit XML)")
        println("═".repeat(W))
        println("  %-40s  %7s  %7s  %7s".format("Category", "Total", "✅ Pass", "❌ Fail"))
        println("  " + "─".repeat(W - 2))

        for ((category, tests) in categories) {
            val passed = tests.count { it.passed }
            val failed = tests.count { !it.passed }
            val icon = if (failed == 0) "✅" else "❌"
            println("  $icon  %-38s  %7d  %7d  %7d".format(category, tests.size, passed, failed))
        }

        println("  " + "─".repeat(W - 2))
        val totalIcon = if (totalFailed == 0) "✅" else "❌"
        println("  $totalIcon  %-38s  %7d  %7d  %7d".format("TOTAL", total, totalPassed, totalFailed))
        println("═".repeat(W))

        if (totalFailed > 0) {
            println("\n  ❌ FAILURES:")
            results.filter { !it.passed }.forEach { r ->
                println("     [${r.category}] ${r.name}" + (r.error?.let { " — $it" } ?: ""))
            }
        } else {
            println("\n  🎉 ALL $total TESTS PASSED ACROSS ${categories.size} CATEGORIES")
        }
        println()
    }
}
