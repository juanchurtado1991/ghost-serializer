@file:OptIn(InternalGhostApi::class)

package com.ghost.benchmark

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.integration.model.ExternalColor
import com.ghost.serialization.integration.model.ExternalColorSerializer
import com.ghost.serialization.integration.model.ExternalDate
import com.ghost.serialization.integration.model.ExternalDateSerializer
import com.sun.management.ThreadMXBean
import kotlin.reflect.KClass

/**
 * One-time Ghost registry + prewarm shared by every benchmark JVM.
 */
internal object BenchmarkEnvironment {

    fun init(): ThreadMXBean? {
        Ghost.addRegistry(manualRegistry)
        Ghost.prewarm()
        return initializePlatformDiagnostics()
    }

    fun printConfigHeader(suite: BenchmarkSuite) {
        println("\n--- GHOST BENCHMARK: ${suite.cliName.uppercase()} ---")
        println(
            "  Profile: ${BenchmarkStandard.profileName} — global warmup=${BenchmarkStandard.WARMUP_ITERATIONS}, " +
                "local warmup=${BenchmarkStandard.LOCAL_WARMUP_ITERATIONS}, " +
                "synthetic sessions=${BenchmarkStandard.SYNTHETIC_SESSIONS}, " +
                "measurement runs=${BenchmarkStandard.MEASUREMENT_RUNS}, " +
                "regression ±${"%.0f".format(BenchmarkStandard.REGRESSION_TOLERANCE * 100.0)}%"
        )
    }

    private val manualRegistry = object : GhostRegistry {
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return when (clazz) {
                ExternalColor::class -> ExternalColorSerializer as GhostSerializer<T>
                ExternalDate::class -> ExternalDateSerializer as GhostSerializer<T>
                else -> null
            }
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(
                ExternalColor::class to ExternalColorSerializer,
                ExternalDate::class to ExternalDateSerializer
            )
        }
    }
}
