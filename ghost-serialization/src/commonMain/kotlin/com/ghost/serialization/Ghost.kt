@file:Suppress("UNCHECKED_CAST", "OPT_IN_USAGE")

package com.ghost.serialization

import com.ghost.serialization.Ghost.addRegistry
import com.ghost.serialization.Ghost.decodeFromSource
import com.ghost.serialization.Ghost.deserialize
import com.ghost.serialization.Ghost.deserializeStreaming
import com.ghost.serialization.Ghost.encodeToSink
import com.ghost.serialization.Ghost.encodeToString
import com.ghost.serialization.Ghost.prewarm
import com.ghost.serialization.Ghost.serialize
import com.ghost.serialization.Ghost.serializerCache
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlSetSerializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import okio.BufferedSink
import okio.BufferedSource

expect fun <K, V> createAtomicMap(): MutableMap<K, V>

/**
 * Service-loader/reflection based module discovery. iOS/Wasm actuals return empty — register
 * modules manually via [Ghost.addRegistry]; JVM/Android use ServiceLoader/reflection.
 */
expect fun discoverRegistries(): Iterable<GhostRegistry>

/**
 * Serializes via the pooled in-memory writer but discards the output without allocating a
 * result [ByteArray]. Useful for warm-up / JIT priming.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeAndDiscard(
    crossinline block: (GhostJsonWriter) -> Unit
)

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
 * Encodes via the pooled in-memory [GhostJsonStringWriter] and returns the result as a [String],
 * built directly from the writer's contiguous char slice (no Okio segments) with minimal allocations.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeToString(
    crossinline block: (GhostJsonStringWriter) -> Unit
): String

/**
 * Pools the in-memory writer per-thread and returns encoded bytes directly, avoiding the
 * overhead of going through [String]; the scratch buffer stays warm between calls.
 */
@InternalGhostApi
expect inline fun ghostInternalEncodeWithWriter(
    crossinline block: (GhostJsonWriter) -> Unit
): ByteArray

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
expect fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T

/**
 * Runs a block of operations using a pooled [GhostJsonStringReader] instance.
 */
expect fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T

expect fun <T> runSynchronized(lock: Any, block: () -> T): T

/**
 * Core entry point for Ghost Serialization: modular discovery and serialization management
 * across platforms.
 *
 * Implements [GhostRegistry] so frameworks (Retrofit, Ktor, Spring) can depend on the registry
 * abstraction instead of this singleton directly — [getAllSerializers]/[registeredCount] report
 * the aggregate across every discovered/manually-added [GhostRegistry], not just one module.
 *
 * Overloads taking a pre-resolved `serializer` bypass the type lookup.
 */
object Ghost : GhostRegistry {

    /** Registered serializers keyed by type name
     *  for compiler name-based lookup (polymorphic serialization). */
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

    private val lock = Any()

    /**
     * Manually registered [GhostRegistry] instances — required on platforms without
     * ServiceLoader/reflection (iOS, JS, Wasm).
     */
    internal val mutableRegistries = mutableSetOf<GhostRegistry>()

    /** Registries discovered via ServiceLoader/reflection
     *  lazily initialized to keep startup fast. */
    internal var discoveredRegistries: Iterable<GhostRegistry>? = null

    /** Registers a [GhostRegistry] manually
     * required on platforms without ServiceLoader discovery (iOS, JS/Wasm). */
    fun addRegistry(registry: GhostRegistry) {
        runSynchronized(lock = lock) {
            if (mutableRegistries.add(element = registry)) {
                cacheRegistrySerializers(registry = registry)
            }
        }
    }

    fun throwError(message: String): Nothing = throw IllegalArgumentException(message)

    /** Caches every (KClass, name)→serializer mapping [registry] exposes.
     *  Shared by [addRegistry] and [prewarm]. */
    private fun cacheRegistrySerializers(registry: GhostRegistry) {
        for ((kclass, serializer) in registry.getAllSerializers()) {
            serializerCache[kclass] = serializer
            serializerByName[serializer.typeName] = serializer
        }
    }

