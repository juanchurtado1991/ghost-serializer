@file:Suppress("UNCHECKED_CAST", "OPT_IN_USAGE")

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.common.GhostHeuristics
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
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.serializers.ShortSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlBooleanArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlDoubleArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlFloatArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlIntArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlLongArraySerializer
import com.ghost.serialization.yaml.serializer.GhostYamlSetSerializer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf


/**
 * Platform synchronization primitive.
 */
expect fun <T> runSynchronized(lock: Any, block: () -> T): T

/**
 * Platform-specific thread-safe atomic map creation.
 */
expect fun <K, V> createAtomicMap(): MutableMap<K, V>

/**
 * Service loader or reflection based module discovery mechanism.
 *
 * Debt: iOS/Wasm actuals return empty — register modules manually via [Ghost.addRegistry].
 * JVM/Android use ServiceLoader/reflection; not unified across targets yet.
 */
expect fun discoverRegistries(): Iterable<GhostRegistry>

/**
 * Runs a block of operations using a pooled [GhostJsonReader] instance.
 */
expect fun <T> ghostInternalUseReader(bytes: ByteArray, block: (GhostJsonReader) -> T): T

/**
 * Runs a block of operations using a pooled [GhostJsonStringReader] instance.
 */
expect fun <T> ghostInternalUseStringReader(json: String, block: (GhostJsonStringReader) -> T): T

/**
 * Runs a block of operations using a pooled zero-copy [GhostJsonFlatReader] instance
 * over a flat [ByteArray] (no Okio streaming).
 */
expect fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray,
    limit: Int = bytes.size,
    block: (GhostJsonFlatReader) -> T
): T

/**
 * Runs a block of operations using a pooled [GhostJsonReader] reading from an Okio [BufferedSource].
 */
expect fun <T> ghostInternalUseSource(source: BufferedSource, block: (GhostJsonReader) -> T): T

/**
 * Encodes via the pooled in-memory [GhostJsonStringWriter] and returns the result as a [String],
 * built directly from the writer's contiguous char slice (no Okio segments) with minimal allocations.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeToString(crossinline block: (GhostJsonStringWriter) -> Unit): String

/**
 * Pools the in-memory writer per-thread and returns the encoded bytes, avoiding the overhead
 * of going through [String]. The writer's scratch buffer is kept warm (not released) between calls.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeWithWriter(crossinline block: (GhostJsonWriter) -> Unit): ByteArray

/**
 * Serializes via the pooled in-memory writer but discards the output
 * without allocating a result [ByteArray]. Useful for warm-up / JIT priming
 * where the encoded bytes are not needed.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeAndDiscard(crossinline block: (GhostJsonWriter) -> Unit)

/**
 * Encodes through the pooled in-memory writer and drains the result to [sink] in a single bulk
 * write — the fast path for `Ghost.serialize(sink, value)`, avoiding per-byte Okio segment dispatch.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeAndDrainTo(
    sink: BufferedSink,
    crossinline block: (GhostJsonWriter) -> Unit
)

/**
 * Core entry point for Ghost Serialization.
 * Provides modular discovery and serialization management across platforms.
 */
object Ghost {

    /**
     * Cache storing registered serializers keyed by their unique type name.
     * Used by the compiler for name-based lookup (e.g., in polymorphic serialization).
     */
    private val serializerByName = createAtomicMap<String, GhostSerializer<*>>()

    /**
     * Fast-path lock-free cache mapping a [KClass] to its corresponding [GhostSerializer].
     * Annotated with `@PublishedApi` because it is accessed by public inline functions on the hot path
     * to avoid lookup overhead.
     */
    @PublishedApi
    internal val serializerCache = createAtomicMap<KClass<*>, GhostSerializer<*>>()

    /**
     * Fast-path lock-free cache mapping a full [KType] (e.g., generic collections like `List<Int>`)
     * to its resolved [GhostSerializer]. Kept separate from [serializerCache] so generic types
     * don't collide on the same [KClass].
     */
    @PublishedApi
    internal val typeCache = createAtomicMap<KType, GhostSerializer<*>>()

