package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractEncoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux
import kotlin.reflect.KClass

private const val NDJSON_NEWLINE: Byte = '\n'.code.toByte()

/**
 * Reactive Encoder for Ghost Serialization.
 *
 * Serializes through the pooled `GhostJsonFlatWriter`
 * to a [ByteArray], then wraps the bytes in a [DataBuffer] via [DataBufferFactory].
 *
 * [canEncode] accepts types that have a serializer registered with [Ghost].
 */
class GhostReactiveEncoder : AbstractEncoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    GhostSpringMediaTypes.MIME_APPLICATION_X_NDJSON
) {
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        val clazz = elementType.toClass()
        return super.canEncode(elementType, mimeType) &&
                Ghost.getSerializer(clazz.kotlin) != null
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        val isNdJson = isNdJson(mimeType)

        return Flux.from(inputStream).map { value ->
            encodeValue(value, bufferFactory, isNdJson)
        }
    }

    private fun encodeValue(
        value: Any,
        bufferFactory: DataBufferFactory,
        isNdJson: Boolean
    ): DataBuffer {
        @Suppress("UNCHECKED_CAST")
        val kClass = value::class as KClass<Any>
        val serializer = Ghost.getSerializer(kClass)
            ?: throw IllegalArgumentException(
                "${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}"
            )

        val encoded = Ghost.encodeToBytes(serializer, value)

        if (!isNdJson) return bufferFactory.wrap(encoded)

        val bytes = ByteArray(encoded.size + 1)
        encoded.copyInto(bytes)
        bytes[encoded.size] = NDJSON_NEWLINE
        return bufferFactory.wrap(bytes)
    }

    private fun isNdJson(mimeType: MimeType?): Boolean {
        return mimeType?.subtype?.contains(GhostSpringMediaTypes.SUBTYPE_NDJSON_TOKEN) == true
    }
}
