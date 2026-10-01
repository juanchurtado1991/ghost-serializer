package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.applyOptions
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.ghostYamlEncodeToBytes
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
 * [GhostYamlSerializer]. Resolves serializers through [registry] (defaults to the global [Ghost]
 * singleton) rather than calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 */
class GhostYamlHttpMessageConverter(
    private val registry: GhostRegistry = Ghost
) : AbstractGenericHttpMessageConverter<Any>(
    GhostSpringMediaTypes.APPLICATION_YAML,
    GhostSpringMediaTypes.APPLICATION_X_YAML,
    GhostSpringMediaTypes.TEXT_YAML,
) {

    private val typeSerializers = GhostSpringTypeSerializers(registry = registry)

    override fun canRead(type: Type, contextClass: Class<*>?, mediaType: MediaType?): Boolean =
        canRead(mediaType) && typeSerializers.getYamlSerializer(type) != null

    override fun canRead(mediaType: MediaType?): Boolean {
        if (mediaType == null) {
            return false
        }
        return super.canRead(mediaType)
    }

    override fun canWrite(type: Type?, clazz: Class<*>, mediaType: MediaType?): Boolean =
        canWrite(mediaType) &&
            typeSerializers.getYamlSerializer(type ?: clazz) != null

    override fun canWrite(mediaType: MediaType?): Boolean {
        val isWildcardMediaType = mediaType == null || mediaType.isWildcardType || mediaType.isWildcardSubtype
        if (isWildcardMediaType) {
            return false
        }
        return super.canWrite(mediaType)
    }

    override fun read(
        type: Type,
        contextClass: Class<*>?,
        inputMessage: HttpInputMessage
    ): Any {
        val serializer = typeSerializers.getYamlSerializer(type)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} $type",
                inputMessage
            )
        return deserializeYaml(serializer = serializer, bytes = inputMessage.body.readBytes())
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val serializer = typeSerializers.getYamlSerializer(clazz)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} ${clazz.simpleName}",
                inputMessage
            )
        return deserializeYaml(serializer = serializer, bytes = inputMessage.body.readBytes())
    }

    override fun supports(clazz: Class<*>): Boolean =
        typeSerializers.getYamlSerializer(clazz) != null

    override fun writeInternal(t: Any, type: Type?, outputMessage: HttpOutputMessage) {
        val serializer = resolveWriteSerializer(t = t, type = type)
        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val bytes = ghostYamlEncodeToBytes(
            serializer = yamlSerializer,
            value = t
        )
        outputMessage.body.write(bytes)
        outputMessage.body.flush()
    }

    private fun deserializeYaml(serializer: GhostSerializer<Any>, bytes: ByteArray): Any {
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()
        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        return ghostYamlInternalUseFlatReader(bytes = bytes) { reader ->
            reader.applyOptions(isStrict = isStrict, isCoerce = isCoerce)
            yamlSerializer.deserialize(reader)
        }
    }

    private fun resolveWriteSerializer(t: Any, type: Type?): GhostSerializer<Any> {
        type?.let { declared ->
            typeSerializers.getYamlSerializer(declared)?.let { return it }
        }
        @Suppress("UNCHECKED_CAST")
        return typeSerializers.getYamlSerializer(t.javaClass)
            ?: run {
                val resolved = registry.getSerializer(t::class as KClass<Any>)
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
}
