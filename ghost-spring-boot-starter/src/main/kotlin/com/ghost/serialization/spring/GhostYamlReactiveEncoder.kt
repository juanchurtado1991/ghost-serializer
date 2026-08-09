package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
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
        val clazz = elementType.toClass()
        val serializer = Ghost.getSerializer(clazz.kotlin)
        return super.canEncode(elementType, mimeType) && serializer is GhostYamlSerializer<*>
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        return Flux.from(inputStream).map { value ->
            encodeValue(value, bufferFactory)
        }
    }

    private fun encodeValue(value: Any, bufferFactory: DataBufferFactory): DataBuffer {
        @Suppress("UNCHECKED_CAST")
        val kClass = value::class as KClass<Any>
        val serializer = Ghost.getSerializer(kClass)
            ?: throw IllegalArgumentException(
                "${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}"
            )
        if (serializer !is GhostYamlSerializer<*>) {
            throw IllegalArgumentException(
                "Serializer for ${kClass.simpleName} does not implement GhostYamlSerializer"
            )
        }

        @Suppress("UNCHECKED_CAST")
        val yamlSerializer = serializer as GhostYamlSerializer<Any>
        val encoded = ghostYamlInternalUseFlatWriter { writer ->
            yamlSerializer.serialize(writer, value)
            writer.buffer.toByteArray()
        }
        return bufferFactory.wrap(encoded)
    }
}