    /**
     * Platform-independent lock object used to synchronize access to registries and cache updates.
     */
    private val lock = Any()

    /**
     * Set of manually registered [GhostRegistry] instances.
     * Critical for platforms where automated discovery (ServiceLoader/reflection) is unavailable (e.g., iOS, JS, Wasm).
     */
    private val mutableRegistries = mutableSetOf<GhostRegistry>()

    /**
     * Holds dynamically discovered [GhostRegistry] instances via ServiceLoader or reflection.
     * Lazily initialized to optimize startup time.
     */
    private var _discoveredRegistries: Iterable<GhostRegistry>? = null

    /**
     * Resolves a serializer for a given class from all registered modules.
     */
    private fun <T : Any> getSerializerFromRegistries(
        clazz: KClass<T>
    ): GhostSerializer<T>? {
        for (registry in mutableRegistries) {
            registry.getSerializer(clazz)?.let { return it }
        }

        val disc = _discoveredRegistries
            ?: discoverRegistries().also { _discoveredRegistries = it }

        for (registry in disc) {
            registry.getSerializer(clazz)?.let { return it }
        }

        return null
    }

    /**
     * Helper utility to raise a serialization-related exception.
     */
    fun throwError(message: String): Nothing {
        throw IllegalArgumentException(message)
    }

    /**
     * Registers a new [GhostRegistry] manually. Critical on platforms like iOS and JS/Wasm
     * where automated ServiceLoader discovery is unavailable.
     */
    fun addRegistry(registry: GhostRegistry) {
        runSynchronized(lock) {
            if (mutableRegistries.add(registry)) {
                val serializers = registry.getAllSerializers()
                for (entry in serializers) {
                    val kclass = entry.key
                    val serializer = entry.value
                    serializerCache[kclass] = serializer
                    serializerByName[serializer.typeName] = serializer
                }
            }
        }
    }

    /**
     * Used by compiler to get serializers by name.
     */
    @Suppress("unused")
    fun getSerializerByName(name: String): GhostSerializer<*>? {
        return serializerByName[name]
    }

    /**
     * Internal: Used by compiler-generated code to verify registered serializers.
     */
    @Suppress("unused")
    fun getSerializerNames(): List<String> {
        return serializerByName.keys.toList()
    }

    /**
     * Resolves a [GhostSerializer] for [clazz]. Checks primitives first, then the fast-path
     * cache, and falls back to registered modules if necessary.
     */
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        // Fast path for primitives
        getPrimitiveSerializer(clazz)?.let { return it }
        // Built-in protobuf well-known types (idempotent with manual addRegistry)
        getWktSerializer(clazz)?.let { return it }

        // Atomic lookup (Lock-free on JVM/Android)
        val cached = serializerCache[clazz] as? GhostSerializer<T>
        if (cached != null) {
            return cached
        }

