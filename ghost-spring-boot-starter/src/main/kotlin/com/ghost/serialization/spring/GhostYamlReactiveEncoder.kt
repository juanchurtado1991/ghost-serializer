package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.ghostYamlEncodeToBytes
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractEncoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import kotlin.reflect.KClass

/**
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 */
class GhostYamlReactiveEncoder(
    private val registry: GhostRegistry = Ghost
) : AbstractEncoder<Any>(
    GhostSpringMediaTypes.MIME_APPLICATION_YAML,
    GhostSpringMediaTypes.MIME_APPLICATION_X_YAML,
    GhostSpringMediaTypes.MIME_TEXT_YAML,
) {

    private val typeSerializers = GhostSpringTypeSerializers(registry = registry)
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canEncode(elementType, mimeType) &&
            typeSerializers.getYamlSerializer(elementType) != null
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        val declaredSerializer = typeSerializers.getYamlSerializer(elementType)
        return Flux.from(inputStream).map { value ->
            encodeValue(value, bufferFactory, declaredSerializer)
        }
    }

    private fun encodeValue(
        value: Any,
        bufferFactory: DataBufferFactory,
        declaredSerializer: GhostSerializer<Any>?
    ): DataBuffer {
        val serializer = declaredSerializer
            ?: run {
                @Suppress("UNCHECKED_CAST")
                registry.getSerializer(value::class as KClass<Any>)
            }
            ?: throw IllegalArgumentException(
                "${Ghost.NOT_FOUND} ${value::class.simpleName}. ${Ghost.MISSING_ANN}"
            )
        if (serializer !is GhostYamlSerializer<*>) {
            throw IllegalArgumentException(
                "Serializer for ${value::class.simpleName} does not implement GhostYamlSerializer"
            )
        }

        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val encoded = ghostYamlEncodeToBytes(
            serializer = yamlSerializer,
            value = value
        )
        return bufferFactory.wrap(encoded)
    }
}
