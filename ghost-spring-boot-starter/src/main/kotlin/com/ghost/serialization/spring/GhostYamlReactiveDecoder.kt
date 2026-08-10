package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractDecoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class GhostYamlReactiveDecoder : AbstractDecoder<Any>(
    GhostSpringMediaTypes.MIME_APPLICATION_YAML,
    GhostSpringMediaTypes.MIME_APPLICATION_X_YAML,
    GhostSpringMediaTypes.MIME_TEXT_YAML,
) {
    override fun canDecode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canDecode(elementType, mimeType) &&
            GhostSpringTypeSerializers.getYamlSerializer(elementType) != null
    }

    override fun decode(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<Any> = decodeJoined(inputStream, elementType)

    override fun decodeToMono(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Mono<Any> = decodeJoined(inputStream, elementType).next()

    private fun decodeJoined(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType
    ): Flux<Any> = DataBufferUtils.join(inputStream).flatMapMany { buffer ->
        try {
            val bytes = ByteArray(buffer.readableByteCount())
            buffer.read(bytes)
            Flux.just(deserializeBytes(bytes, elementType))
        } finally {
            DataBufferUtils.release(buffer)
        }
    }

    private fun deserializeBytes(
        bytes: ByteArray,
        elementType: ResolvableType
    ): Any {
        return try {
            val serializer = GhostSpringTypeSerializers.getYamlSerializer(elementType)
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} $elementType. ${Ghost.MISSING_ANN}"
                )
            @Suppress("UNCHECKED_CAST")
            val yamlSerializer = serializer as GhostYamlSerializer<Any>
            ghostYamlInternalUseFlatReader(bytes) { reader ->
                yamlSerializer.deserialize(reader)
            }
        } catch (e: Exception) {
            throw GhostJsonException("$DECODE_ERROR $elementType: ${e.message}")
        }
    }

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive YAML buffer for"
    }
}
