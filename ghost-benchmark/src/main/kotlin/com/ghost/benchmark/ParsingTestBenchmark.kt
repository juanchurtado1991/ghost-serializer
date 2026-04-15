package com.ghost.benchmark

import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.engine.TestExecutionResult

/**
 * Objective Safety Audit Engine.
 * Refactored to eliminate hardcoded safety descriptions (Rule #10: Zero Ghost Code).
 * Prints dynamic execution status for every project test.
 */
object ParsingTestBenchmark {

    fun runSafetyAudit() {
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage("com.ghost"))
            .build()

        val launcher = LauncherFactory.create()
        val summaryListener = SummaryGeneratingListener()
        
        val objectiveListener = object : TestExecutionListener {
            override fun executionStarted(testIdentifier: TestIdentifier) {
                if (testIdentifier.isTest) {
                    print("\r[PENDING] ${testIdentifier.displayName}".padEnd(60))
                }
            }

            override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
                if (testIdentifier.isTest) {
                    val status = if (testExecutionResult.status == TestExecutionResult.Status.SUCCESSFUL) 
                        "✅ [PASS]" else "❌ [FAIL]"
                    println("\r$status ${testIdentifier.displayName}".padEnd(80))
                    if (testExecutionResult.status == TestExecutionResult.Status.FAILED) {
                        testExecutionResult.throwable.ifPresent { it.printStackTrace() }
                    }
                }
            }
        }

        launcher.registerTestExecutionListeners(summaryListener, objectiveListener)
        
        println("\n" + "=".repeat(93))
        println("| OBJECTIVE SAFETY AUDIT: DYNAMIC TEST EXECUTION LOG                                        |")
        println("=".repeat(93))

        launcher.execute(request)

        val summary = summaryListener.summary
        
        println("\n" + "=".repeat(93))
        println("| AUDIT SUMMARY (H): TEST RESULTS                                                           |")
        println("=".repeat(93))
        println("Total Verifications : ${summary.testsFoundCount}")
        println("Succeeded          : ${summary.testsSucceededCount}")
        println("Failed             : ${summary.testsFailedCount}")
        println("Aborted            : ${summary.testsAbortedCount}")
        println("=".repeat(93))

        if (summary.testsFailedCount > 0) {
            println("\n[CRITICAL] Audit failed with ${summary.testsFailedCount} errors. Industrial integrity compromised.")
        } else {
            println("\n[SECURE] All security and performance guards passed. System integrity: 100%.")
        }
    }
}
