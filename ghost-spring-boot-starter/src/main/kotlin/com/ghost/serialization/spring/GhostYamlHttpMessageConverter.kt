package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractGenericHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * `HttpMessageConverter` for YAML-backed Ghost types (`GhostYamlSerializer`).
 *
 * Resolves top-level `List` / `Set` / `Map` when element/value serializers implement
 * [GhostYamlSerializer].
 */
class GhostYamlHttpMessageConverter : AbstractGenericHttpMessageConverter<Any>(
    GhostSpringMediaTypes.APPLICATION_YAML,
    GhostSpringMediaTypes.APPLICATION_X_YAML,
    GhostSpringMediaTypes.TEXT_YAML,
) {

    override fun supports(clazz: Class<*>): Boolean =
        GhostSpringTypeSerializers.getYamlSerializer(clazz) != null

    override fun canRead(type: Type, contextClass: Class<*>?, mediaType: MediaType?): Boolean =
        canRead(mediaType) && GhostSpringTypeSerializers.getYamlSerializer(type) != null

    override fun canWrite(type: Type?, clazz: Class<*>, mediaType: MediaType?): Boolean =
        canWrite(mediaType) &&
            GhostSpringTypeSerializers.getYamlSerializer(type ?: clazz) != null

    override fun canWrite(mediaType: MediaType?): Boolean {
        if (mediaType == null || mediaType.isWildcardType || mediaType.isWildcardSubtype) {
            return false
        }
        return super.canWrite(mediaType)
    }

    override fun canRead(mediaType: MediaType?): Boolean {
        if (mediaType == null) {
            return false
        }
        return super.canRead(mediaType)
    }

    override fun read(
        type: Type,
        contextClass: Class<*>?,
        inputMessage: HttpInputMessage
    ): Any {
        val serializer = GhostSpringTypeSerializers.getYamlSerializer(type)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} $type",
                inputMessage
            )
        return deserializeYaml(serializer, inputMessage.body.readBytes())
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val serializer = GhostSpringTypeSerializers.getYamlSerializer(clazz)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} ${clazz.simpleName}",
                inputMessage
            )
        return deserializeYaml(serializer, inputMessage.body.readBytes())
    }

    override fun writeInternal(t: Any, type: Type?, outputMessage: HttpOutputMessage) {
        val serializer = resolveWriteSerializer(t, type)
        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val bytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            yamlSerializer.serialize(writer, t)
            buffer.toByteArray()
        }
        outputMessage.body.write(bytes)
        outputMessage.body.flush()
    }

    private fun resolveWriteSerializer(t: Any, type: Type?): GhostSerializer<Any> {
        type?.let { declared ->
            GhostSpringTypeSerializers.getYamlSerializer(declared)?.let { return it }
        }
        @Suppress("UNCHECKED_CAST")
        return GhostSpringTypeSerializers.getYamlSerializer(t.javaClass)
            ?: run {
                val resolved = Ghost.getSerializer(t::class as KClass<Any>)
                if (resolved is GhostYamlSerializer<*>) {
                    @Suppress("UNCHECKED_CAST")
                    resolved as GhostSerializer<Any>
                } else {
                    null
                }
            }
            ?: throw HttpMessageNotWritableException(
                "${Ghost.NOT_FOUND} ${t.javaClass.simpleName}. ${Ghost.MISSING_ANN}"
            )
    }

    private fun deserializeYaml(serializer: GhostSerializer<Any>, bytes: ByteArray): Any {
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()
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
}
