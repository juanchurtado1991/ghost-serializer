package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlSetSerializer
import org.springframework.core.ResolvableType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Resolves Ghost serializers from Java [Type] / Spring [ResolvableType], including
 * top-level `List` / `Set` / `Map` unwrap (parity with Retrofit / Ktor).
 *
 * Top-level `String` / `byte[]` / primitives / `java.lang.*` stay excluded so Spring's
 * default converters keep those bodies. The same types are still valid as List/Set/Map
 * element arguments (e.g. `List<String>`), matching Retrofit's `Ghost.getSerializer` path.
 * Map unwrap requires a [String] key type.
 */
internal object GhostSpringTypeSerializers {

    private val jsonCache = ConcurrentHashMap<Type, GhostSerializer<Any>>()
    private val yamlCache = ConcurrentHashMap<Type, GhostSerializer<Any>>()

    fun getJsonSerializer(type: Type): GhostSerializer<Any>? =
        resolveCached(type, yamlOnly = false)

    fun getYamlSerializer(type: Type): GhostSerializer<Any>? =
        resolveCached(type, yamlOnly = true)

    fun getJsonSerializer(elementType: ResolvableType): GhostSerializer<Any>? =
        getJsonSerializer(elementType.type)

    fun getYamlSerializer(elementType: ResolvableType): GhostSerializer<Any>? =
        getYamlSerializer(elementType.type)

    private fun resolveCached(type: Type, yamlOnly: Boolean): GhostSerializer<Any>? {
        val cache = if (yamlOnly) yamlCache else jsonCache
        cache[type]?.let { return it }
        val resolved = resolve(type, yamlOnly) ?: return null
        val existing = cache.putIfAbsent(type, resolved)
        return existing ?: resolved
    }

    private fun resolve(type: Type, yamlOnly: Boolean): GhostSerializer<Any>? {
        if (type is Class<*>) {
            // Top-level only: leave scalars to Spring's String/byte[] converters.
            if (isExcludedTopLevelType(type)) return null
            return resolveClass(type, yamlOnly)
        }
        return resolveParameterized(type, yamlOnly)
    }

    /** Element / value args: no top-level exclusion (allows `List<String>`, etc.). */
    private fun resolveElement(type: Type, yamlOnly: Boolean): GhostSerializer<Any>? {
        if (type is Class<*>) {
            return resolveClass(type, yamlOnly)
        }
        return resolveParameterized(type, yamlOnly)
    }

    private fun resolveClass(clazz: Class<*>, yamlOnly: Boolean): GhostSerializer<Any>? {
        @Suppress("UNCHECKED_CAST")
        val serializer = Ghost.getSerializer(clazz.kotlin as KClass<Any>) ?: return null
        if (yamlOnly && serializer !is GhostYamlSerializer<*>) return null
        return serializer
    }

    private fun resolveParameterized(type: Type, yamlOnly: Boolean): GhostSerializer<Any>? {
        if (type !is ParameterizedType) return null
        val rawType = type.rawType as? Class<*> ?: return null

        if (List::class.java.isAssignableFrom(rawType)) {
            val arg = type.actualTypeArguments.firstOrNull() ?: return null
            val item = resolveElement(arg, yamlOnly) ?: return null
            return if (yamlOnly) {
                if (item !is GhostYamlSerializer<*>) return null
                @Suppress("UNCHECKED_CAST")
                GhostYamlListSerializer(item) as GhostSerializer<Any>
            } else {
                @Suppress("UNCHECKED_CAST")
                ListSerializer(item) as GhostSerializer<Any>
            }
        }

        if (Set::class.java.isAssignableFrom(rawType)) {
            val arg = type.actualTypeArguments.firstOrNull() ?: return null
            val item = resolveElement(arg, yamlOnly) ?: return null
            return if (yamlOnly) {
                if (item !is GhostYamlSerializer<*>) return null
                @Suppress("UNCHECKED_CAST")
                GhostYamlSetSerializer(item) as GhostSerializer<Any>
            } else {
                @Suppress("UNCHECKED_CAST")
                SetSerializer(item) as GhostSerializer<Any>
            }
        }

        if (Map::class.java.isAssignableFrom(rawType)) {
            val keyArg = type.actualTypeArguments.getOrNull(0) ?: return null
            if (!isStringMapKeyType(keyArg)) return null
            val valueArg = type.actualTypeArguments.getOrNull(1) ?: return null
            val value = resolveElement(valueArg, yamlOnly) ?: return null
            return if (yamlOnly) {
                if (value !is GhostYamlSerializer<*>) return null
                @Suppress("UNCHECKED_CAST")
                GhostYamlMapSerializer(value) as GhostSerializer<Any>
            } else {
                @Suppress("UNCHECKED_CAST")
                MapSerializer(value) as GhostSerializer<Any>
            }
        }

        return null
    }

    private fun isExcludedTopLevelType(clazz: Class<*>): Boolean {
        return clazz == String::class.java ||
            clazz == ByteArray::class.java ||
            clazz.isPrimitive ||
            clazz.name.startsWith("java.lang.")
    }

    private fun isStringMapKeyType(keyType: Type): Boolean =
        keyType == String::class.java
}
