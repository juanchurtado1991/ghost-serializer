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
 * Dynamic JVM Test Runner.
 * Executes JVM core tests at runtime to verify parsing stability.
 */
object ParsingTestBenchmark {

    fun runSafetyAudit() {
        val request: LauncherDiscoveryRequest = LauncherDiscoveryRequestBuilder
            .request()
            .selectors(
                DiscoverySelectors
                    .selectPackage("com.ghost")
            )
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
                    val status = if (testExecutionResult.status == TestExecutionResult.Status.SUCCESSFUL) {
                        "✅ [PASS]"
                    } else {
                        "❌ [FAIL]"
                    }

                    println("\r$status ${testIdentifier.displayName}".padEnd(80))
                    if (testExecutionResult.status == TestExecutionResult.Status.FAILED) {
                        testExecutionResult.throwable.ifPresent { it.printStackTrace() }
                    }
                }
            }
        }

        launcher.registerTestExecutionListeners(summaryListener, objectiveListener)
        
        println("\n" + "=".repeat(93))
        println("| DYNAMIC JVM TEST EXECUTION LOG                                                            |")
        println("=".repeat(93))

        launcher.execute(request)

        val summary = summaryListener.summary
        
        println("\n" + "=".repeat(93))
        println("| TEST SUMMARY                                                                              |")
        println("=".repeat(93))
        println("Total Verifications: ${summary.testsFoundCount}")
        println("Succeeded          : ${summary.testsSucceededCount}")
        println("Failed             : ${summary.testsFailedCount}")
        println("Aborted            : ${summary.testsAbortedCount}")
        println("=".repeat(93))

        if (summary.testsFailedCount > 0) {
            println("\n[FAILED] Test suite execution failed with ${summary.testsFailedCount} errors.")
        } else {
            println("\n[SUCCESS] All JVM core tests passed.")
        }
    }
}
