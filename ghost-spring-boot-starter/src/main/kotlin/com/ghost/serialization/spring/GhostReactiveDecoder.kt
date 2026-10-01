package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
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

/**
 * Reactive Decoder for Ghost Serialization. Resolves serializers from the full
 * [ResolvableType] so `List` / `Set` / `Map` element types are unwrapped (parity with
 * MVC / Retrofit / Ktor).
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 */
class GhostReactiveDecoder(
    private val registry: GhostRegistry = Ghost
) : AbstractDecoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    GhostSpringMediaTypes.MIME_APPLICATION_X_NDJSON
) {

    private val typeSerializers = GhostSpringTypeSerializers(registry = registry)
    override fun canDecode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canDecode(elementType, mimeType) &&
            typeSerializers.getJsonSerializer(elementType) != null
    }

    override fun decode(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<Any> {
        val isNdJson = GhostSpringMediaTypes.isNdJson(mimeType = mimeType)
        return if (isNdJson) {
            decodeStreaming(inputStream = inputStream, elementType = elementType)
        } else {
            decodeJoined(inputStream = inputStream, elementType = elementType)
        }
    }

    override fun decodeToMono(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Mono<Any> {
        return decodeJoined(inputStream = inputStream, elementType = elementType).next()
    }

    private fun decodeJoined(
        inputStream: Publisher<DataBuffer>,
        elementType: ResolvableType
    ): Flux<Any> = DataBufferUtils
        .join(inputStream).flatMapMany { buffer ->
            Flux.just(deserializeBytes(bytes = buffer.consumeToByteArray(), elementType = elementType))
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
                val bytes = buffer.consumeToByteArray()

                val combined = if (carry.isEmpty()) bytes else carry + bytes
                val lines = mutableListOf<ByteArray>()
                var lineStart = 0
                for (index in combined.indices) {
                    if (combined[index] == GhostSpringMediaTypes.NDJSON_NEWLINE) {
                        if (index > lineStart) lines += combined.copyOfRange(lineStart, index)
                        lineStart = index + 1
                    }
                }
                carry = combined.copyOfRange(lineStart, combined.size)
                Flux.fromIterable(lines)
            }
            .concatWith(Flux.defer { if (carry.isEmpty()) Flux.empty() else Flux.just(carry) })
            .map { line -> deserializeBytes(bytes = line, elementType = elementType) }
    }

    private fun deserializeBytes(
        bytes: ByteArray,
        elementType: ResolvableType
    ): Any {
        return try {
            val serializer = typeSerializers.getJsonSerializer(elementType)
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} $elementType. ${Ghost.MISSING_ANN}"
                )
            if (serializer.isProto) {
                return ghostProtoInternalUseFlatReader(bytes = bytes) { reader ->
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

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive buffer for"
    }
}
