@file:Suppress("UNCHECKED_CAST", "OPT_IN_USAGE")

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.serializers.BooleanSerializer
import com.ghost.serialization.serializers.DoubleSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonSerializer
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
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
 */
expect fun discoverRegistries(): Iterable<GhostRegistry>

/**
 * Runs a block of operations using a pooled [GhostJsonReader] instance.
 */
@OptIn(InternalGhostApi::class)
expect fun <T> ghostInternalUseReader(bytes: ByteArray, block: (GhostJsonReader) -> T): T

/**
 * Runs a block of operations using a pooled [GhostJsonStringReader] instance.
 */
@OptIn(InternalGhostApi::class)
expect fun <T> ghostInternalUseStringReader(json: String, block: (GhostJsonStringReader) -> T): T

/**
 * Runs a block of operations using a pooled [GhostJsonFlatReader] instance.
 */
@OptIn(InternalGhostApi::class)
expect fun <T> ghostInternalUseFlatReader(bytes: ByteArray, limit: Int = bytes.size, block: (GhostJsonFlatReader) -> T): T

/**
 * Runs a block of operations using a pooled [GhostJsonReader] reading from an Okio [BufferedSource].
 */
@OptIn(InternalGhostApi::class)
expect fun <T> ghostInternalUseSource(source: BufferedSource, block: (GhostJsonReader) -> T): T

