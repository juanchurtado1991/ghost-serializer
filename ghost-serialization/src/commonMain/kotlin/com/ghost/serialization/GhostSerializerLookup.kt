package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
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
import com.ghost.serialization.proto.wkt.ProtoEmpty
import com.ghost.serialization.proto.wkt.ProtoEmptySerializer
import com.ghost.serialization.proto.wkt.ProtoFieldMask
import com.ghost.serialization.proto.wkt.ProtoFieldMaskSerializer
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
import com.ghost.serialization.serializers.BooleanArraySerializer
import com.ghost.serialization.serializers.BooleanSerializer
import com.ghost.serialization.serializers.ByteSerializer
import com.ghost.serialization.serializers.CharSerializer
import com.ghost.serialization.serializers.DoubleArraySerializer
import com.ghost.serialization.serializers.DoubleSerializer
import com.ghost.serialization.serializers.FloatArraySerializer
import com.ghost.serialization.serializers.FloatSerializer
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.ShortSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlBooleanArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlDoubleArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlFloatArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlIntArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlLongArraySerializer
import kotlin.reflect.KClass

/**
 * [Ghost.getSerializer]'s lookup tables: fast-path dispatch for primitives, protobuf well-known
 * types, and manually/service-loader-registered [com.ghost.serialization.contract.GhostRegistry]
 * modules. Split out of `Ghost.kt` since none of these are called by name from outside it.
 */

/** Fast path serializer lookup for native primitive types. */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Ghost.getPrimitiveSerializer(clazz: KClass<T>): GhostSerializer<T>? {
    return when (clazz) {
        String::class -> StringSerializer as GhostSerializer<T>
        Int::class -> IntSerializer as GhostSerializer<T>
        Long::class -> LongSerializer as GhostSerializer<T>
        Boolean::class -> BooleanSerializer as GhostSerializer<T>
        Double::class -> DoubleSerializer as GhostSerializer<T>
        Float::class -> FloatSerializer as GhostSerializer<T>
        Byte::class -> ByteSerializer as GhostSerializer<T>
        Short::class -> ShortSerializer as GhostSerializer<T>
        Char::class -> CharSerializer as GhostSerializer<T>
        IntArray::class -> IntArraySerializer as GhostSerializer<T>
        LongArray::class -> LongArraySerializer as GhostSerializer<T>
        FloatArray::class -> FloatArraySerializer as GhostSerializer<T>
        DoubleArray::class -> DoubleArraySerializer as GhostSerializer<T>
        BooleanArray::class -> BooleanArraySerializer as GhostSerializer<T>
        RawJson::class -> RawJsonSerializer as GhostSerializer<T>
        else -> null
    }
}

internal fun <T : Any> Ghost.getSerializerFromRegistries(clazz: KClass<T>): GhostSerializer<T>? {
    for (registry in mutableRegistries) {
        registry.getSerializer(clazz = clazz)?.let { return it }
    }

    val disc = discoveredRegistries ?: discoverRegistries().also { discoveredRegistries = it }

    for (registry in disc) {
        registry.getSerializer(clazz = clazz)?.let { return it }
    }

    return null
}

/**
 * YAML entry-point lookup for primitive arrays. Kept separate from [getPrimitiveSerializer]
 * so JSON resolution continues to return the JSON `*ArraySerializer` instances.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Ghost.getYamlPrimitiveSerializer(clazz: KClass<T>): GhostYamlSerializer<T>? {
    return when (clazz) {
        IntArray::class -> GhostYamlIntArraySerializer as GhostYamlSerializer<T>
        LongArray::class -> GhostYamlLongArraySerializer as GhostYamlSerializer<T>
        FloatArray::class -> GhostYamlFloatArraySerializer as GhostYamlSerializer<T>
        DoubleArray::class -> GhostYamlDoubleArraySerializer as GhostYamlSerializer<T>
        BooleanArray::class -> GhostYamlBooleanArraySerializer as GhostYamlSerializer<T>
        else -> null
    }
}

/** Single source of truth for [getWktSerializer] — adding a new protobuf well-known type is one
 * map entry here instead of a new `when` branch. */
private val wktSerializers: Map<KClass<*>, GhostSerializer<*>> = mapOf(
    ProtoTimestamp::class to ProtoTimestampSerializer,
    ProtoDuration::class to ProtoDurationSerializer,
    ProtoEmpty::class to ProtoEmptySerializer,
    ProtoFieldMask::class to ProtoFieldMaskSerializer,
    ProtoAny::class to ProtoAnySerializer,
    // ProtoStruct is a typealias for Map<String, ProtoValue> — KClass erases to Map.
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

/** Built-in serializers for protobuf well-known types (WKT). */
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Ghost.getWktSerializer(clazz: KClass<T>): GhostSerializer<T>? =
    wktSerializers[clazz] as? GhostSerializer<T>
