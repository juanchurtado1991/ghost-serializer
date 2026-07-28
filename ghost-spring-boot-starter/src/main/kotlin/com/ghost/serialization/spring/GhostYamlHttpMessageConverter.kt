package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Spring [org.springframework.http.converter.HttpMessageConverter] for YAML-backed Ghost types
 * ([GhostYamlSerializer]).
 */
class GhostYamlHttpMessageConverter : AbstractHttpMessageConverter<Any>(
    MediaType("application", "yaml"),
    MediaType("application", "x-yaml"),
    MediaType("text", "yaml"),
) {
    private val supportsCache = ConcurrentHashMap<Class<*>, Boolean>()
    private val serializerCache = ConcurrentHashMap<Class<*>, GhostSerializer<Any>>()

    override fun supports(clazz: Class<*>): Boolean {
        val cached = supportsCache[clazz]
        if (cached != null) return cached

        val result = if (isExcludedType(clazz)) {
            false
        } else {
            val serializer = Ghost.getSerializer(clazz.kotlin)
            serializer is GhostYamlSerializer<*>
        }
        supportsCache[clazz] = result
        return result
    }

    private fun isExcludedType(clazz: Class<*>): Boolean {
        return clazz == String::class.java ||
            clazz == ByteArray::class.java ||
            clazz.isPrimitive ||
            clazz.name.startsWith("java.lang.")
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val bytes = inputMessage.body.readBytes()
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()

        @Suppress("UNCHECKED_CAST")
        val targetClass = clazz as Class<Any>
        val serializer = serializerCache.getOrPut(targetClass) {
            val resolved = Ghost.getSerializer(targetClass.kotlin)
                ?: Ghost.throwError("${Ghost.NOT_FOUND} ${targetClass.simpleName}")
            if (resolved !is GhostYamlSerializer<*>) {
                Ghost.throwError("Serializer for ${targetClass.simpleName} does not implement GhostYamlSerializer")
            }
            resolved
        }

        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>

        return ghostYamlInternalUseFlatReader(bytes) { reader ->
            reader.strictMode = isStrict
            if (isCoerce) {
                reader.coerceStringsToNumbers = true
                reader.coerceBooleans = true
            }
            yamlSerializer.deserialize(reader)
        }
    }

    override fun writeInternal(t: Any, outputMessage: HttpOutputMessage) {
        val clazz = t.javaClass
        val serializer = serializerCache.getOrPut(clazz) {
            @Suppress("UNCHECKED_CAST")
            val resolved = Ghost.getSerializer(clazz.kotlin as KClass<Any>) as GhostSerializer<Any>?
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} ${clazz.simpleName}. ${Ghost.MISSING_ANN}"
                )
            if (resolved !is GhostYamlSerializer<*>) {
                throw IllegalArgumentException(
                    "Serializer for ${clazz.simpleName} does not implement GhostYamlSerializer"
                )
            }
            resolved
        }

        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val bytes = ghostYamlInternalUseFlatWriter { writer ->
            yamlSerializer.serialize(writer, t)
            writer.buffer.toByteArray()
        }
        outputMessage.body.write(bytes)
        outputMessage.body.flush()
    }
}
