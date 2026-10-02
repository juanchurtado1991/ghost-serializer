@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.readAllDocuments
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import kotlin.reflect.KClass
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Bridges a [serializer] already runtime-verified (e.g. via `is GhostYamlSerializer<*>`) to
 * implement [GhostYamlSerializer] back to a type satisfying both bounds the 3
 * `GhostYaml*Serializer` collection wrappers (`GhostYamlListSerializer`/`...MapSerializer`/
 * `...SetSerializer`) require. Needed only when the concrete serializer type is resolved
 * dynamically (reflection over a [KClass]/[kotlin.reflect.KType]) — the compiler can't unify
 * the two bounds on its own there, the way it can when a caller passes an object whose static
 * type already implements both interfaces (as every handwritten/generated serializer does).
 */
@InternalGhostApi
@Suppress("UNCHECKED_CAST")
fun <T, S> asYamlCapableSerializer(serializer: GhostSerializer<T>): S
    where S : GhostSerializer<T>, S : GhostYamlSerializer<T> = serializer as S

/** Decodes every YAML document in [yaml] (separated by `---`) into instances of [T]. */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(yaml: String): List<T> {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatReader(bytes = yaml.encodeToByteArray()) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(reader = docReader) }
    }
}

/** Decodes every YAML document in [bytes] (separated by `---`) into instances of [T]. */
inline fun <reified T : Any> Ghost.decodeAllFromYaml(bytes: ByteArray): List<T> {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatReader(bytes = bytes) { reader ->
        reader.readAllDocuments { docReader -> yamlSerializer.deserialize(reader = docReader) }
    }
}

inline fun <reified T : Any> Ghost.decodeFromYaml(yaml: String): T {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    val bytes = yaml.encodeToByteArray()
    return ghostYamlInternalUseFlatReader(bytes = bytes) { reader ->
        yamlSerializer.deserialize(reader = reader)
    }
}

inline fun <reified T : Any> Ghost.decodeFromYaml(bytes: ByteArray): T {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatReader(bytes = bytes) { reader ->
        yamlSerializer.deserialize(reader = reader)
    }
}

/** Serializes [values] as a multi-document YAML stream (`---` between documents). */
inline fun <reified T : Any> Ghost.encodeAllToYaml(values: List<T>): String {
    if (values.isEmpty()) return ""
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return values.joinToString(separator = "\n---\n") { value ->
        ghostYamlInternalUseFlatWriter { writer, buffer ->
            yamlSerializer.serialize(writer = writer, value = value)
            buffer.toStringUtf8()
        }
    }
}

/** Serializes [values] as a multi-document YAML UTF-8 byte stream, for callers that prefer bytes over [String]. */
inline fun <reified T : Any> Ghost.encodeAllToYamlBytes(values: List<T>): ByteArray {
    if (values.isEmpty()) return ByteArray(size = 0)
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatWriter { writer, buffer ->
        var index = 0
        val size = values.size
        while (index < size) {
            if (index > 0) {
                buffer.writeByte(byteAsInt = TOK.NEWLINE_INT)
                buffer.writeUtf8(text = TOK.STR_DOC_START)
                buffer.writeByte(byteAsInt = TOK.NEWLINE_INT)
            }
            yamlSerializer.serialize(writer = writer, value = values[index])
            index++
        }
        buffer.toByteArray()
    }
}

inline fun <reified T : Any> Ghost.encodeToYaml(value: T): String {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatWriter { writer, buffer ->
        yamlSerializer.serialize(writer = writer, value = value)
        buffer.toStringUtf8()
    }
}

inline fun <reified T : Any> Ghost.encodeToYamlBytes(value: T): ByteArray {
    val yamlSerializer = resolveYamlSerializer(clazz = T::class)
    return ghostYamlInternalUseFlatWriter { writer, buffer ->
        yamlSerializer.serialize(writer = writer, value = value)
        buffer.toByteArray()
    }
}

/**
 * Serializes [value] with an already-resolved [serializer] into YAML bytes — the non-reified entry
 * point the HTTP integrations (Retrofit, Spring, Ktor) share, since they resolve serializers
 * dynamically instead of from a `reified` type.
 */
@InternalGhostApi
fun <T> ghostYamlEncodeToBytes(
    serializer: GhostYamlSerializer<T>,
    value: T
): ByteArray = ghostYamlInternalUseFlatWriter { writer, buffer ->
    serializer.serialize(writer = writer, value = value)
    buffer.toByteArray()
}

/**
 * Resolves a [GhostYamlSerializer] for [clazz], preferring YAML primitive-array serializers
 * so JSON `*ArraySerializer` instances from [Ghost.getSerializer] do not shadow them.
 */
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T : Any> Ghost.resolveYamlSerializer(clazz: KClass<T>): GhostYamlSerializer<T> {
    getYamlPrimitiveSerializer(clazz = clazz)?.let { return it }
    val serializer = getSerializer(clazz = clazz) ?: throw IllegalArgumentException(
        "${EM.ERR_SERIALIZER_NOT_FOUND_PREFIX}${clazz.simpleName ?: EM.STR_UNKNOWN_TYPE}"
    )
    if (serializer !is GhostYamlSerializer<*>) {
        throw IllegalArgumentException(
            "${EM.ERR_NOT_YAML_SERIALIZER_PREFIX}${
                clazz.simpleName
                    ?: EM.STR_UNKNOWN_TYPE
            }${EM.ERR_NOT_YAML_SERIALIZER_SUFFIX}"
        )
    }
    return serializer as GhostYamlSerializer<T>
}
