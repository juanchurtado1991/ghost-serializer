package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.exception.GhostJsonException
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractDecoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.reflect.KClass

private const val NDJSON_NEWLINE: Byte = '\n'.code.toByte()

/**
 * Reactive Decoder for Ghost Serialization.
 *
 * Extracts the raw [ByteArray] from each [DataBuffer] and feeds it directly
 * to the pooled [com.ghost.serialization.parser.GhostJsonReader], avoiding
 * intermediate Okio/InputStream wrappers entirely.
 */
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

    /**
     * NDJSON records aren't guaranteed to arrive one-per-[DataBuffer] — a small multi-line
     * request body will typically arrive as a single network buffer, and a single record can
     * just as easily be split across two. This re-frames the raw buffer stream on `\n` before
     * decoding each line, carrying any trailing partial line over to the next buffer and
     * flushing a final unterminated line at stream completion.
     */
    private fun decodeStreaming(
        inputStream: Publisher<DataBuffer>,
        clazz: Class<*>
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
            .map { line -> deserializeBytes(line, clazz) }
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
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        return deserializeBytes(bytes, clazz)
    }

    private fun deserializeBytes(
        bytes: ByteArray,
        clazz: Class<*>
    ): Any {
        return try {
            val serializer = Ghost.getSerializer(clazz.kotlin as KClass<Any>)
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} ${clazz.simpleName}. ${Ghost.MISSING_ANN}"
                )
            Ghost.deserialize(serializer, bytes)
        } catch (e: Exception) {
            throw GhostJsonException(
                "$DECODE_ERROR ${clazz.simpleName}: ${e.message}"
            )
        }
    }

    private fun isNdJson(mimeType: MimeType?): Boolean {
        return mimeType?.subtype?.contains("ndjson") == true
    }

    companion object {
        private const val DECODE_ERROR = "Failed to decode reactive buffer for"
    }
}