        return runSynchronized(lock) {
            val doubleCheck = serializerCache[clazz] as? GhostSerializer<T>
            if (doubleCheck != null) {
                return@runSynchronized doubleCheck
            }

            val found = getSerializerFromRegistries(clazz)
            if (found != null) {
                serializerCache[clazz] = found as GhostSerializer<Any>
            }
            found
        }
    }

    /**
     * Fast path serializer lookup for native primitive types.
     */
    private fun <T : Any> getPrimitiveSerializer(
        clazz: KClass<T>
    ): GhostSerializer<T>? {
        return when (clazz) {
            String::class -> {
                StringSerializer as GhostSerializer<T>
            }

            Int::class -> {
                IntSerializer as GhostSerializer<T>
            }

            Long::class -> {
                LongSerializer as GhostSerializer<T>
            }

            Boolean::class -> {
                BooleanSerializer as GhostSerializer<T>
            }

            Double::class -> {
                DoubleSerializer as GhostSerializer<T>
            }

            Float::class -> {
                FloatSerializer as GhostSerializer<T>
            }

            Byte::class -> {
                ByteSerializer as GhostSerializer<T>
            }

            Short::class -> {
                ShortSerializer as GhostSerializer<T>
            }

            Char::class -> {
                CharSerializer as GhostSerializer<T>
            }

            IntArray::class -> {
                IntArraySerializer as GhostSerializer<T>
            }

            LongArray::class -> {
                LongArraySerializer as GhostSerializer<T>
            }

            FloatArray::class -> {
                FloatArraySerializer as GhostSerializer<T>
            }

            DoubleArray::class -> {
                DoubleArraySerializer as GhostSerializer<T>
            }

            BooleanArray::class -> {
                BooleanArraySerializer as GhostSerializer<T>
            }

            RawJson::class -> {
                RawJsonSerializer as GhostSerializer<T>
            }

            else -> {
                null
            }
        }
    }

    /**
     * YAML entry-point lookup for primitive arrays. Kept separate from [getPrimitiveSerializer]
     * so JSON resolution continues to return the JSON `*ArraySerializer` instances.
     */
    @PublishedApi
    internal fun <T : Any> getYamlPrimitiveSerializer(
        clazz: KClass<T>
    ): GhostYamlSerializer<T>? {
        return when (clazz) {
            IntArray::class -> GhostYamlIntArraySerializer as GhostYamlSerializer<T>
            LongArray::class -> GhostYamlLongArraySerializer as GhostYamlSerializer<T>
            FloatArray::class -> GhostYamlFloatArraySerializer as GhostYamlSerializer<T>
            DoubleArray::class -> GhostYamlDoubleArraySerializer as GhostYamlSerializer<T>
            BooleanArray::class -> GhostYamlBooleanArraySerializer as GhostYamlSerializer<T>
            else -> null
        }
    }

    /**
     * Built-in serializers for protobuf well-known types.
     */
    private fun <T : Any> getWktSerializer(
        clazz: KClass<T>
    ): GhostSerializer<T>? {
        return when (clazz) {
            ProtoTimestamp::class -> ProtoTimestampSerializer as GhostSerializer<T>
            ProtoDuration::class -> ProtoDurationSerializer as GhostSerializer<T>
            ProtoEmpty::class -> ProtoEmptySerializer as GhostSerializer<T>
            ProtoFieldMask::class -> ProtoFieldMaskSerializer as GhostSerializer<T>
            ProtoAny::class -> ProtoAnySerializer as GhostSerializer<T>
            // ProtoStruct is a typealias for Map<String, ProtoValue> — KClass erases to Map.
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

    /**
     * Resolves a [GhostSerializer] for [type], handling generic type arguments for
     * parameterized classes like lists and maps.
     */
    fun getSerializer(type: KType): GhostSerializer<Any>? {
        val classifier = type.classifier

        if (classifier == List::class || classifier == Map::class || classifier == Set::class) {
            val cached = typeCache[type]
            if (cached != null) {
                return cached as GhostSerializer<Any>
            }

            return runSynchronized(lock) {
                val doubleCheck = typeCache[type]
                if (doubleCheck != null) {
                    return@runSynchronized doubleCheck as GhostSerializer<Any>
                }

                val created = when (classifier) {
                    List::class -> {
                        val itemType = type.arguments.getOrNull(0)?.type
                            ?: return@runSynchronized null

                        val itemSerializer = getSerializer(itemType)
                            ?: return@runSynchronized null

                        if (itemSerializer is GhostYamlSerializer<*>) {
                            com.ghost.serialization.yaml.serializer.GhostYamlListSerializer(
                                itemSerializer
                            )
                        } else {
                            ListSerializer(itemSerializer)
                        }
                    }

                    Set::class -> {
                        val itemType = type.arguments.getOrNull(0)?.type
                            ?: return@runSynchronized null

                        val itemSerializer = getSerializer(itemType)
                            ?: return@runSynchronized null

                        if (itemSerializer is GhostYamlSerializer<*>) {
                            GhostYamlSetSerializer(itemSerializer)
                        } else {
                            SetSerializer(itemSerializer)
                        }
                    }

                    Map::class -> {
                        val valueType = type.arguments.getOrNull(1)?.type
                            ?: return@runSynchronized null

                        val valueSerializer = getSerializer(valueType)
                            ?: return@runSynchronized null

                        if (valueSerializer is GhostYamlSerializer<*>) {
                            com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer(
                                valueSerializer
                            )
                        } else {
                            MapSerializer(valueSerializer)
                        }
                    }

                    else -> {
                        null
                    }
                }

                if (created != null) {
                    typeCache[type] = created
                }

                created as? GhostSerializer<Any>
            }
        }

        // Delegate to class-based resolution (handles primitives and caching)
        val kClass = classifier as? KClass<Any> ?: return null
        return getSerializer(kClass)
    }

    /**
     * Internally resolves the serializer for dynamic type-checking or compile-time resolution.
     */
    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> resolveSerializerByType(
        kClass: KClass<T>,
        typeProducer: () -> KType
    ): GhostSerializer<T> {
        // Fast path: serializerCache is populated at startup by addRegistry/prewarm.
        // Safe for all types that were registered as KClass → serializer mappings.
        (serializerCache[kClass] as? GhostSerializer<T>)?.let { return it }
        // Slow path (first call for this KType): getSerializer(KType) internally
        // caches the result in typeCache so subsequent calls are O(1).
        // No write to serializerCache here — generic types (List<T>, Map<K,V>)
        // share the same KClass and must NOT pollute that cache.
        val type = typeProducer()
        return (getSerializer(type) ?: getSerializer(kClass as KClass<Any>))
                as? GhostSerializer<T>
            ?: throwError("$NOT_FOUND $kClass. $MISSING_ANN")
    }

    /**
     * Resolves the serializer for the reified type parameter [T].
     */
    inline fun <reified T : Any> resolveSerializer(): GhostSerializer<T> {
        val cached = serializerCache[T::class]
        if (cached != null) {
            return cached as GhostSerializer<T>
        }
        return resolveSerializerByType(T::class) { typeOf<T>() }
    }

    /**
     * Encodes [value] and writes the resulting JSON payload into [sink]. Uses the zero-allocation
     * in-memory writer, flushed in a single block write to avoid Okio segment overhead.
     */
    inline fun <reified T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Encodes [value] and writes the resulting JSON payload into [sink] using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> serialize(serializer: GhostSerializer<T>, sink: BufferedSink, value: T) {
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Convenience alias for [encodeToString] to maintain compatibility with standard APIs.
     */
    inline fun <reified T : Any> serialize(value: T): String {
        return encodeToString(value)
    }

    /**
     * Serializes [value] to an in-memory JSON string.
     *
     * On Wasm JavaScriptCore (`GhostHeuristics.encodeToStringViaUtf8Bytes`), uses the UTF-8
     * flat writer + platform UTF-8→String conversion — JSC's `CharArray.concatToString` path is
     * a known encode cliff (#16). Other targets keep the pooled [GhostJsonStringWriter].
     */
    inline fun <reified T : Any> encodeToString(value: T): String {
        val serializer = resolveSerializer<T>()
        return encodeToString(serializer, value)
    }

    /**
     * Serializes [value] to an in-memory JSON string representation using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> encodeToString(serializer: GhostSerializer<T>, value: T): String {
        if (GhostHeuristics.encodeToStringViaUtf8Bytes) {
            val bytes = encodeToBytes(serializer, value)
            return ghostUtf8BytesToString(bytes, 0, bytes.size)
        }
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] to an in-memory JSON [ByteArray], skipping intermediate
     * string formatting/decoding steps.
     */
    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray {
        val serializer = resolveSerializer<T>()
        return ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] to an in-memory JSON byte array representation using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> encodeToBytes(serializer: GhostSerializer<T>, value: T): ByteArray {
        return ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] through the pooled in-memory writer and discards the output.
     * Public API for frameworks / JIT warm-up; may not be referenced from this module.
     */
    @Suppress("unused")
    inline fun <reified T : Any> encodeAndDiscard(value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDiscard { writer ->
            serializer.serialize(writer, value)
        }
    }

    // ── Public deserialize API ────────────

    /**
     * Deserializes the JSON [json] string into an instance of type [T].
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or structure is invalid.
     */
    inline fun <reified T : Any> deserialize(json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            deserialize(reader)
        }
    }

    /**
     * Deserializes the JSON [json] string using a pre-resolved [serializer].
     */
    fun <T : Any> deserialize(serializer: GhostSerializer<T>, json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Deserializes JSON data from an Okio [BufferedSource] into an instance of type [T].
     *
     * Loads the entire stream into heap via `source.request(Long.MAX_VALUE)` (~2× payload size
     * peak RAM) — not suitable for payloads over ~10 MB; use [deserializeStreaming] instead.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(source: BufferedSource): T {
        source.request(Long.MAX_VALUE)
        val limit = source.buffer.size.toInt()
        val bytes = acquireScratchBuffer(limit)
        try {
            var offset = 0
            while (offset < limit) {
                val count = source.read(bytes, offset, limit - offset)
                if (count == -1) {
                    break
                }
                offset += count
            }
            return ghostInternalUseFlatReader(bytes, limit) { reader ->
                deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(bytes)
        }
    }

    /**
     * Deserializes JSON from [source] using true O(1)-memory streaming — Okio paginates in ~8 KB
     * segments rather than loading the whole payload into memory, unlike [deserialize]. Prefer
     * [deserialize] for normal-sized payloads (faster flat-array parsing).
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or structure is invalid.
     */
    inline fun <reified T : Any> deserializeStreaming(source: BufferedSource): T {
        return ghostInternalUseSource(source) { reader ->
            deserialize(reader)
        }
    }

    /**
     * Deserializes JSON data from [source] using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> deserializeStreaming(serializer: GhostSerializer<T>, source: BufferedSource): T {
        return ghostInternalUseSource(source) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Deserializes the JSON [bytes] array into an instance of type [T]. Uses the flat reader
     * ([GhostJsonFlatReader]), same engine as the options overload.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException
     * if the JSON payload is malformed or structure is invalid.
     */
    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostInternalUseFlatReader(bytes) { reader ->
            deserialize(reader)
        }
    }

    /**
     * Deserializes the JSON [bytes] array using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> deserialize(serializer: GhostSerializer<T>, bytes: ByteArray): T {
        return ghostInternalUseFlatReader(bytes) { reader ->
            serializer.deserialize(reader)
        }
    }

    // ── Advanced overloads: options exposes GhostJsonReader → opt-in required ─

    /**
     * Advanced: Deserializes the JSON [json] string using custom parser settings.
     */
    inline fun <reified T : Any> deserialize(
        json: String,
        crossinline options: (GhostJsonStringReader) -> Unit
    ): T {
        return ghostInternalUseStringReader(json) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    /**
     * Advanced: Deserializes JSON data from a [BufferedSource] stream using custom parser settings.
     */
    inline fun <reified T : Any> deserialize(
        source: BufferedSource,
        crossinline options: (GhostJsonReader) -> Unit
    ): T {
        return ghostInternalUseSource(source) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    /**
     * Advanced: Deserializes the JSON [bytes] array using custom parser settings.
     *
     * Uses the same flat reader as [deserialize] `(bytes)` so `strictMode` /
     * `coerceStringsToNumbers` apply on the hot path. For true streaming from a
     * [BufferedSource], use [deserializeStreaming] or the source+options overload.
     */
    inline fun <reified T : Any> deserialize(
        bytes: ByteArray,
        crossinline options: (GhostJsonFlatReader) -> Unit
    ): T {
        return ghostInternalUseFlatReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    /**
     * Non-inline variant of [deserialize] that decodes a [ByteArray] into the specified [clazz].
     * Public API for frameworks (Spring, Retrofit) where reified types are unavailable.
     *
     * @param limit The byte length boundary of the payload inside [bytes].
     */
    @Suppress("unused")
    fun <T : Any> decodeFromBytes(bytes: ByteArray, clazz: KClass<T>, limit: Int = bytes.size): T {
        return ghostInternalUseFlatReader(bytes, limit) { reader ->
            val serializer = getSerializer(clazz)
                ?: throwError("$NOT_FOUND ${clazz.simpleName}")

            serializer.deserialize(reader)
        }
    }

    /**
     * Non-inline variant of [deserialize] that decodes a [BufferedSource] stream into the specified [clazz].
     * Useful in reflection or framework integration contexts where reified types are unavailable.
     */
    fun <T : Any> decodeFromSource(source: BufferedSource, clazz: KClass<T>): T {
        source.request(Long.MAX_VALUE)
        val limit = source.buffer.size.toInt()
        val bytes = acquireScratchBuffer(limit)
        try {
            var offset = 0
            while (offset < limit) {
                val count = source.read(bytes, offset, limit - offset)
                if (count == -1) {
                    break
                }
                offset += count
            }
            return ghostInternalUseFlatReader(bytes, limit) { reader ->
                val serializer = getSerializer(clazz)
                    ?: throwError("$NOT_FOUND ${clazz.simpleName}")

                serializer.deserialize(reader)
            }
        } finally {
            releaseScratchBuffer(bytes)
        }
    }

    /**
     * Encodes [value] and writes it directly to [sink]. Alias for [serialize].
     */
    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known
     * only as a [KClass] at runtime.
     * Public API for frameworks (Spring HttpMessageConverter, Retrofit adapters).
     */
    @Suppress("unused")
    fun <T : Any> encodeToSink(sink: BufferedSink, value: T, clazz: KClass<T>) {
        val serializer = getSerializer(clazz)
            ?: throwError("$NOT_FOUND ${clazz.simpleName}. $MISSING_ANN")
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Helper deserialize routine for KSP generated serializers.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /**
     * Helper deserialize routine for KSP generated serializers using the flat in-memory reader.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonFlatReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /**
     * Advanced: Deserializes directly from an existing [GhostJsonStringReader].
     * Note: This bypassing of pooling means the caller is responsible for the reader lifecycle.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonStringReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /**
     * Triggers eager loading and JIT/ART warm-up cycles for all registered serializers.
     * Call this at application startup to achieve zero-latency first-run deserialization.
     */
    fun prewarm() {
        runSynchronized(lock) {
            // Force discovery if not yet done
            if (_discoveredRegistries == null) {
                _discoveredRegistries = discoverRegistries()
            }

            // Manual ones
            for (registry in mutableRegistries) {
                registry.prewarm()
                val serializers = registry.getAllSerializers()
                if (serializers.isNotEmpty()) {
                    for (entry in serializers) {
                        val kclass = entry.key
                        val serializer = entry.value
                        serializerCache[kclass] = serializer
                        serializerByName[serializer.typeName] = serializer
                        serializer.warmUp()
                    }
                }
            }

            // Discovered ones
            val discovered = _discoveredRegistries
            if (discovered != null) {
                for (registry in discovered) {
                    registry.prewarm()
                    val serializers = registry.getAllSerializers()
                    if (serializers.isNotEmpty()) {
                        for (entry in serializers) {
                            val kclass = entry.key
                            val serializer = entry.value
                            serializerCache[kclass] = serializer
                            serializerByName[serializer.typeName] = serializer
                            serializer.warmUp()
                        }
                    }
                }
            }
        }
    }

    internal const val DEFAULT_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_Default"
    internal const val TEST_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_Default_Test"
    internal const val ANDROID_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization"
    internal const val INSTANCE_FIELD = "INSTANCE"

    /**
     * Serializer not found message prefix.
     */
    const val MISSING_ANN = "Did you annotate it with @GhostSerialization?"

    /**
     * Missing serializer configuration error prefix.
     */
    const val NOT_FOUND = "No Ghost serializer found for"

    /**
     * Test hook: clears registries and serializer caches to prevent cross-test pollution.
     * Not for production use.
     */
    @InternalGhostApi
    fun resetForTest() {
        runSynchronized(lock) {
            mutableRegistries.clear()
            _discoveredRegistries = null
            serializerCache.clear()
            typeCache.clear()
            serializerByName.clear()
        }
    }
}
