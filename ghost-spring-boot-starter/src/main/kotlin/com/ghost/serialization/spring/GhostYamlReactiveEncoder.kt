package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractEncoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import kotlin.reflect.KClass

class GhostYamlReactiveEncoder : AbstractEncoder<Any>(
    GhostSpringMediaTypes.MIME_APPLICATION_YAML,
    GhostSpringMediaTypes.MIME_APPLICATION_X_YAML,
    GhostSpringMediaTypes.MIME_TEXT_YAML,
) {
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canEncode(elementType, mimeType) &&
            GhostSpringTypeSerializers.getYamlSerializer(elementType) != null
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        val declaredSerializer = GhostSpringTypeSerializers.getYamlSerializer(elementType)
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
                Ghost.getSerializer(value::class as KClass<Any>)
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
        val encoded = ghostYamlInternalUseFlatWriter { writer, buffer ->
            yamlSerializer.serialize(writer, value)
            buffer.toByteArray()
        }
        return bufferFactory.wrap(encoded)
    }
}
