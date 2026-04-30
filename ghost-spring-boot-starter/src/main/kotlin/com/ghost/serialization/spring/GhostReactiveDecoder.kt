package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.exception.GhostJsonException
import okio.buffer
import okio.source
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractDecoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Reactive Decoder for Ghost Serialization.
 * Supports streaming of objects from [DataBuffer]s with Zero-Copy.
 */
@OptIn(InternalGhostApi::class)
class GhostReactiveDecoder : AbstractDecoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    MimeType("application", "x-ndjson")
) {
    override fun canDecode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        val clazz = elementType.toClass()
        return super.canDecode(elementType, mimeType) &&
                (clazz.isAnnotationPresent(GhostSerialization::class.java) ||
                        Ghost.getSerializer(clazz.kotlin) != null)
    }

    override fun decode(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<Any> {
        val clazz = elementType.toClass()
        val isNdJson = isNdJson(mimeType)

        return if (isNdJson) {
            decodeStreaming(inputStream, clazz)
        } else {
            decodeJoined(inputStream, clazz)
        }
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

    private fun decodeStreaming(
        inputStream: Publisher<DataBuffer>,
        clazz: Class<*>
    ): Flux<Any> = Flux.from(inputStream).map { buffer ->
        try {
            deserializeBuffer(buffer, clazz)
        } finally {
            DataBufferUtils.release(buffer)
        }
    }

    private fun decodeJoined(
        inputStream: Publisher<DataBuffer>,
        clazz: Class<*>
    ): Flux<Any> = DataBufferUtils
        .join(inputStream).flatMapMany { buffer ->
            try {
                Flux.just(deserializeBuffer(buffer, clazz))
            } finally {
                DataBufferUtils.release(buffer)
            }
        }

    private fun deserializeBuffer(
        buffer: DataBuffer,
        clazz: Class<*>
    ): Any {
        val inputStream = buffer.asInputStream()
        val source = inputStream.source().buffer()

        return try {
            Ghost.decodeFromSource(source, clazz.kotlin)
        } catch (e: Exception) {
            throw GhostJsonException(
                "$DECODE_ERROR ${clazz.simpleName}: ${e.message}"
            )
        }
    }

    private fun isNdJson(mimeType: MimeType?): Boolean {
        return mimeType?.toString()?.contains("x-ndjson") == true
    }

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive buffer for"
    }
}
