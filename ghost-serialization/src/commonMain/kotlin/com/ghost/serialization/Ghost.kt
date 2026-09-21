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


expect fun <T> runSynchronized(lock: Any, block: () -> T): T

expect fun <K, V> createAtomicMap(): MutableMap<K, V>

/**
 * Service-loader/reflection based module discovery. iOS/Wasm actuals return empty — register
 * modules manually via [Ghost.addRegistry]; JVM/Android use ServiceLoader/reflection.
 */
expect fun discoverRegistries(): Iterable<GhostRegistry>

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
 * Pools the in-memory writer per-thread and returns encoded bytes directly, avoiding the
 * overhead of going through [String]; the scratch buffer stays warm between calls.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeWithWriter(crossinline block: (GhostJsonWriter) -> Unit): ByteArray

/**
 * Serializes via the pooled in-memory writer but discards the output without allocating a
 * result [ByteArray]. Useful for warm-up / JIT priming.
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
 * Core entry point for Ghost Serialization: modular discovery and serialization management
 * across platforms.
 */
object Ghost {

    /** Registered serializers keyed by type name, for compiler name-based lookup (polymorphic serialization). */
    private val serializerByName = createAtomicMap<String, GhostSerializer<*>>()

    /**
     * Lock-free [KClass] → [GhostSerializer] cache. `@PublishedApi` because public inline
     * functions access it directly on the hot path.
     */
    @PublishedApi
    internal val serializerCache = createAtomicMap<KClass<*>, GhostSerializer<*>>()

    /**
     * Lock-free [KType] → [GhostSerializer] cache (e.g. `List<Int>`). Kept separate from
     * [serializerCache] so generic types don't collide on the same [KClass].
     */
    @PublishedApi
    internal val typeCache = createAtomicMap<KType, GhostSerializer<*>>()

    /** Platform-independent lock for registry and cache updates. */
    private val lock = Any()

    /** Manually registered [GhostRegistry] instances — required on platforms without ServiceLoader/reflection (iOS, JS, Wasm). */
    private val mutableRegistries = mutableSetOf<GhostRegistry>()

    /** Registries discovered via ServiceLoader/reflection; lazily initialized to keep startup fast. */
    private var _discoveredRegistries: Iterable<GhostRegistry>? = null

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

    fun throwError(message: String): Nothing {
        throw IllegalArgumentException(message)
    }

    /** Registers a [GhostRegistry] manually — required on platforms without ServiceLoader discovery (iOS, JS/Wasm). */
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

    /** Used by compiler-generated code for name-based lookup. */
    @Suppress("unused")
    fun getSerializerByName(name: String): GhostSerializer<*>? {
        return serializerByName[name]
    }

    /** Used by compiler-generated code to verify registered serializers. */
    @Suppress("unused")
    fun getSerializerNames(): List<String> {
        return serializerByName.keys.toList()
    }

    /** Resolves a [GhostSerializer] for [clazz]: primitives, then the fast-path cache, then registered modules. */
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

    /** Built-in serializers for protobuf well-known types (WKT). */
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

    /** Resolves the serializer for [kClass], preferring the cache over calling [typeProducer]. */
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

    inline fun <reified T : Any> resolveSerializer(): GhostSerializer<T> {
        val cached = serializerCache[T::class]
        if (cached != null) {
            return cached as GhostSerializer<T>
        }
        return resolveSerializerByType(T::class) { typeOf<T>() }
    }

