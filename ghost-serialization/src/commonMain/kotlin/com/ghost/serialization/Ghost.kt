@file:OptIn(InternalGhostApi::class)
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
import okio.BufferedSink
import okio.BufferedSource
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Core entry point for Ghost Serialization.
 * Provides modular discovery and serialization management across platforms.
 */

expect fun <T> runSynchronized(lock: Any, block: () -> T): T

expect fun <K, V> createAtomicMap(): MutableMap<K, V>

expect fun discoverRegistries(): List<GhostRegistry>

expect fun <T> ghostInternalUseReader(
    bytes: ByteArray, block: (GhostJsonReader) -> T
): T

expect fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T

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

    inline fun <reified T : Any> serialize(sink: BufferedSink, value: T) {
        // High-speed path for known classes
        val serializer = (serializerCache[T::class] as? GhostSerializer<T>)
            ?: run {
                val type = kotlin.reflect.typeOf<T>()
                getSerializer(type) ?: getSerializer(T::class as KClass<Any>)
            } as? GhostSerializer<T> ?: throwError("$NOT_FOUND ${T::class}. $MISSING_ANN")
            
        val writer = GhostJsonWriter(sink)
        try {
            serializer.serialize(writer, value)
        } finally {
            writer.release()
        }
    }

    /** Convenience alias for [encodeToString] to maintain API compatibility. */
    inline fun <reified T : Any> serialize(value: T): String = encodeToString(value)

    inline fun <reified T : Any> encodeToString(value: T): String {
        val buffer = okio.Buffer()
        serialize(buffer, value)
        return buffer.readUtf8()
    }

    inline fun <reified T : Any> encodeToBytes(value: T): ByteArray {
        val buffer = okio.Buffer()
        serialize(buffer, value)
        return buffer.readByteArray()
    }

    inline fun <reified T : Any> deserialize(
        json: String,
        crossinline options: (GhostJsonReader) -> Unit = {}
    ): T {
        val bytes = json.encodeToByteArray()
        return ghostInternalUseReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    inline fun <reified T : Any> deserialize(
        source: BufferedSource,
        crossinline options: (GhostJsonReader) -> Unit = {}
    ): T {
        return ghostInternalUseSource(source) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    inline fun <reified T : Any> deserialize(
        bytes: ByteArray,
        crossinline options: (GhostJsonReader) -> Unit = {}
    ): T {
        return ghostInternalUseReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    fun <T : Any> decodeFromBytes(bytes: ByteArray, clazz: KClass<T>): T {
        return ghostInternalUseReader(bytes) { reader ->
            val serializer = getSerializer(clazz)
                ?: throwError("$NOT_FOUND ${clazz.simpleName}")
            serializer.deserialize(reader) as T
        }
    }

    fun <T : Any> decodeFromSource(source: BufferedSource, clazz: KClass<T>): T {
        return ghostInternalUseSource(source) { reader ->
            val serializer = getSerializer(clazz)
                ?: throwError("$NOT_FOUND ${clazz.simpleName}")
            serializer.deserialize(reader) as T
        }
    }

    inline fun <reified T : Any> encodeToSink(sink: BufferedSink, value: T) {
        serialize(sink, value)
    }

    @MustUseReturnValues
    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T {
        val serializer = getSerializer(T::class)
            ?: throwError("$NOT_FOUND ${T::class.simpleName}")

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
}
