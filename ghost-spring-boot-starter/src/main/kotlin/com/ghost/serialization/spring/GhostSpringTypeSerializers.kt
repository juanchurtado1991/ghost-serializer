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
            if (isExcludedType(type)) return null
            @Suppress("UNCHECKED_CAST")
            val serializer = Ghost.getSerializer(type.kotlin as KClass<Any>) ?: return null
            if (yamlOnly && serializer !is GhostYamlSerializer<*>) return null
            return serializer
        }

        if (type !is ParameterizedType) return null
        val rawType = type.rawType as? Class<*> ?: return null

        if (List::class.java.isAssignableFrom(rawType)) {
            val arg = type.actualTypeArguments.firstOrNull() ?: return null
            val item = resolveCached(arg, yamlOnly) ?: return null
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
            val item = resolveCached(arg, yamlOnly) ?: return null
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
            val arg = type.actualTypeArguments.getOrNull(1) ?: return null
            val value = resolveCached(arg, yamlOnly) ?: return null
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

    private fun isExcludedType(clazz: Class<*>): Boolean {
        return clazz == String::class.java ||
            clazz == ByteArray::class.java ||
            clazz.isPrimitive ||
            clazz.name.startsWith("java.lang.")
    }
}
