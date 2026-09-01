package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractDecoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

private const val NDJSON_NEWLINE: Byte = '\n'.code.toByte()

/**
 * Reactive Decoder for Ghost Serialization. Resolves serializers from the full
 * [ResolvableType] so `List` / `Set` / `Map` element types are unwrapped (parity with
 * MVC / Retrofit / Ktor).
 */
class GhostReactiveDecoder : AbstractDecoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    GhostSpringMediaTypes.MIME_APPLICATION_X_NDJSON
) {
    override fun canDecode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canDecode(elementType, mimeType) &&
            GhostSpringTypeSerializers.getJsonSerializer(elementType) != null
    }

    override fun decode(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<Any> {
        val isNdJson = isNdJson(mimeType)
        return if (isNdJson) {
            decodeStreaming(inputStream, elementType)
        } else {
            decodeJoined(inputStream, elementType)
        }
    }

    override fun decodeToMono(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Mono<Any> {
        return decodeJoined(inputStream, elementType).next()
    }

    /**
     * NDJSON records don't align with [DataBuffer] boundaries, so this re-frames the byte
     * stream on `\n`, carrying partial lines across buffers and flushing the final
     * unterminated line at completion.
     */
    private fun decodeStreaming(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType
    ): Flux<Any> = Flux.defer {
        var carry = ByteArray(0)

        Flux.from(inputStream)
            .concatMap { buffer ->
                val bytes: ByteArray
                try {
                    bytes = ByteArray(buffer.readableByteCount())
                    buffer.read(bytes)
                } finally {
                    DataBufferUtils.release(buffer)
                }

                val combined = if (carry.isEmpty()) bytes else carry + bytes
                val lines = mutableListOf<ByteArray>()
                var lineStart = 0
                for (index in combined.indices) {
                    if (combined[index] == NDJSON_NEWLINE) {
                        if (index > lineStart) lines += combined.copyOfRange(lineStart, index)
                        lineStart = index + 1
                    }
                }
                carry = combined.copyOfRange(lineStart, combined.size)
                Flux.fromIterable(lines)
            }
            .concatWith(Flux.defer { if (carry.isEmpty()) Flux.empty() else Flux.just(carry) })
            .map { line -> deserializeBytes(line, elementType) }
    }

    private fun decodeJoined(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType
    ): Flux<Any> = DataBufferUtils
        .join(inputStream).flatMapMany { buffer ->
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
            val serializer = GhostSpringTypeSerializers.getJsonSerializer(elementType)
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} $elementType. ${Ghost.MISSING_ANN}"
                )
            if (serializer.isProto) {
                return ghostProtoInternalUseFlatReader(bytes) { reader ->
                    serializer.deserialize(reader)
                }
            }
            Ghost.deserialize(serializer, bytes)
        } catch (e: Exception) {
            throw GhostJsonException(
                "$DECODE_ERROR $elementType: ${e.message}"
            )
        }
    }

    private fun isNdJson(mimeType: MimeType?): Boolean {
        return mimeType?.subtype?.contains(GhostSpringMediaTypes.SUBTYPE_NDJSON_TOKEN) == true
    }

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive buffer for"
    }
}