    /** Serializes [value] and discards the output. Public API for frameworks / JIT warm-up. */
    @Suppress("unused")
    inline fun <reified T : Any> encodeAndDiscard(
        value: T
    ) = ghostInternalEncodeAndDiscard { writer ->
        resolveSerializer<T>().serialize(writer = writer, value = value)
    }

    /** Serializes [value] to a JSON [ByteArray], skipping intermediate string formatting/decoding. */
    inline fun <reified T : Any> encodeToBytes(
        value: T
    ): ByteArray = ghostInternalEncodeWithWriter { writer ->
        resolveSerializer<T>().serialize(writer = writer, value = value)
    }

    fun <T : Any> encodeToBytes(
        serializer: GhostSerializer<T>,
        value: T
    ): ByteArray = ghostInternalEncodeWithWriter { writer ->
        serializer.serialize(writer = writer, value = value)
    }

    /**
     * Serializes [value] to a JSON string.
     *
     * On Wasm JavaScriptCore (`GhostHeuristics.encodeToStringViaUtf8Bytes`), uses UTF-8 flat
     * writer + platform UTF-8→String conversion — JSC's `CharArray.concatToString` is a known
     * encode cliff (#16). Other targets use the pooled [GhostJsonStringWriter].
     */
    inline fun <reified T : Any> encodeToString(value: T): String = encodeToString(
        serializer = resolveSerializer<T>(),
        value = value
    )

    fun <T : Any> encodeToString(
        serializer: GhostSerializer<T>,
        value: T
    ): String {
        if (GhostHeuristics.encodeToStringViaUtf8Bytes) {
            val bytes = encodeToBytes(serializer = serializer, value = value)
            return ghostUtf8BytesToString(bytes = bytes, offset = 0, length = bytes.size)
        }
        return ghostInternalEncodeToString { writer ->
            serializer.serialize(writer = writer, value = value)
        }
    }

    /** Resolves a [GhostSerializer] for [clazz]: primitives
     *  then the fast-path cache, then registered modules. */
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        // Fast path for primitives
        getPrimitiveSerializer(clazz = clazz)?.let { return it }
        // Built-in protobuf well-known types (idempotent with manual addRegistry)
        getWktSerializer(clazz = clazz)?.let { return it }

        // Atomic lookup (Lock-free on JVM/Android)
        val cached = serializerCache[clazz] as? GhostSerializer<T>
        if (cached != null) {
            return cached
        }

