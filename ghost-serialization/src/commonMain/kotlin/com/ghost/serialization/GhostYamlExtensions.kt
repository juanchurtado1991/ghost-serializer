@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter

/**
 * Decodes the YAML [yaml] string into an instance of type [T] using its registered companion serializer.
 */
inline fun <reified T : Any> Ghost.decodeFromYaml(yaml: String): T {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
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
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        yamlSerializer.deserialize(reader)
    }
}

/**
 * Decodes every YAML document in [yaml] (separated by `---`) into instances of [T].
 */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(yaml: String): List<T> {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatReader(yaml.encodeToByteArray()) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(docReader) }
    }
}

/**
 * Decodes every YAML document in [bytes] (separated by `---`) into instances of [T].
 */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(bytes: ByteArray): List<T> {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(docReader) }
    }
}

/**
 * Serializes [value] into a YAML string representation.
 */
inline fun <reified T : Any> Ghost.encodeToYaml(value: T): String {
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
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
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return ghostYamlInternalUseFlatWriter { writer ->
        yamlSerializer.serialize(writer, value)
        writer.buffer.toByteArray()
    }
}

/**
 * Serializes [values] as a multi-document YAML stream (`---` between documents).
 */
inline fun <reified T : Any> Ghost.encodeAllToYaml(values: List<T>): String {
    if (values.isEmpty()) return ""
    val serializer = Ghost.getSerializer(T::class)
        ?: throw IllegalArgumentException("${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}")
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException("${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${T::class.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}")
    }
    @Suppress("UNCHECKED_CAST")
    val yamlSerializer = serializer as GhostYamlSerializer<T>
    return values.joinToString(separator = "\n---\n") { value ->
        ghostYamlInternalUseFlatWriter { writer ->
            yamlSerializer.serialize(writer, value)
            writer.buffer.toStringUtf8()
        }
    }
}

/**
 * Serializes [values] as a multi-document YAML UTF-8 byte stream (`---` between documents).
 * Public API for frameworks that prefer byte payloads over [String].
 */
inline fun <reified T : Any> Ghost.encodeAllToYamlBytes(values: List<T>): ByteArray {
    return encodeAllToYaml(values).encodeToByteArray()
}
