package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.SetSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlSetSerializer
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Resolves a [GhostSerializer] from a `java.lang.reflect.Type` for the JVM HTTP integrations
 * (Retrofit, Spring): a raw [Class] is looked up in [registry], and top-level `List<T>`/`Set<T>`/
 * `Map<String, V>` are unwrapped by resolving the element/value type recursively and wrapping it in
 * the matching collection serializer. With [yamlOnly], only serializers implementing
 * [GhostYamlSerializer] qualify and the YAML collection wrappers are used. Top-level classes for
 * which [isExcludedTopLevel] is true resolve to `null` (still valid as collection elements), and
 * top-level results are cached per [Type]. Ktor resolves from `KType` through [Ghost.getSerializer]
 * instead, so the two type systems stay separate.
 */
@InternalGhostApi
class GhostReflectTypeSerializers(
    private val registry: GhostRegistry,
    private val yamlOnly: Boolean = false,
    private val isExcludedTopLevel: (Class<*>) -> Boolean = { false }
) {

    private val cache = ConcurrentHashMap<Type, GhostSerializer<Any>>()

    fun get(type: Type): GhostSerializer<Any>? {
        cache[type]?.let { return it }
        val isExcluded = type is Class<*> && isExcludedTopLevel(type)
        val resolved = (if (isExcluded) null else resolve(type = type)) ?: return null
        return cache.putIfAbsent(type, resolved) ?: resolved
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveClass(clazz: Class<*>): GhostSerializer<Any>? {
        val serializer = registry.getSerializer(clazz = clazz.kotlin as KClass<Any>) ?: return null
        val isRejectedForYaml = yamlOnly && serializer !is GhostYamlSerializer<*>
        return if (isRejectedForYaml) null else serializer
    }

    private fun resolve(type: Type): GhostSerializer<Any>? {
        if (type is Class<*>) return resolveClass(clazz = type)
        if (type !is ParameterizedType) return null
        val rawType = type.rawType as? Class<*> ?: return null
        val arguments = type.actualTypeArguments

        return when {
            List::class.java.isAssignableFrom(rawType) ->
                resolveElement(type = arguments.firstOrNull())?.let(::wrapList)
            Set::class.java.isAssignableFrom(rawType) ->
                resolveElement(type = arguments.firstOrNull())?.let(::wrapSet)
            Map::class.java.isAssignableFrom(rawType) -> {
                val hasStringKey = arguments.getOrNull(0) == String::class.java
                if (hasStringKey) resolveElement(type = arguments.getOrNull(1))?.let(::wrapMap) else null
            }
            else -> null
        }
    }

    private fun resolveElement(type: Type?): GhostSerializer<Any>? = type?.let(::resolve)

    @Suppress("UNCHECKED_CAST")
    private fun wrapList(item: GhostSerializer<Any>): GhostSerializer<Any>? = when {
        !yamlOnly -> ListSerializer(itemSerializer = item)
        item is GhostYamlSerializer<*> -> GhostYamlListSerializer(
            itemSerializer = asYamlCapableSerializer(serializer = item)
        )
        else -> null
    } as GhostSerializer<Any>?

    @Suppress("UNCHECKED_CAST")
    private fun wrapMap(value: GhostSerializer<Any>): GhostSerializer<Any>? = when {
        !yamlOnly -> MapSerializer(valueSerializer = value)
        value is GhostYamlSerializer<*> -> GhostYamlMapSerializer(
            valueSerializer = asYamlCapableSerializer(serializer = value)
        )
        else -> null
    } as GhostSerializer<Any>?

    @Suppress("UNCHECKED_CAST")
    private fun wrapSet(item: GhostSerializer<Any>): GhostSerializer<Any>? = when {
        !yamlOnly -> SetSerializer(itemSerializer = item)
        item is GhostYamlSerializer<*> -> GhostYamlSetSerializer(
            itemSerializer = asYamlCapableSerializer(serializer = item)
        )
        else -> null
    } as GhostSerializer<Any>?
}
