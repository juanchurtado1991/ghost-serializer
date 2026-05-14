@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization

import com.ghost.serialization.annotations.MustUseReturnValues
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
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
 * Core entry point for Ghost Serialization.
 * Provides modular discovery and serialization management across platforms.
 */

expect fun <T> runSynchronized(lock: Any, block: () -> T): T

expect fun <K, V> createAtomicMap(): MutableMap<K, V>

expect fun discoverRegistries(): List<GhostRegistry>

@OptIn(InternalGhostApi::class)
expect fun <T> ghostInternalUseReader(bytes: ByteArray, block: (GhostJsonReader) -> T): T

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
 *
 * Trade-off: the entire encoded payload lives in memory before the bulk
 * write. For typical request/response sizes (< MB) this is strictly faster
 * than incremental flushing; if a caller really needs to bound memory while
 * streaming GBs of JSON they should encode in chunks.
 */
expect fun ghostInternalEncodeAndDrainTo(sink: BufferedSink, block: (GhostJsonFlatWriter) -> Unit)


object Ghost {

    @PublishedApi
    internal val serializerCache = createAtomicMap<KClass<*>, GhostSerializer<*>>()

    @PublishedApi
    internal val typeCache = createAtomicMap<KType, GhostSerializer<*>>()

    private val lock = Any()
    private val mutableRegistries = mutableSetOf<GhostRegistry>()
    private var _discoveredRegistries: List<GhostRegistry>? = null
    private var _consolidatedRegistries: List<GhostRegistry> = emptyList()

    private fun updateConsolidatedRegistries() {
        val discovered = if (_discoveredRegistries == null) {
            _discoveredRegistries = discoverRegistries()
            _discoveredRegistries!!
        } else {
            _discoveredRegistries!!
        }
        _consolidatedRegistries = mutableRegistries.toList() + discovered
    }

    private val registries: List<GhostRegistry>
        get() = runSynchronized(lock) {
            if (_discoveredRegistries == null) {
                updateConsolidatedRegistries()
            }
            _consolidatedRegistries
        }

