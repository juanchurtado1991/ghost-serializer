package com.ghost.benchmark

import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.engine.TestExecutionResult

// ─── Data model ───────────────────────────────────────────────────────────────

data class TestResult(
    val name: String,
    val category: String,
    val passed: Boolean,
    val error: String? = null
)

/**
 * Unified test runner for all Ghost modules.
 * Runs JVM tests dynamically, JS transpiler via subprocess,
 * and accumulates results for the final classified table.
 */
object ParsingTestBenchmark {

    private val W = 93

    // Called from GhostBenchmark.main() – returns all test results for the unified table
    fun runAllTests(): List<TestResult> {
        val allResults = mutableListOf<TestResult>()

        // Silently run tests and collect results

        // ── 1. JVM Tests (dynamic discovery) ──────────────────────────────────
        allResults += runJvmTests()

        // ── 2. JS Transpiler Tests (Node.js subprocess) ────────────────────────
        allResults += runTranspilerTests()

        return allResults
    }

    // ── JVM dynamic runner ─────────────────────────────────────────────────────

    private fun runJvmTests(): List<TestResult> {

        val request = LauncherDiscoveryRequestBuilder
            .request()
            // Exclude ghost-gradle-plugin: needs Gradle jars not on benchmark classpath
            // Exclude ghost-ktor common: needs Android/KMP runtime not on benchmark classpath
            // Both are run as dependsOn tasks; we read their XML reports instead
            .selectors(
                DiscoverySelectors.selectPackage("com.ghost.serialization"),
                DiscoverySelectors.selectPackage("com.ghost.benchmark")
            )
            .filters(
                org.junit.platform.engine.discovery.PackageNameFilter.excludePackageNames(
                    "com.ghost.serialization.ktor"
                )
            )
            .build()

        val launcher = LauncherFactory.create()
        val summaryListener = SummaryGeneratingListener()
        val results = mutableListOf<TestResult>()

        val liveListener = object : TestExecutionListener {
            override fun executionStarted(id: TestIdentifier) {
                // Silent
            }

            override fun executionFinished(id: TestIdentifier, result: TestExecutionResult) {
                if (!id.isTest) return
                val passed = result.status == TestExecutionResult.Status.SUCCESSFUL
                if (!passed) {
                    println("\n  ❌ [FAIL] ${id.displayName}")
                    result.throwable.ifPresent { t -> 
                        println("       ↳ ${t.message}")
                        t.printStackTrace(System.out)
                    }
                }
                results += TestResult(
                    name = id.displayName,
                    category = resolveCategory(id),
                    passed = passed,
                    error = if (!passed) result.throwable.map { it.message }.orElse(null) else null
                )
            }
        }

        launcher.registerTestExecutionListeners(summaryListener, liveListener)
        launcher.execute(request)

        // Also read results for modules that can't run in-process (Gradle plugin, Ktor)
        val offProcessResults = readJUnitXmlResults()
        offProcessResults.forEach { r ->
            if (!r.passed) {
                println("\n  ❌ [FAIL] ${r.name}")
                r.error?.let { println("       ↳ $it") }
            }
        }

        return results + offProcessResults
    }

    /**
     * Reads JUnit XML test reports for modules that can't run in the benchmark
     * classpath (e.g. ghost-gradle-plugin which needs Gradle jars,
     * ghost-ktor which needs KMP/Android runtime).
     */
    private fun readJUnitXmlResults(): List<TestResult> {
        val rootDir = java.io.File(System.getProperty("user.dir")).let {
            // benchmark runs from project root; confirm by checking for settings.gradle.kts
            if (java.io.File(it, "settings.gradle.kts").exists()) it
            else it.parentFile
        }
        val reportDirs = mapOf(
            "Gradle Plugin"    to java.io.File(rootDir, "ghost-gradle-plugin/build/test-results/test"),
            "Ktor Adapter"     to java.io.File(rootDir, "ghost-ktor/build/test-results/jvmTest"),
            "Retrofit Adapter" to java.io.File(rootDir, "ghost-retrofit/build/test-results/test")
        )
        val results = mutableListOf<TestResult>()
        for ((category, dir) in reportDirs) {
            if (!dir.exists()) {
                println("  ⚠️  No XML reports found for $category at ${dir.path}")
                continue
            }
            dir.walkTopDown().filter { it.name.endsWith(".xml") }.forEach { file ->
                parseJUnitXml(file, category, results)
            }
        }
        return results
    }