        return runSynchronized(lock = lock) {
            val doubleCheck = serializerCache[clazz] as? GhostSerializer<T>
            if (doubleCheck != null) {
                return@runSynchronized doubleCheck
            }

            val found = getSerializerFromRegistries(clazz = clazz)
            if (found != null) {
                serializerCache[clazz] = found as GhostSerializer<Any>
            }
            found
        }
    }

    /**
     * Resolves a [GhostSerializer] for [type], handling generic type arguments for
     * parameterized classes like lists and maps.
     */
    fun getSerializer(type: KType): GhostSerializer<Any>? {
        val classifier = type.classifier

        val isBuiltInCollection = classifier == List::class || classifier == Map::class || classifier == Set::class
        if (isBuiltInCollection) {
            val cached = typeCache[type]
            if (cached != null) return cached as GhostSerializer<Any>

            return runSynchronized(lock = lock) {
                val doubleCheck = typeCache[type]
                if (doubleCheck != null) {
                    return@runSynchronized doubleCheck as GhostSerializer<Any>
                }

                val created = when (classifier) {
                    List::class -> {
                        val itemType = type.arguments.getOrNull(index = 0)?.type
                            ?: return@runSynchronized null

                        val itemSerializer = getSerializer(type = itemType)
                            ?: return@runSynchronized null

                        if (itemSerializer is GhostYamlSerializer<*>) {
                            GhostYamlListSerializer(
                                itemSerializer = asYamlCapableSerializer(serializer = itemSerializer)
                            )
                        } else {
                            ListSerializer(itemSerializer = itemSerializer)
                        }
                    }

                    Set::class -> {
                        val itemType = type.arguments.getOrNull(index = 0)?.type
                            ?: return@runSynchronized null

                        val itemSerializer = getSerializer(type = itemType)
                            ?: return@runSynchronized null

                        if (itemSerializer is GhostYamlSerializer<*>) {
                            GhostYamlSetSerializer(
                                itemSerializer = asYamlCapableSerializer(
                                    serializer = itemSerializer
                                )
                            )
                        } else {
                            SetSerializer(itemSerializer = itemSerializer)
                        }
                    }

                    Map::class -> {
                        val valueType = type.arguments.getOrNull(index = 1)?.type
                            ?: return@runSynchronized null

                        val valueSerializer = getSerializer(type = valueType)
                            ?: return@runSynchronized null

                        if (valueSerializer is GhostYamlSerializer<*>) {
                            GhostYamlMapSerializer(
                                valueSerializer = asYamlCapableSerializer(serializer = valueSerializer)
                            )
                        } else {
                            MapSerializer(valueSerializer = valueSerializer)
                        }
                    }

                    else -> null
                }

                if (created != null) {
                    typeCache[type] = created
                }

                created as? GhostSerializer<Any>
            }
        }

        // Delegate to class-based resolution (handles primitives and caching)
        val kClass = classifier as? KClass<Any> ?: return null
        return getSerializer(clazz = kClass)
    }

    /** Used by compiler-generated code for name-based lookup. */
    @Suppress("unused")
    fun getSerializerByName(name: String): GhostSerializer<*>? = serializerByName[name]

    /** Used by compiler-generated code to verify registered serializers. */
    @Suppress("unused")
    fun getSerializerNames(): List<String> = serializerByName.keys.toList()

    inline fun <reified T : Any> resolveSerializer(): GhostSerializer<T> {
        val cached = serializerCache[T::class]
        if (cached != null) return cached as GhostSerializer<T>
        return resolveSerializerByType(kClass = T::class) { typeOf<T>() }
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
        return (getSerializer(type = type)
            ?: getSerializer(clazz = kClass as KClass<Any>)) as? GhostSerializer<T>
            ?: throwError(message = "$NOT_FOUND $kClass. $MISSING_ANN")
    }

    /**
     * Encodes [value] into [sink] using the zero-allocation in-memory writer, flushed in a
     * single block write to avoid Okio segment overhead.
     */
    inline fun <reified T : Any> serialize(
        sink: BufferedSink,
        value: T
    ) = ghostInternalEncodeAndDrainTo(sink = sink) { writer ->
        resolveSerializer<T>().serialize(writer = writer, value = value)
    }

    fun <T : Any> serialize(
        serializer: GhostSerializer<T>,
        sink: BufferedSink,
        value: T
    ) = ghostInternalEncodeAndDrainTo(sink = sink) { writer ->
        serializer.serialize(writer = writer, value = value)
    }

    /** Alias for [encodeToString], for compatibility with standard APIs. */
    inline fun <reified T : Any> serialize(value: T): String = encodeToString(value = value)

    // ── Public deserialize API ────────────

    /**
     * Deserializes [json] into an instance of [T].
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed
     *   or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(json: String): T =
        ghostInternalUseStringReader(json = json) { reader -> deserialize(reader = reader) }

    fun <T : Any> deserialize(
        serializer: GhostSerializer<T>,
        json: String
    ): T = ghostInternalUseStringReader(json = json) { reader ->
        serializer.deserialize(reader = reader)
    }

    /**
     * Deserializes [source] into an instance of [T]. Loads the entire stream into heap via
     * `source.request(Long.MAX_VALUE)` (~2× payload size peak RAM) — not suitable for payloads
     * over ~10 MB; use [deserializeStreaming] instead.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed
     *   or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(source: BufferedSource): T =
        readSourceIntoFlatReader(source = source) { reader -> deserialize(reader = reader) }

    /**
     * Deserializes [bytes] into an instance of [T] via the flat reader ([GhostJsonFlatReader]),
     * same engine as the options overload.
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed
     *   or the structure is invalid.
     */
    inline fun <reified T : Any> deserialize(bytes: ByteArray): T =
        ghostInternalUseFlatReader(bytes = bytes) { reader -> deserialize(reader = reader) }

    fun <T : Any> deserialize(
        serializer: GhostSerializer<T>,
        bytes: ByteArray
    ): T = ghostInternalUseFlatReader(bytes = bytes) { reader ->
        serializer.deserialize(reader = reader)
    }

    /**
     * Deserializes [source] using true O(1)-memory streaming — Okio paginates in ~8 KB segments
     * instead of loading the whole payload, unlike [deserialize]. Prefer [deserialize] for
     * normal-sized payloads (faster flat-array parsing).
     *
     * @throws com.ghost.serialization.exception.GhostJsonException if the payload is malformed
     *   or the structure is invalid.
     */
    inline fun <reified T : Any> deserializeStreaming(source: BufferedSource): T =
        ghostInternalUseSource(source = source) { reader -> deserialize(reader = reader) }

    fun <T : Any> deserializeStreaming(
        serializer: GhostSerializer<T>,
        source: BufferedSource
    ): T = ghostInternalUseSource(source = source) { reader ->
        serializer.deserialize(reader = reader)
    }

    // ── Advanced overloads: options exposes GhostJsonReader → opt-in required ─

    /**
     * Non-inline variant of [deserialize] for frameworks (Spring, Retrofit) where reified types
     * are unavailable.
     *
     * @param limit The byte length boundary of the payload inside [bytes].
     */
    @Suppress("unused")
    fun <T : Any> decodeFromBytes(
        bytes: ByteArray,
        clazz: KClass<T>,
        limit: Int = bytes.size
    ): T = ghostInternalUseFlatReader(bytes = bytes, limit = limit) { reader ->
        val serializer = getSerializer(clazz = clazz)
            ?: throwError(message = "$NOT_FOUND ${clazz.simpleName}")

        serializer.deserialize(reader = reader)
    }

    /** Non-inline variant of [deserialize] for reflection/framework contexts where reified types are unavailable. */
    fun <T : Any> decodeFromSource(source: BufferedSource, clazz: KClass<T>): T =
        readSourceIntoFlatReader(source = source) { reader ->
            val serializer = getSerializer(clazz = clazz)
                ?: throwError(message = "$NOT_FOUND ${clazz.simpleName}")

            serializer.deserialize(reader = reader)
        }

    /**
     * Advanced: Deserializes the JSON [json] string using custom parser settings.
     */
    inline fun <reified T : Any> deserialize(
        json: String,
        crossinline options: (GhostJsonStringReader) -> Unit
    ): T = ghostInternalUseStringReader(json = json) { reader ->
        options(reader)
        deserialize(reader = reader)
    }

    /**
     * Advanced: Deserializes JSON data from a [BufferedSource] stream using custom parser settings.
     */
    inline fun <reified T : Any> deserialize(
        source: BufferedSource,
        crossinline options: (GhostJsonReader) -> Unit
    ): T = ghostInternalUseSource(source = source) { reader ->
        options(reader)
        deserialize(reader = reader)
    }

    /**
     * Advanced: Deserializes [bytes] using custom parser settings, via the same flat reader as
     * [deserialize] `(bytes)` so `strictMode` / `coerceStringsToNumbers` apply. For true
     * streaming from a [BufferedSource], use [deserializeStreaming] or the source+options overload.
     */
    inline fun <reified T : Any> deserialize(
        bytes: ByteArray,
        crossinline options: (GhostJsonFlatReader) -> Unit
    ): T = ghostInternalUseFlatReader(bytes = bytes) { reader ->
        options(reader)
        deserialize(reader = reader)
    }

    /**
     * Helper deserialize routine for KSP generated serializers.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T =
        resolveSerializer<T>().deserialize(reader = reader)

    /**
     * Helper deserialize routine for KSP generated serializers using the flat in-memory reader.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonFlatReader): T =
        resolveSerializer<T>().deserialize(reader = reader)

    /**
     * Advanced: Deserializes directly from an existing [GhostJsonStringReader], bypassing
     * pooling — the caller is responsible for the reader's lifecycle.
     */
    inline fun <reified T : Any> deserialize(reader: GhostJsonStringReader): T =
        resolveSerializer<T>().deserialize(reader = reader)

    /** Alias for [serialize]. */
    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) =
        serialize(sink = sink, value = value)

    /**
     * Non-inline variant of [encodeToSink] for contexts where the type is known only as a
     * [KClass] at runtime. Public API for frameworks (Spring HttpMessageConverter, Retrofit adapters).
     */
    @Suppress("unused")
    fun <T : Any> encodeToSink(sink: BufferedSink, value: T, clazz: KClass<T>) {
        val serializer = getSerializer(clazz = clazz)
            ?: throwError(message = "$NOT_FOUND ${clazz.simpleName}. $MISSING_ANN")

        ghostInternalEncodeAndDrainTo(sink = sink) { writer ->
            serializer.serialize(writer = writer, value = value)
        }
    }

    /**
     * Aggregate of every [KClass]→serializer mapping Ghost currently knows about across all
     * discovered/manually-added registries — forces [prewarm] first so this reflects every
     * module, not just whichever ones have been resolved on-demand so far. Built-in primitive
     * and protobuf well-known-type serializers (served via a separate fast path, never cached
     * here) are intentionally excluded — they aren't "registered" in the [GhostRegistry] sense.
     */
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
        prewarm()
        return serializerCache.toMap()
    }

    /**
     * Eagerly loads and JIT/ART-warms all registered serializers; call at app startup for
     * zero-latency first-run deserialization.
     */
    override fun prewarm() {
        runSynchronized(lock = lock) {
            // Force discovery if not yet done
            if (discoveredRegistries == null) {
                discoveredRegistries = discoverRegistries()
            }
            for (registry in mutableRegistries) {
                prewarmRegistry(registry = registry)
            }
            discoveredRegistries?.forEach { registry ->
                prewarmRegistry(registry = registry)
            }
        }
    }

    /**
     * Slurps [source] into a pooled scratch buffer, then hands it to [block] as a
     * [GhostJsonFlatReader]. Shared by [deserialize] `(BufferedSource)` and [decodeFromSource]
     * — the only difference between them is how the resolved serializer is looked up.
     */
    @PublishedApi
    internal fun <T> readSourceIntoFlatReader(
        source: BufferedSource,
        block: (GhostJsonFlatReader) -> T
    ): T {
        source.request(byteCount = Long.MAX_VALUE)
        val limit = source.buffer.size.toInt()
        val bytes = acquireScratchBuffer(minSize = limit)
        try {
            var offset = 0
            while (offset < limit) {
                val count = source.read(
                    sink = bytes,
                    offset = offset,
                    byteCount = limit - offset
                )
                if (count == -1) {
                    break
                }
                offset += count
            }
            return ghostInternalUseFlatReader(
                bytes = bytes,
                limit = limit,
                block = block
            )
        } finally {
            releaseScratchBuffer(buffer = bytes)
        }
    }

    override fun registeredCount(): Int = getAllSerializers().size

    /** Prewarms and caches one [registry]'s serializers. Shared by [prewarm]'s two registry sources. */
    private fun prewarmRegistry(registry: GhostRegistry) {
        registry.prewarm()
        cacheRegistrySerializers(registry = registry)
        for (serializer in registry.getAllSerializers().values) {
            serializer.warmUp()
        }
    }

    internal const val DEFAULT_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_Default"
    internal const val TEST_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_Default_Test"
    internal const val ANDROID_REGISTRY_NAME =
        "com.ghost.serialization.generated.GhostModuleRegistry_ghost_serialization"
    internal const val INSTANCE_FIELD = "INSTANCE"

    const val MISSING_ANN = "Did you annotate it with @GhostSerialization?"

    const val NOT_FOUND = "No Ghost serializer found for"

    /** Test-only: clears registries and serializer caches to prevent cross-test pollution. */
    @InternalGhostApi
    fun resetForTest() {
        runSynchronized(lock = lock) {
            mutableRegistries.clear()
            discoveredRegistries = null
            serializerCache.clear()
            typeCache.clear()
            serializerByName.clear()
        }
    }
}
