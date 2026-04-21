package com.ghost.serialization

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.serializers.BooleanSerializer
import com.ghost.serialization.serializers.DoubleSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.StringSerializer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.reflect.KClass

/**
 * High-level Industrial Entry Point for Ghost Serialization.
 * Absolute Superiority: Modular Discovery via expect/actual.
 */

internal val serializerCache = mutableMapOf<KClass<*>, GhostSerializer<*>>()
expect fun discoverRegistries(): List<GhostRegistry>

// Platform-specific lock implementation
expect fun <T> runSynchronized(lock: Any, block: () -> T): T

expect fun <T> ghostIternalUseReader(bytes: ByteArray, block: (GhostJsonReader) -> T): T
expect fun <T> ghostInternalUseSource(source: BufferedSource, block: (GhostJsonReader) -> T): T

object Ghost {
    private val lock = Any()
    private val mutableRegistries = mutableListOf<GhostRegistry>()

    private val discoveredRegistries: List<GhostRegistry> by lazy {
        discoverRegistries()
    }

    private val registries: List<GhostRegistry>
        get() = runSynchronized(lock) { mutableRegistries + discoveredRegistries }

    private val serializerByName = mutableMapOf<String, GhostSerializer<*>>()

    /**
     * Manual Registration: Essential for iOS (K/N) and JS/Wasm where auto-discovery
     * via ServiceLoader is not available.
     */
    fun addRegistry(registry: GhostRegistry) {
        runSynchronized(lock) {
            if (!mutableRegistries.contains(registry)) {
                mutableRegistries.add(registry)
                registry
                    .getAllSerializers()
                    .forEach { (kclass, serializer) ->
                        serializerCache[kclass] = serializer
                        val name = kclass.simpleName
                        if (name != null) {
                            serializerByName[name] = serializer
                        }
                    }
            }
        }
    }

    fun getSerializerByName(name: String): GhostSerializer<*>? {
        return runSynchronized(lock) { serializerByName[name] }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        return runSynchronized(lock) {
            val cached = serializerCache[clazz]
            if (cached != null) {
                cached as GhostSerializer<T>
            } else {
                val found = registries.firstNotNullOfOrNull { it.getSerializer(clazz) }
                if (found != null) serializerCache[clazz] = found
                found as? GhostSerializer<T>
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getSerializer(type: kotlin.reflect.KType): GhostSerializer<Any>? {
        val classifier = type.classifier

        when (classifier) {
            String::class -> return StringSerializer as GhostSerializer<Any>
            Int::class -> return IntSerializer as GhostSerializer<Any>
            Long::class -> return LongSerializer as GhostSerializer<Any>
            Boolean::class -> return BooleanSerializer as GhostSerializer<Any>
            Double::class -> return DoubleSerializer as GhostSerializer<Any>
            List::class -> {
                val itemType = type.arguments.getOrNull(0)?.type ?: return null
                val itemSerializer = getSerializer(itemType) ?: return null
                return ListSerializer(itemSerializer) as GhostSerializer<Any>
            }
            Map::class -> {
                val valueType = type.arguments.getOrNull(1)?.type ?: return null
                val valueSerializer = getSerializer(valueType) ?: return null
                return MapSerializer(valueSerializer) as GhostSerializer<Any>
            }
        }

        // Fallback to class-based resolution
        val kClass = classifier as? KClass<Any> ?: return null
        return getSerializer(kClass)
    }

    fun <T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = getSerializer(value::class as KClass<T>)
            ?: throw IllegalArgumentException(
                "No Ghost serializer found for ${value::class.simpleName}." +
                        " Did you annotate it with @GhostSerialization?"
            )
        serializer.serialize(sink, value)
    }

    fun <T : Any> serialize(value: T): String {
        val buffer = okio.Buffer()
        serialize(buffer, value)
        return buffer.readUtf8()
    }

    inline fun <reified T : Any> deserialize(
        json: String,
        options: (GhostJsonReader) -> Unit = {}
    ): T {
        // Optimization: Use direct ByteArray conversion to avoid okio.Buffer overhead in JS/Wasm
        val bytes = json.encodeToByteArray()
        val reader = GhostJsonReader(bytes)
        options(reader)
        val serializer = getSerializer(T::class)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${T::class.simpleName}")
        return serializer.deserialize(reader)
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
        return ghostIternalUseReader(bytes) { reader ->
            options(reader)
            deserialize(reader)
        }
    }

    inline fun <reified T : Any> deserialize(reader: GhostJsonReader): T {
        val serializer = getSerializer(T::class)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${T::class.simpleName}")
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
}