    private val serializerByName = mutableMapOf<String, GhostSerializer<*>>()

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
                updateConsolidatedRegistries()
                registry
                    .getAllSerializers()
                    .forEach { (kclass, serializer) ->
                        serializerCache[kclass] = serializer
                        serializerByName[serializer.typeName] = serializer
                    }
            }
        }
    }

    /**
     * Used by compiler to get serializers by name
     */
    @Suppress("unused")
    fun getSerializerByName(name: String): GhostSerializer<*>? {
        return runSynchronized(lock) { serializerByName[name] }
    }

    /**
     * Internal: Used by compiler-generated code to verify registered serializers.
     */
    @Suppress("unused")
    fun getSerializerNames(): List<String> {
        return runSynchronized(lock) { serializerByName.keys.toList() }
    }

    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        // Fast path for primitives
        getPrimitiveSerializer(clazz)?.let { return it }

        // Atomic lookup (Lock-free on JVM/Android)
        val cached = serializerCache[clazz] as? GhostSerializer<T>
        if (cached != null) return cached

        return runSynchronized(lock) {
            val doubleCheck = serializerCache[clazz] as? GhostSerializer<T>
            if (doubleCheck != null) return@runSynchronized doubleCheck

            val found = registries.firstNotNullOfOrNull { it.getSerializer(clazz) }
            if (found != null) {
                serializerCache[clazz] = found as GhostSerializer<Any>
            }
            found as? GhostSerializer<T>
        }
    }

    private fun <T : Any> getPrimitiveSerializer(clazz: KClass<T>): GhostSerializer<T>? = when (clazz) {
        String::class -> StringSerializer as GhostSerializer<T>
        Int::class -> IntSerializer as GhostSerializer<T>
        Long::class -> LongSerializer as GhostSerializer<T>
        Boolean::class -> BooleanSerializer as GhostSerializer<T>
        Double::class -> DoubleSerializer as GhostSerializer<T>
        else -> null
    }

    fun getSerializer(type: KType): GhostSerializer<Any>? {
        val classifier = type.classifier

        // Special handling for parameterized collections
        if (classifier == List::class || classifier == Map::class) {
            val cached = runSynchronized(lock) { typeCache[type] }
            if (cached != null) return cached as GhostSerializer<Any>

            val created = when (classifier) {
                List::class -> {
                    val itemType = type.arguments.getOrNull(0)?.type ?: return null
                    val itemSerializer = getSerializer(itemType) ?: return null
                    ListSerializer(itemSerializer)
                }

                Map::class -> {
                    val valueType = type.arguments.getOrNull(1)?.type ?: return null
                    val valueSerializer = getSerializer(valueType) ?: return null
                    MapSerializer(valueSerializer)
                }

                else -> null
            }

            if (created != null) {
                runSynchronized(lock) { typeCache[type] = created }
            }

            return created as? GhostSerializer<Any>
        }

        // Delegate to class-based resolution (handles primitives and caching)
        val kClass = classifier as? KClass<Any> ?: return null
        return getSerializer(kClass)
    }

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

    @PublishedApi
    internal inline fun <reified T : Any> resolveSerializer(): GhostSerializer<T> =
        resolveSerializerByType(T::class) { typeOf<T>() }

    /**
     * Encodes [value] and writes the resulting JSON payload into [sink].
     *
     * Internally this routes through the pooled monomorphic
     * [GhostJsonFlatWriter] and bulk-copies the produced bytes into [sink] in
     * a single call. That removes per-byte Okio segment dispatch from the
     * hot path while still honouring the `BufferedSink` contract — this is
     * what makes serialize-to-sink the fastest of the three modes in the
     * Ghost benchmark suite.
     *
     * If you need true incremental streaming (e.g. multi-MB payloads where
     * memory bounds matter more than throughput), prefer encoding in chunks
     * yourself.
     */
    inline fun <reified T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    /** Convenience alias for [encodeToString] to maintain API compatibility. */
    inline fun <reified T : Any> serialize(value: T): String = encodeToString(value)

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
     * Use this for stream-mode benchmarks and JIT warm-up where the encoded
     * bytes are not needed.
     */
    inline fun <reified T : Any> encodeAndDiscard(value: T) {
        val serializer = resolveSerializer<T>()
        ghostInternalEncodeAndDiscard { writer ->
            serializer.serialize(writer, value)
        }
    }

    // ── Public deserialize API ────────────

    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(json: String): T {
        val bytes = json.encodeToByteArray()
        return ghostInternalUseReader(bytes) { reader -> deserialize(reader) }
    }

    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(source: BufferedSource): T {
        return ghostInternalUseSource(source) { reader -> deserialize(reader) }
    }

    @OptIn(InternalGhostApi::class)
    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        return ghostInternalUseReader(bytes) { reader -> deserialize(reader) }
    }

    // ── Advanced overloads: options exposes GhostJsonReader → opt-in required ─

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

    @OptIn(InternalGhostApi::class)
    fun <T : Any> decodeFromBytes(bytes: ByteArray, clazz: KClass<T>): T {
        return ghostInternalUseReader(bytes) { reader ->
            val serializer = getSerializer(clazz)
                ?: throwError("$NOT_FOUND ${clazz.simpleName}")

            serializer.deserialize(reader)
        }
    }

    @OptIn(InternalGhostApi::class)
    fun <T : Any> decodeFromSource(source: BufferedSource, clazz: KClass<T>): T {
        return ghostInternalUseSource(source) { reader ->
            val serializer = getSerializer(clazz)
                ?: throwError("$NOT_FOUND ${clazz.simpleName}")

            serializer.deserialize(reader)
        }
    }

    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known
     * only as a [KClass] at runtime (e.g. Spring HttpMessageConverter, Retrofit adapters).
     */
    @OptIn(InternalGhostApi::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> encodeToSink(sink: BufferedSink, value: T, clazz: KClass<T>) {
        val serializer = getSerializer(clazz)
            ?: throwError("$NOT_FOUND ${clazz.simpleName}. $MISSING_ANN")
        ghostInternalEncodeAndDrainTo(sink) { writer ->
            serializer.serialize(writer, value)
        }
    }

    @OptIn(InternalGhostApi::class)
    @MustUseReturnValues
    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T {
        val serializer = resolveSerializer<T>()
        return serializer.deserialize(reader)
    }

    /**
     * Deep Prewarm: Pull all serializers and induce JIT/ART optimization
     */
    fun prewarm() {
        runSynchronized(lock) {
            registries.forEach { registry ->
                registry.prewarm()
                registry
                    .getAllSerializers()
                    .forEach { (kclass, serializer) ->
                        serializerCache[kclass] = serializer
                        serializer.warmUp()
                    }
            }
        }
    }

    const val MISSING_ANN = "Did you annotate it with @GhostSerialization?"
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
            updateConsolidatedRegistries()
        }
    }
}