    private fun parseJUnitXml(
        file: java.io.File,
        category: String,
        results: MutableList<TestResult>
    ) {
        val text = file.readText()
        // Simple regex-based parse — avoids adding an XML dependency
        val testcaseRegex = Regex("""<testcase[^>]*name="([^"]+)"[^>]*(?:/>|>([\s\S]*?)</testcase>)""")
        for (match in testcaseRegex.findAll(text)) {
            val name = match.groupValues[1]
            val body = match.groupValues[2]
            val failed = body.contains("<failure") || body.contains("<error")
            results += TestResult(
                name = name,
                category = category,
                passed = !failed,
                error = if (failed) Regex("""message="([^"]*)""").find(body)?.groupValues?.get(1) else null
            )
        }
    }

    /**
     * Derives a human-readable category from the test class/package name.
     */
    private fun resolveCategory(id: TestIdentifier): String {
        val uid = id.uniqueId
        return when {
            uid.contains("GhostPluginTest")               -> "Gradle Plugin"
            uid.contains("GhostKtorTest")                  -> "Ktor Adapter"
            uid.contains("GhostRetrofitTest")              -> "Retrofit Adapter"
            uid.contains("integration")                    -> "Integration"
            uid.contains("GhostChaosTest")
                || uid.contains("GhostCrashProof")
                || uid.contains("GhostHardening")
                || uid.contains("GhostMalice")
                || uid.contains("GhostRobustness")        -> "Resilience"
            uid.contains("GhostReader")
                || uid.contains("GhostWriter")
                || uid.contains("FieldTrie")
                || uid.contains("OkioTest")
                || uid.contains("PrimitiveArray")          -> "Parser / Writer"
            uid.contains("GhostMemory")
                || uid.contains("GhostPerformance")
                || uid.contains("GhostStressAudit")        -> "Performance"
            uid.contains("GhostPrewarm")
                || uid.contains("DeepPrewarm")             -> "Prewarm"
            uid.contains("GhostGeneric")
                || uid.contains("GhostAdvancedTypes")
                || uid.contains("GhostValueClass")
                || uid.contains("GhostEnum")
                || uid.contains("GhostCoercion")
                || uid.contains("GhostCustomDiscriminator") -> "Type System"
            uid.contains("GhostFuture")                    -> "Future / Discovery"
            uid.contains("GhostConcurrency")               -> "Concurrency"
            uid.contains("GhostException")                 -> "Exceptions"
            uid.contains("GhostGodObject")                 -> "God Object"
            uid.contains("GhostResilience")                -> "Resilience"
            else                                           -> "Core"
        }
    }

    // ── Node.js transpiler tests ───────────────────────────────────────────────

    private fun runTranspilerTests(): List<TestResult> {
        val scriptPath = resolveTranspilerTestPath()
        if (scriptPath == null) {
            println("  ⚠️  test-transpiler.js not found — skipping")
            return emptyList()
        }

        return try {
            val process = ProcessBuilder("node", scriptPath)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                println("\n  ❌ Transpiler tests FAILED (exit code $exitCode)")
                output.lines().forEach { line ->
                    if (line.isNotBlank()) println("  $line")
                }
            }

            if (exitCode == 0) {
                // Parse passed/failed counts from transpiler output
                val passedMatch = Regex("""(\d+) passed""").find(output)
                val failedMatch = Regex("""(\d+) failed""").find(output)
                val passed = passedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val failed = failedMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                val results = mutableListOf<TestResult>()
                repeat(passed) { i -> results += TestResult("Transpiler assertion #${i+1}", "Transpiler (JS)", true) }
                repeat(failed) { i -> results += TestResult("Transpiler FAILED assertion #${i+1}", "Transpiler (JS)", false) }
                results
            } else {
                println("  ❌ Transpiler tests FAILED (exit code $exitCode)")
                listOf(TestResult("Transpiler suite", "Transpiler (JS)", false, "Process exited with code $exitCode"))
            }
        } catch (e: Exception) {
            println("  ⚠️  Could not run Node.js: ${e.message}")
            emptyList()
        }
    }

    private fun resolveTranspilerTestPath(): String? {
        // Try to locate test-transpiler.js relative to the project root
        val candidates = listOf(
            System.getProperty("user.dir") + "/../ghost-serialization/npm-tools/test-transpiler.js",
            System.getProperty("user.dir") + "/ghost-serialization/npm-tools/test-transpiler.js",
        )
        return candidates.map { java.io.File(it).canonicalFile }
            .firstOrNull { it.exists() }
            ?.absolutePath
    }

    // ── Unified summary table ──────────────────────────────────────────────────

    fun printUnifiedSummaryTable(results: List<TestResult>) {
        val categories = results.groupBy { it.category }.toSortedMap()

        val totalPassed = results.count { it.passed }
        val totalFailed = results.count { !it.passed }
        val total = results.size

        println("\n" + "═".repeat(W))
        println("  UNIFIED TEST RESULTS — ALL MODULES")
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
