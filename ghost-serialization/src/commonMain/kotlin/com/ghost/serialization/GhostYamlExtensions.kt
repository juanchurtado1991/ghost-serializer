@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.yaml.GhostYamlConstants as C
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import kotlin.reflect.KClass

/**
 * Resolves a [GhostYamlSerializer] for [clazz], preferring YAML primitive-array serializers
 * so JSON `*ArraySerializer` instances from [Ghost.getSerializer] do not shadow them.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Ghost.resolveYamlSerializer(clazz: KClass<T>): GhostYamlSerializer<T> {
    getYamlPrimitiveSerializer(clazz)?.let { return it }
    val serializer = getSerializer(clazz)
        ?: throw IllegalArgumentException(
            "${C.ERR_SERIALIZER_NOT_FOUND_PREFIX}${clazz.simpleName ?: C.STR_UNKNOWN_TYPE}"
        )
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException(
            "${C.ERR_NOT_YAML_SERIALIZER_PREFIX}${clazz.simpleName ?: C.STR_UNKNOWN_TYPE}${C.ERR_NOT_YAML_SERIALIZER_SUFFIX}"
        )
    }
    return serializer as GhostYamlSerializer<T>
}

/**
 * Decodes the YAML [yaml] string into an instance of type [T] using its registered companion serializer.
 */
inline fun <reified T : Any> Ghost.decodeFromYaml(yaml: String): T {
    val yamlSerializer = resolveYamlSerializer(T::class)
    val bytes = yaml.encodeToByteArray()
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        yamlSerializer.deserialize(reader)
    }
}

/**
 * Decodes the YAML UTF-8 [bytes] into an instance of type [T] using its registered companion serializer.
 */
inline fun <reified T : Any> Ghost.decodeFromYaml(bytes: ByteArray): T {
    val yamlSerializer = resolveYamlSerializer(T::class)
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        yamlSerializer.deserialize(reader)
    }
}

/**
 * Decodes every YAML document in [yaml] (separated by `---`) into instances of [T].
 */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(yaml: String): List<T> {
    val yamlSerializer = resolveYamlSerializer(T::class)
    return ghostYamlInternalUseFlatReader(yaml.encodeToByteArray()) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(docReader) }
    }
}

/**
 * Decodes every YAML document in [bytes] (separated by `---`) into instances of [T].
 */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(bytes: ByteArray): List<T> {
    val yamlSerializer = resolveYamlSerializer(T::class)
    return ghostYamlInternalUseFlatReader(bytes) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(docReader) }
    }
}

/**
 * Serializes [value] into a YAML string representation.
 */
inline fun <reified T : Any> Ghost.encodeToYaml(value: T): String {
    val yamlSerializer = resolveYamlSerializer(T::class)
    return ghostYamlInternalUseFlatWriter { writer ->
        yamlSerializer.serialize(writer, value)
        writer.buffer.toStringUtf8()
    }
}

/**
 * Serializes [value] into a YAML UTF-8 byte array representation.
 */
inline fun <reified T : Any> Ghost.encodeToYamlBytes(value: T): ByteArray {
    val yamlSerializer = resolveYamlSerializer(T::class)
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
    val yamlSerializer = resolveYamlSerializer(T::class)
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
    if (values.isEmpty()) return ByteArray(0)
    val yamlSerializer = resolveYamlSerializer(T::class)
    return ghostYamlInternalUseFlatWriter { writer ->
        var index = 0
        val size = values.size
        while (index < size) {
            if (index > 0) {
                writer.buffer.writeByte(C.NEWLINE_INT)
                writer.buffer.writeUtf8(C.STR_DOC_START)
                writer.buffer.writeByte(C.NEWLINE_INT)
            }
            yamlSerializer.serialize(writer, values[index])
            index++
        }
        writer.buffer.toByteArray()
    }
}
