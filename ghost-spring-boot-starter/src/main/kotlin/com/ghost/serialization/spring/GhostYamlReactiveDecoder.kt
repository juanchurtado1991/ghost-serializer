package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
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
import kotlin.reflect.KClass

class GhostYamlReactiveDecoder : AbstractDecoder<Any>(
    MimeType("application", "yaml"),
    MimeType("application", "x-yaml"),
    MimeType("text", "yaml"),
) {
    override fun canDecode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        val clazz = elementType.toClass()
        val serializer = Ghost.getSerializer(clazz.kotlin)
        return super.canDecode(elementType, mimeType) && serializer is GhostYamlSerializer<*>
    }

    override fun decode(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<Any> {
        val clazz = elementType.toClass()
        return decodeJoined(inputStream, clazz)
    }

    override fun decodeToMono(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Mono<Any> {
        val clazz = elementType.toClass()
        return decodeJoined(inputStream, clazz).next()
    }

    private fun decodeJoined(
        inputStream: Publisher<DataBuffer>,
        clazz: Class<*>
    ): Flux<Any> = DataBufferUtils.join(inputStream).flatMapMany { buffer ->
        try {
            Flux.just(deserializeBuffer(buffer, clazz))
        } finally {
            DataBufferUtils.release(buffer)
        }
    }

    private fun deserializeBuffer(buffer: DataBuffer, clazz: Class<*>): Any {
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        return deserializeBytes(bytes, clazz)
    }

    private fun deserializeBytes(bytes: ByteArray, clazz: Class<*>): Any {
        return try {
            val serializer = Ghost.getSerializer(clazz.kotlin as KClass<Any>)
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} ${clazz.simpleName}. ${Ghost.MISSING_ANN}"
                )
            if (serializer !is GhostYamlSerializer<*>) {
                throw IllegalArgumentException(
                    "Serializer for ${clazz.simpleName} does not implement GhostYamlSerializer"
                )
            }

            @Suppress("UNCHECKED_CAST")
            val yamlSerializer = serializer as GhostYamlSerializer<Any>
            ghostYamlInternalUseFlatReader(bytes) { reader ->
                yamlSerializer.deserialize(reader)
            }
        } catch (e: Exception) {
            throw GhostJsonException("$DECODE_ERROR ${clazz.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive YAML buffer for"
    }
}
