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

object Ghost {

    private val mutableRegistries = mutableListOf<GhostRegistry>()

    private val discoveredRegistries: List<GhostRegistry> by lazy {
        discoverRegistries()
    }

    private val registries: List<GhostRegistry>
        get() = mutableRegistries + discoveredRegistries

    /**
     * Manual Registration: Essential for iOS (K/N) where auto-discovery
     * via ServiceLoader is not available.
     */
    fun addRegistry(registry: GhostRegistry) {
        if (!mutableRegistries.contains(registry)) {
            mutableRegistries.add(registry)
            registry
                .getAllSerializers()
                .forEach { (kclass, serializer) ->
                    serializerCache[kclass] = serializer
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        val cached = serializerCache[clazz]
        if (cached != null) return cached as GhostSerializer<T>

        val found = registries.firstNotNullOfOrNull { it.getSerializer(clazz) }
        if (found != null) serializerCache[clazz] = found
        return found
    }

    @Suppress("UNCHECKED_CAST")
    fun getSerializer(type: kotlin.reflect.KType): GhostSerializer<Any>? {
        val classifier = type.classifier
        val classifierStr = classifier?.toString() ?: ""

        when {
            classifier == String::class ||
                    classifierStr.contains("String") ->
                return StringSerializer as GhostSerializer<Any>

            classifier == Int::class ||
                    classifierStr.contains("Int") ->
                return IntSerializer as GhostSerializer<Any>

            classifier == Long::class ||
                    classifierStr.contains("Long") ->
                return LongSerializer as GhostSerializer<Any>

            classifier == Boolean::class ||
                    classifierStr.contains("Boolean") ->
                return BooleanSerializer as GhostSerializer<Any>

            classifier == Double::class ||
                    classifierStr.contains("Double") ->
                return DoubleSerializer as GhostSerializer<Any>
        }

        val isList = classifier == List::class ||
                classifierStr.contains("kotlin.collections.List") ||
                classifierStr.contains("java.util.List")

        if (isList) {
            val itemType = type.arguments.getOrNull(0)?.type ?: return null
            val itemSerializer = getSerializer(itemType) ?: return null
            return ListSerializer(itemSerializer) as GhostSerializer<Any>
        }

        val isMap = classifier == Map::class ||
                classifierStr.contains("kotlin.collections.Map") ||
                classifierStr.contains("java.util.Map")

        if (isMap) {
            val valueType = type.arguments.getOrNull(1)?.type ?: return null
            val valueSerializer = getSerializer(valueType) ?: return null
            return MapSerializer(valueSerializer) as GhostSerializer<Any>
        }

        // Fallback to class-based resolution
        val kClass = classifier as? KClass<Any> ?: return null
        return getSerializer(kClass)
    }

    fun <T : Any> serialize(sink: BufferedSink, value: T) {
        val serializer = getSerializer(value::class as KClass<T>)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${value::class.simpleName}. Did you annotate it with @GhostSerialization?")
        serializer.serialize(sink, value)
    }

    fun <T : Any> serialize(value: T): String {
        val buffer = okio.Buffer()
        serialize(buffer, value)
        return buffer.readUtf8()
    }

    inline fun <reified T : Any> deserialize(json: String): T {
        val reader = GhostJsonReader(okio.Buffer().writeUtf8(json))
        val serializer = getSerializer(T::class)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${T::class.simpleName}")
        return serializer.deserialize(reader)
    }

    inline fun <reified T : Any> deserialize(source: BufferedSource): T {
        val reader = GhostJsonReader(source)
        val serializer = getSerializer(T::class)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${T::class.simpleName}")
        return serializer.deserialize(reader)
    }

    inline fun <reified T : Any> deserialize(bytes: ByteArray): T {
        val reader = GhostJsonReader(bytes)
        return deserialize(reader)
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
