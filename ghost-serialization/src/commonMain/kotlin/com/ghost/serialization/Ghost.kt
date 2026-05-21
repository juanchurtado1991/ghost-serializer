@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.serializers.BooleanSerializer
import com.ghost.serialization.serializers.DoubleSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.writer.GhostJsonFlatWriter
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
 * Encodes via the pooled in-memory [GhostJsonFlatWriter] and returns the
 * result as a [String]. The flat writer holds a contiguous [ByteArray]
 * (no Okio segments), so the returned string is decoded directly from
 * the produced byte slice with zero intermediate copies.
 */
expect fun ghostInternalEncodeToString(block: (GhostJsonFlatWriter) -> Unit): String

/**
 * Pools the in-memory [GhostJsonFlatWriter] per-thread and returns the
 * encoded bytes. Use this when you need a [ByteArray] result without the
 * overhead of going through [String].
 *
 * The writer is reset (`needsComma=false, depth=0`) before each call; its
 * scratch buffer is kept warm (not released) to avoid pool round-trips
 * between requests.
 */
expect fun ghostInternalEncodeWithWriter(block: (GhostJsonFlatWriter) -> Unit): ByteArray

/**
 * Serializes via the pooled [GhostJsonFlatWriter] but discards the output
 * without allocating a result [ByteArray]. Useful for warm-up / JIT priming
 * where the encoded bytes are not needed.
 */
expect fun ghostInternalEncodeAndDiscard(block: (GhostJsonFlatWriter) -> Unit)

/**
 * Encodes through the pooled [GhostJsonFlatWriter] and drains the resulting
 * contiguous payload to [sink] in a single bulk write. This is the fast path
 * for `Ghost.serialize(sink, value)` — every byte-level operation goes
 * through the monomorphic flat writer (no per-byte Okio segment dispatch),
 * and the final flush is a single [BufferedSink.write] call which Okio
 * implements as a few `System.arraycopy`s into its segment buffer.
 */
expect fun ghostInternalEncodeAndDrainTo(sink: BufferedSink, block: (GhostJsonFlatWriter) -> Unit)

/**
 * Core entry point for Ghost Serialization.
 * Provides modular discovery and serialization management across platforms.
 */
object Ghost {

    @PublishedApi
    internal val serializerCache = createAtomicMap<KClass<*>, GhostSerializer<*>>()

    @PublishedApi
    internal val typeCache = createAtomicMap<KType, GhostSerializer<*>>()

    private val lock = Any()
    private val mutableRegistries = mutableSetOf<GhostRegistry>()
    private var _discoveredRegistries: Iterable<GhostRegistry>? = null

    /**
     * Resolves a serializer for a given class from all registered modules.
     */
    private fun <T : Any> getSerializerFromRegistries(clazz: KClass<T>): GhostSerializer<T>? {
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

    private val serializerByName = createAtomicMap<String, GhostSerializer<*>>()

    /**
     * Exception utility for serialization issues.
     */
    fun throwError(message: String): Nothing {
        throw IllegalArgumentException(message)
    }

    /**
     * Manual Registration: Essential for iOS (K/N) and JS/Wasm where auto-discovery
     * via ServiceLoader is not available.
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
     * Finds or creates a serializer for [clazz].
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
    private fun <T : Any> getPrimitiveSerializer(clazz: KClass<T>): GhostSerializer<T>? {
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
            else -> {
                null
            }
        }
    }

    /**
     * Finds or creates a serializer for [type] (handles generic type parameters).
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
     * Internally this routes through the pooled monomorphic
     * [GhostJsonFlatWriter] and bulk-copies the produced bytes into [sink] in
     * a single call. That removes per-byte Okio segment dispatch from the
     * hot path while still honouring the `BufferedSink` contract — this is
     * what makes serialize-to-sink the fastest of the three modes in the
     * Ghost benchmark suite.
     */
    inline fun <reified T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Convenience alias for [encodeToString] to maintain API compatibility. */
    inline fun <reified T : Any> serialize(value: T): String {
        return encodeToString(value)
    }

    /**
     * In-memory encode to [String]. Routes through [GhostJsonFlatWriter] so
     * every byte-level write resolves monomorphically against
     * [com.ghost.serialization.writer.FlatByteArrayWriter] — no Okio segment
     * management, no virtual dispatch on the hot path.
     */
    inline fun <reified T : Any> encodeToString(value: T): String {
        val serializer = resolveSerializer<T>()
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * In-memory encode to [ByteArray]. Same routing as [encodeToString] but
     * skips the UTF-8 decode at the end.
     */
    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray {
        val serializer = resolveSerializer<T>()
        return ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, value)
        }
    }

    /**
     * Serializes [value] through the pooled [GhostJsonFlatWriter] and discards
     * the output. No [BufferedSink] allocation, no Okio wrapper objects.
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
     * Decodes JSON string into object of reified type [T].
     */
    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(json: String): T {
        val bytes = json.encodeToByteArray()
        return ghostInternalUseFlatReader(bytes) { reader ->
            deserialize(reader)
        }
    }

    /**
     * Decodes BufferedSource stream into object of reified type [T].
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
     * Decodes ByteArray bytes into object of reified type [T].
     */
    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostInternalUseFlatReader(bytes) { reader ->
            deserialize(reader)
        }
    }

    // ── Advanced overloads: options exposes GhostJsonReader → opt-in required ─

    /**
     * Advanced: Decodes JSON string using specific reader settings.
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
     * Advanced: Decodes BufferedSource using specific reader settings.
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
     * Advanced: Decodes ByteArray using specific reader settings.
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
     * Non-inline variant of [deserialize] for Class-based deserialization.
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
     * Non-inline variant of [deserialize] for Class-based deserialization from source.
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
     * Encodes a value to BufferedSink.
     */
    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known
     * only as a [KClass] at runtime (e.g. Spring HttpMessageConverter, Retrofit adapters).
     */
    @OptIn(InternalGhostApi::class)
    @Suppress("UNCHECKED_CAST", "unused")
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
     * Deep Prewarm: Pull all serializers and induce JIT/ART optimization
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