    /**
     * Encodes [value] into [sink] using the zero-allocation in-memory writer, flushed in a
     * single block write to avoid Okio segment overhead.
     */
    inline fun <reified T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Encodes [value] into [sink] using a pre-resolved [serializer], bypassing type lookup. */
    fun <T : Any> serialize(serializer: GhostSerializer<T>, sink: BufferedSink, value: T) {
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Alias for [encodeToString], for compatibility with standard APIs. */
    inline fun <reified T : Any> serialize(value: T): String {
        return encodeToString(value)
    }

    /**
     * Serializes [value] to a JSON string.
     *
     * On Wasm JavaScriptCore (`GhostHeuristics.encodeToStringViaUtf8Bytes`), uses UTF-8 flat
     * writer + platform UTF-8→String conversion — JSC's `CharArray.concatToString` is a known
     * encode cliff (#16). Other targets use the pooled [GhostJsonStringWriter].
     */
    inline fun <reified T : Any> encodeToString(value: T): String {
        val serializer = resolveSerializer<T>()
        return encodeToString(serializer, value)
    }

    /** Serializes [value] to a JSON string using a pre-resolved [serializer], bypassing type lookup. */
    fun <T : Any> encodeToString(serializer: GhostSerializer<T>, value: T): String {
        if (GhostHeuristics.encodeToStringViaUtf8Bytes) {
            val bytes = encodeToBytes(serializer, value)
            return ghostUtf8BytesToString(bytes, 0, bytes.size)
        }
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Serializes [value] to a JSON [ByteArray], skipping intermediate string formatting/decoding. */
    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray {
        val serializer = resolveSerializer<T>()
        return ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Serializes [value] to a JSON [ByteArray] using a pre-resolved [serializer], bypassing type lookup. */
    fun <T : Any> encodeToBytes(serializer: GhostSerializer<T>, value: T): ByteArray {
        return ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Serializes [value] and discards the output. Public API for frameworks / JIT warm-up. */
    @Suppress("unused")
    inline fun <reified T : Any> encodeAndDiscard(value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDiscard { writer ->
            serializer.serialize(writer, value)
        }
    }

    // ── Public deserialize API ────────────

    /**
     * Deserializes [json] into an instance of [T].
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            deserialize(reader)
        }
    }

    /** Deserializes [json] using a pre-resolved [serializer], bypassing type lookup. */
    fun <T : Any> deserialize(serializer: GhostSerializer<T>, json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Deserializes [source] into an instance of [T]. Loads the entire stream into heap via
     * `source.request(Long.MAX_VALUE)` (~2× payload size peak RAM) — not suitable for payloads
     * over ~10 MB; use [deserializeStreaming] instead.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed or the structure is invalid.
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
     * Deserializes [source] using true O(1)-memory streaming — Okio paginates in ~8 KB segments
     * instead of loading the whole payload, unlike [deserialize]. Prefer [deserialize] for
     * normal-sized payloads (faster flat-array parsing).
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed or the structure is invalid.
     */
    inline fun <reified T : Any> deserializeStreaming(source: BufferedSource): T {
        return ghostInternalUseSource(source) { reader ->
            deserialize(reader)
        }
    }

    /** Deserializes [source] using a pre-resolved [serializer], bypassing type lookup. */
    fun <T : Any> deserializeStreaming(serializer: GhostSerializer<T>, source: BufferedSource): T {
        return ghostInternalUseSource(source) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Deserializes [bytes] into an instance of [T] via the flat reader ([GhostJsonFlatReader]),
     * same engine as the options overload.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostInternalUseFlatReader(bytes) { reader ->
            deserialize(reader)
        }
    }

    /** Deserializes [bytes] using a pre-resolved [serializer], bypassing type lookup. */
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
     * Advanced: Deserializes [bytes] using custom parser settings, via the same flat reader as
     * [deserialize] `(bytes)` so `strictMode` / `coerceStringsToNumbers` apply. For true
     * streaming from a [BufferedSource], use [deserializeStreaming] or the source+options overload.
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
     * Non-inline variant of [deserialize] for frameworks (Spring, Retrofit) where reified types
     * are unavailable.
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

    /** Non-inline variant of [deserialize] for reflection/framework contexts where reified types are unavailable. */
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

    /** Alias for [serialize]. */
    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known only as a
     * [KClass] at runtime. Public API for frameworks (Spring HttpMessageConverter, Retrofit adapters).
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
     * Advanced: Deserializes directly from an existing [GhostJsonStringReader], bypassing
     * pooling — the caller is responsible for the reader's lifecycle.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonStringReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /** Eagerly loads and JIT/ART-warms all registered serializers; call at app startup for zero-latency first-run deserialization. */
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

    /** Hint appended when a serializer lookup fails. */
    const val MISSING_ANN = "Did you annotate it with @GhostSerialization?"

    /** Prefix for the serializer-not-found error message. */
    const val NOT_FOUND = "No Ghost serializer found for"

    /** Test-only: clears registries and serializer caches to prevent cross-test pollution. */
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
