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
import com.ghost.serialization.proto.wkt.ProtoAny
import com.ghost.serialization.proto.wkt.ProtoAnySerializer
import com.ghost.serialization.proto.wkt.ProtoBoolValue
import com.ghost.serialization.proto.wkt.ProtoBoolValueSerializer
import com.ghost.serialization.proto.wkt.ProtoBytesValue
import com.ghost.serialization.proto.wkt.ProtoBytesValueSerializer
import com.ghost.serialization.proto.wkt.ProtoDoubleValue
import com.ghost.serialization.proto.wkt.ProtoDoubleValueSerializer
import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoDurationSerializer
import com.ghost.serialization.proto.wkt.ProtoFloatValue
import com.ghost.serialization.proto.wkt.ProtoFloatValueSerializer
import com.ghost.serialization.proto.wkt.ProtoInt32Value
import com.ghost.serialization.proto.wkt.ProtoInt32ValueSerializer
import com.ghost.serialization.proto.wkt.ProtoInt64Value
import com.ghost.serialization.proto.wkt.ProtoInt64ValueSerializer
import com.ghost.serialization.proto.wkt.ProtoStringValue
import com.ghost.serialization.proto.wkt.ProtoStringValueSerializer
import com.ghost.serialization.proto.wkt.ProtoTimestamp
import com.ghost.serialization.proto.wkt.ProtoTimestampSerializer
import com.ghost.serialization.proto.wkt.ProtoUInt32Value
import com.ghost.serialization.proto.wkt.ProtoUInt32ValueSerializer
import com.ghost.serialization.proto.wkt.ProtoUInt64Value
import com.ghost.serialization.proto.wkt.ProtoUInt64ValueSerializer
import com.ghost.serialization.proto.wkt.ProtoValue
import com.ghost.serialization.proto.wkt.ProtoValueSerializer
import com.sun.management.ThreadMXBean
import kotlin.reflect.KClass

/**
 * One-time Ghost registry wiring and JVM prewarm shared by every benchmark process.
 *
 * Registers manual serializers for integration-test types (external coders, protobuf WKTs),
 * calls [Ghost.prewarm], and enables thread allocation tracking.
 */
internal object BenchmarkEnvironment {

    /**
     * Initializes Ghost and platform diagnostics.
     *
     * @return a [ThreadMXBean] with allocation tracking enabled, or `null` when unsupported
     *   (callers should exit the JVM with a non-zero status).
     */
    fun init(): ThreadMXBean? {
        Ghost.addRegistry(manualRegistry)
        Ghost.prewarm()
        return initializePlatformDiagnostics()
    }

    /** Prints the suite banner and active [BenchmarkProfile] iteration counts. */
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
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            return when (clazz) {
                ExternalColor::class -> ExternalColorSerializer as GhostSerializer<T>
                ExternalDate::class -> ExternalDateSerializer as GhostSerializer<T>
                ProtoDuration::class -> ProtoDurationSerializer as GhostSerializer<T>
                ProtoTimestamp::class -> ProtoTimestampSerializer as GhostSerializer<T>
                ProtoAny::class -> ProtoAnySerializer as GhostSerializer<T>
                ProtoValue::class -> ProtoValueSerializer as GhostSerializer<T>
                ProtoBoolValue::class -> ProtoBoolValueSerializer as GhostSerializer<T>
                ProtoStringValue::class -> ProtoStringValueSerializer as GhostSerializer<T>
                ProtoBytesValue::class -> ProtoBytesValueSerializer as GhostSerializer<T>
                ProtoDoubleValue::class -> ProtoDoubleValueSerializer as GhostSerializer<T>
                ProtoFloatValue::class -> ProtoFloatValueSerializer as GhostSerializer<T>
                ProtoInt32Value::class -> ProtoInt32ValueSerializer as GhostSerializer<T>
                ProtoInt64Value::class -> ProtoInt64ValueSerializer as GhostSerializer<T>
                ProtoUInt32Value::class -> ProtoUInt32ValueSerializer as GhostSerializer<T>
                ProtoUInt64Value::class -> ProtoUInt64ValueSerializer as GhostSerializer<T>
                else -> null
            }
        }

        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(
                ExternalColor::class to ExternalColorSerializer,
                ExternalDate::class to ExternalDateSerializer,
                ProtoDuration::class to ProtoDurationSerializer,
                ProtoTimestamp::class to ProtoTimestampSerializer,
                ProtoAny::class to ProtoAnySerializer,
                ProtoValue::class to ProtoValueSerializer,
                ProtoBoolValue::class to ProtoBoolValueSerializer,
                ProtoStringValue::class to ProtoStringValueSerializer,
                ProtoBytesValue::class to ProtoBytesValueSerializer,
                ProtoDoubleValue::class to ProtoDoubleValueSerializer,
                ProtoFloatValue::class to ProtoFloatValueSerializer,
                ProtoInt32Value::class to ProtoInt32ValueSerializer,
                ProtoInt64Value::class to ProtoInt64ValueSerializer,
                ProtoUInt32Value::class to ProtoUInt32ValueSerializer,
                ProtoUInt64Value::class to ProtoUInt64ValueSerializer
            )
        }
    }
}
