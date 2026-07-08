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
import com.ghost.protobuf.wkt.ProtoDuration
import com.ghost.protobuf.wkt.ProtoDurationSerializer
import com.ghost.protobuf.wkt.ProtoTimestamp
import com.ghost.protobuf.wkt.ProtoTimestampSerializer
import com.ghost.protobuf.wkt.ProtoAny
import com.ghost.protobuf.wkt.ProtoAnySerializer
import com.ghost.protobuf.wkt.ProtoValue
import com.ghost.protobuf.wkt.ProtoValueSerializer
import com.ghost.protobuf.wkt.ProtoBoolValue
import com.ghost.protobuf.wkt.ProtoBoolValueSerializer
import com.ghost.protobuf.wkt.ProtoStringValue
import com.ghost.protobuf.wkt.ProtoStringValueSerializer
import com.ghost.protobuf.wkt.ProtoBytesValue
import com.ghost.protobuf.wkt.ProtoBytesValueSerializer
import com.ghost.protobuf.wkt.ProtoDoubleValue
import com.ghost.protobuf.wkt.ProtoDoubleValueSerializer
import com.ghost.protobuf.wkt.ProtoFloatValue
import com.ghost.protobuf.wkt.ProtoFloatValueSerializer
import com.ghost.protobuf.wkt.ProtoInt32Value
import com.ghost.protobuf.wkt.ProtoInt32ValueSerializer
import com.ghost.protobuf.wkt.ProtoInt64Value
import com.ghost.protobuf.wkt.ProtoInt64ValueSerializer
import com.ghost.protobuf.wkt.ProtoUInt32Value
import com.ghost.protobuf.wkt.ProtoUInt32ValueSerializer
import com.ghost.protobuf.wkt.ProtoUInt64Value
import com.ghost.protobuf.wkt.ProtoUInt64ValueSerializer
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