/**
 * Encodes via the pooled in-memory [GhostJsonStringWriter] and returns the
 * result as a [String]. The string writer holds a contiguous [CharArray]
 * (no Okio segments), so the returned string is decoded directly from
 * the produced char slice with minimal allocations.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeToString(crossinline block: (GhostJsonStringWriter) -> Unit): String

/**
 * Pools the in-memory [GhostJsonFlatWriter] per-thread and returns the
 * encoded bytes. Use this when you need a [ByteArray] result without the
 * overhead of going through [String].
 *
 * The writer is reset (`needsComma=false, depth=0`) before each call; its
 * scratch buffer is kept warm (not released) to avoid pool round-trips
 * between requests.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeWithWriter(crossinline block: (GhostJsonFlatWriter) -> Unit): ByteArray

/**
 * Serializes via the pooled [GhostJsonFlatWriter] but discards the output
 * without allocating a result [ByteArray]. Useful for warm-up / JIT priming
 * where the encoded bytes are not needed.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeAndDiscard(crossinline block: (GhostJsonFlatWriter) -> Unit)

/**
 * Encodes through the pooled [GhostJsonFlatWriter] and drains the resulting
 * contiguous payload to [sink] in a single bulk write. This is the fast path
 * for `Ghost.serialize(sink, value)` — every byte-level operation goes
 * through the monomorphic flat writer (no per-byte Okio segment dispatch),
 * and the final flush is a single [BufferedSink.write] call which Okio
 * implements as a few `System.arraycopy`s into its segment buffer.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeAndDrainTo(sink: BufferedSink, crossinline block: (GhostJsonFlatWriter) -> Unit)

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
     * to its resolved [GhostSerializer].
     * Annotated with `@PublishedApi` because it is accessed by public inline functions.
     * Kept separate from [serializerCache] to prevent generic types from colliding on the same [KClass].
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
        // 1. Check manual registries
        for (registry in mutableRegistries) {
            registry.getSerializer(clazz)?.let { return it }
        }

        // 2. Check discovered registries
        if (_discoveredRegistries == null) {
            _discoveredRegistries = discoverRegistries()
        }
        val disc = _discoveredRegistries!!

        for (registry in disc) {
            registry.getSerializer(clazz)?.let { return it }
        }

        return null
    }

    /**
     * Helper utility to raise a serialization-related exception.
     *
     * @param message The detailed error message.
     * @throws IllegalArgumentException always thrown with the specified message.
     */
    fun throwError(message: String): Nothing {
        throw IllegalArgumentException(message)
    }

    /**
     * Registers a new [GhostRegistry] manually.
     *
     * Manual registration is particularly critical on platforms like iOS (Kotlin/Native)
     * and JS/Wasm where automated discovery via ServiceLoader is unavailable.
     *
     * @param registry The registry instance containing the generated serializers to register.
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
     * Resolves a [GhostSerializer] instance for the specified class [clazz].
     * Checks primitives first, then the fast-path cache, and falls back to
     * registered modules if necessary.
     *
     * @param clazz The [KClass] of the type to resolve the serializer for.
     * @return The matching [GhostSerializer], or `null` if no serializer is found.
     */
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        // Fast path for primitives
        getPrimitiveSerializer(clazz)?.let { return it }

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
            RawJson::class -> {
                RawJsonSerializer as GhostSerializer<T>
            }
            else -> {
                null
            }
        }
    }

    /**
     * Resolves a [GhostSerializer] instance for the specified type [type],
     * handling generic type arguments for parameterized classes like lists and maps.
     *
     * @param type The [KType] representing the type of the data structure.
     * @return The matching [GhostSerializer], or `null` if no serializer is found.
     */
    fun getSerializer(type: KType): GhostSerializer<Any>? {
        val classifier = type.classifier

        // Special handling for parameterized collections
        if (classifier == List::class || classifier == Map::class) {
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

                        ListSerializer(itemSerializer)
                    }

                    Map::class -> {
                        val valueType = type.arguments.getOrNull(1)?.type
                            ?: return@runSynchronized null

                        val valueSerializer = getSerializer(valueType)
                            ?: return@runSynchronized null

                        MapSerializer(valueSerializer)
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
    @PublishedApi
    internal inline fun <reified T : Any> resolveSerializer(): GhostSerializer<T> {
        val cached = serializerCache[T::class]
        if (cached != null) {
            return cached as GhostSerializer<T>
        }
        return resolveSerializerByType(T::class) { typeOf<T>() }
    }

    /**
     * Encodes [value] and writes the resulting JSON payload into [sink].
     *
     * Employs the zero-allocation [GhostJsonFlatWriter] internally to format the data
     * into a contiguous scratch buffer and flushes it in a single block write.
     * This avoids Okio segment management and virtual dispatch overhead on the hot path.
     *
     * @param sink The Okio sink to write the JSON payload to.
     * @param value The value to serialize.
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
     *
     * @param value The value to serialize.
     * @return The serialized JSON string.
     */
    inline fun <reified T : Any> serialize(value: T): String {
        return encodeToString(value)
    }

    /**
     * Serializes [value] to an in-memory JSON string representation.
     *
     * Bypasses Okio segment management by writing to a flat, contiguous byte buffer
     * and performing a zero-copy string decode at the end.
     *
     * @param value The value to serialize.
     * @return The serialized JSON string.
     */
    inline fun <reified T : Any> encodeToString(value: T): String {
        val serializer = resolveSerializer<T>()
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] to an in-memory JSON string representation using a pre-resolved [serializer].
     *
     * Bypasses type lookup and resolution overhead.
     */
    fun <T : Any> encodeToString(serializer: GhostSerializer<T>, value: T): String {
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] to an in-memory JSON byte array representation.
     *
     * Skips intermediate string formatting/decoding steps by exposing the raw
     * UTF-8 bytes directly.
     *
     * @param value The value to serialize.
     * @return A [ByteArray] containing the serialized JSON UTF-8 payload.
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
     * Serializes [value] through the pooled [GhostJsonFlatWriter] and discards
     * the output. No [BufferedSink] allocation, no Okio wrapper objects.
     *
     * @param value The value to serialize.
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
     * @param json The JSON string representation of the object.
     * @return A reconstructed instance of type [T].
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or structure is invalid.
     */
    inline fun <reified T : Any> deserialize(json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            deserialize(reader)
        }
    }

    /**
     * Deserializes the JSON [json] string using a pre-resolved [serializer].
     *
     * @param serializer The pre-resolved serializer.
     * @param json The JSON string representation of the object.
     * @return A reconstructed instance of type [T].
     */
    fun <T : Any> deserialize(serializer: GhostSerializer<T>, json: String): T {
        return ghostInternalUseStringReader(json) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Deserializes JSON data from an Okio [BufferedSource] into an instance of type [T].
     *
     * **⚠️ Not suitable for payloads larger than ~10 MB.**
     *
     * This method calls `source.request(Long.MAX_VALUE)` internally, which forces Okio to
     * download the **entire stream into heap memory** before parsing begins. The peak RAM usage
     * is approximately **2× the payload size** (one copy in Okio's buffer, one in the scratch
     * array). On constrained environments such as Android, this will cause an OutOfMemoryError
     * for large files even if the individual buffers would fit, due to heap fragmentation.
     *
     * For payloads that may exceed available heap, use [deserializeStreaming] instead — it reads
     * in ~8 KB Okio segments and keeps memory usage constant regardless of file size.
     *
     * @param source The [BufferedSource] containing the JSON payload. Must be a bounded stream
     *   whose total size fits comfortably within the available heap (recommended < 10 MB).
     * @return A reconstructed instance of type [T].
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or the structure is invalid.
     * @see deserializeStreaming for O(1)-memory streaming of large files.
     */
    @OptIn(InternalGhostApi::class)
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
     * Deserializes JSON data from an Okio [BufferedSource] using true O(1)-memory streaming.
     *
     * Unlike [deserialize], this method does **not** load the entire file into memory.
     * Okio paginates the source in ~8 KB segments on demand, making it safe for database
     * dumps or JSON files of hundreds of megabytes with a constant memory footprint.
     *
     * Use [deserialize] for normal REST payloads (faster flat-array parsing).
     * Use this method when file size may exceed available heap.
     *
     * @param source The BufferedSource stream containing the JSON payload.
     * @return A reconstructed instance of type [T].
     * @throws com.ghost.serialization.exception.GhostJsonException if the JSON payload is malformed or structure is invalid.
     */
    @OptIn(InternalGhostApi::class)
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
     * Deserializes the JSON [bytes] array into an instance of type [T].
     *
     * @param bytes A [ByteArray] containing the JSON UTF-8 payload.
     * @return A reconstructed instance of type [T].
     * @throws com.ghost.serialization.exception.GhostJsonException
     * if the JSON payload is malformed or structure is invalid.
     */
    @OptIn(InternalGhostApi::class)
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
     *
     * @param json The JSON string representation of the object.
     * @param options A configuration lambda to set reader properties.
     * @return A reconstructed instance of type [T].
     */
    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(
        json: String,
        crossinline options: (GhostJsonReader) -> Unit
    ): T {
        val bytes = json.encodeToByteArray()
        return ghostInternalUseReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    /**
     * Advanced: Deserializes JSON data from a [BufferedSource] stream using custom parser settings.
     *
     * @param source The BufferedSource stream containing the JSON payload.
     * @param options A configuration lambda to set reader properties.
     * @return A reconstructed instance of type [T].
     */
    @OptIn(InternalGhostApi::class)
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
     * @param bytes A [ByteArray] containing the JSON UTF-8 payload.
     * @param options A configuration lambda to set reader properties.
     * @return A reconstructed instance of type [T].
     */
    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(
        bytes: ByteArray,
        crossinline options: (GhostJsonReader) -> Unit
    ): T {
        return ghostInternalUseReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    /**
     * Non-inline variant of [deserialize] that decodes a [ByteArray] into the specified [clazz].
     * Useful in reflection or framework integration contexts where reified types are unavailable.
     *
     * @param bytes A [ByteArray] containing the JSON UTF-8 payload.
     * @param clazz The KClass of the target type to deserialize.
     * @param limit The byte length boundary of the payload inside [bytes].
     * @return A reconstructed instance of type [T].
     */
    @Suppress("unused")
    @OptIn(InternalGhostApi::class)
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
     *
     * @param source The BufferedSource stream containing the JSON payload.
     * @param clazz The KClass of the target type to deserialize.
     * @return A reconstructed instance of type [T].
     */
    @OptIn(InternalGhostApi::class)
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
     *
     * @param sink The Okio sink to write the JSON payload to.
     * @param value The value to serialize.
     */
    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known
     * only as a [KClass] at runtime (e.g. Spring HttpMessageConverter, Retrofit adapters).
     *
     * @param sink The Okio sink to write the JSON payload to.
     * @param value The value to serialize.
     * @param clazz The KClass of the target type to serialize.
     */
    @OptIn(InternalGhostApi::class)
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
    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /**
     * Helper deserialize routine for KSP generated serializers.
     */
    @OptIn(InternalGhostApi::class)
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

    internal const val DEFAULT_REGISTRY_NAME = "com.ghost.serialization.generated.GhostModuleRegistry_Default"
    internal const val TEST_REGISTRY_NAME = "com.ghost.serialization.generated.GhostModuleRegistry_Default_Test"
    internal const val ANDROID_REGISTRY_NAME = "com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization"
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
     * Test-only utility to clear global state and prevent test pollution.
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
