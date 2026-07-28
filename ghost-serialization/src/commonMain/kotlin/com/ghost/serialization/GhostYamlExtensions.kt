@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter

/**
 * Decodes the YAML [yaml] string into an instance of type [T] using its registered companion serializer.
 */
inline fun <reified T : Any> Ghost.decodeFromYaml(yaml: String): T {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("Serializer not found for ${T::class.simpleName ?: "unknown"}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("Serializer for ${T::class.simpleName ?: "unknown"} does not implement GhostYamlSerializer")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    val bytes = yaml.encodeToByteArray()
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        yamlSerializer.deserialize(reader)
    }
}

/**
 * Decodes the YAML UTF-8 [bytes] into an instance of type [T] using its registered companion serializer.
 */
inline fun <reified T : Any> Ghost.decodeFromYaml(bytes: ByteArray): T {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("Serializer not found for ${T::class.simpleName ?: "unknown"}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("Serializer for ${T::class.simpleName ?: "unknown"} does not implement GhostYamlSerializer")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        yamlSerializer.deserialize(reader)
    }
}

/**
 * Serializes [value] into a YAML string representation.
 */
inline fun <reified T : Any> Ghost.encodeToYaml(value: T): String {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("Serializer not found for ${T::class.simpleName ?: "unknown"}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("Serializer for ${T::class.simpleName ?: "unknown"} does not implement GhostYamlSerializer")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatWriter { writer ->
        yamlSerializer.serialize(writer, value)
        writer.buffer.toStringUtf8()
    }
}

/**
 * Serializes [value] into a YAML UTF-8 byte array representation.
 */
inline fun <reified T : Any> Ghost.encodeToYamlBytes(value: T): ByteArray {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("Serializer not found for ${T::class.simpleName ?: "unknown"}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("Serializer for ${T::class.simpleName ?: "unknown"} does not implement GhostYamlSerializer")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatWriter { writer ->
        yamlSerializer.serialize(writer, value)
        writer.buffer.toByteArray()
    }
}
