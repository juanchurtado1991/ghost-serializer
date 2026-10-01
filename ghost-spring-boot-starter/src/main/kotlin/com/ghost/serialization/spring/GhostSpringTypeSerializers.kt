package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.GhostReflectTypeSerializers
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import org.springframework.core.ResolvableType
import java.lang.reflect.Type

/**
 * Resolves Ghost serializers from Java [Type] / Spring [ResolvableType] through the shared
 * [GhostReflectTypeSerializers] (the same traversal Retrofit uses), unwrapping top-level
 * `List` / `Set` / `Map`.
 *
 * Top-level `String` / `byte[]` / primitives / `java.lang.*` stay excluded so Spring's
 * default converters keep those bodies, though they're still valid as collection element
 * types.
 *
 * Resolves top-level classes through [registry] (defaults to the global [Ghost] singleton)
 * rather than calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 */
@OptIn(InternalGhostApi::class)
internal class GhostSpringTypeSerializers(
    registry: GhostRegistry = Ghost
) {

    private val jsonSerializers = GhostReflectTypeSerializers(
        registry = registry,
        isExcludedTopLevel = ::isExcludedTopLevelType
    )
    private val yamlSerializers = GhostReflectTypeSerializers(
        registry = registry,
        yamlOnly = true,
        isExcludedTopLevel = ::isExcludedTopLevelType
    )

    fun getJsonSerializer(type: Type): GhostSerializer<Any>? = jsonSerializers.get(type = type)

    fun getJsonSerializer(elementType: ResolvableType): GhostSerializer<Any>? =
        getJsonSerializer(type = elementType.type)

    fun getYamlSerializer(type: Type): GhostSerializer<Any>? = yamlSerializers.get(type = type)

    fun getYamlSerializer(elementType: ResolvableType): GhostSerializer<Any>? =
        getYamlSerializer(type = elementType.type)

    private fun isExcludedTopLevelType(clazz: Class<*>): Boolean {
        return clazz == String::class.java ||
            clazz == ByteArray::class.java ||
            clazz.isPrimitive ||
            clazz.name.startsWith("java.lang.")
    }
}
