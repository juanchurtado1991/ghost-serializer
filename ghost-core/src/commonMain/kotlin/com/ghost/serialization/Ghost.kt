package com.ghost.serialization

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.reflect.KClass

/**
 * High-level Industrial Entry Point for Ghost Serialization.
 * Absolute Superiority: Modular Discovery via expect/actual.
 */

// Shared cache for performance
internal val serializerCache = mutableMapOf<KClass<*>, GhostSerializer<*>>()

// Platform-specific discovery logic
expect fun discoverRegistries(): List<GhostRegistry>

object Ghost {
    
    private val registries: List<GhostRegistry> by lazy {
        discoverRegistries()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
        // Simple map lookup first
        val cached = serializerCache[clazz]
        if (cached != null) return cached as GhostSerializer<T>
        
        // Chain lookup
        val found = registries.firstNotNullOfOrNull { it.getSerializer(clazz) }
        if (found != null) {
            serializerCache[clazz] = found
        }
        return found as? GhostSerializer<T>
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
        val serializer = getSerializer(T::class)
            ?: throw IllegalArgumentException("No Ghost serializer found for ${T::class.simpleName}")
        return serializer.deserialize(reader)
    }

    fun prewarm() {
        registries.forEach { it.prewarm() }
    }
}
