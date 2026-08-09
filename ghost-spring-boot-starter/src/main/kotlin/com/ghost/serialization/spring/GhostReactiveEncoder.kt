package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
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
 * [canEncode] / encode resolve serializers from the full [ResolvableType] so
 * collection bodies use the declared generic serializer, not `value::class`.
 */
class GhostReactiveEncoder : AbstractEncoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    GhostSpringMediaTypes.MIME_APPLICATION_X_NDJSON
) {
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canEncode(elementType, mimeType) &&
            GhostSpringTypeSerializers.getJsonSerializer(elementType) != null
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        val isNdJson = isNdJson(mimeType)
        val declaredSerializer = GhostSpringTypeSerializers.getJsonSerializer(elementType)

        return Flux.from(inputStream).map { value ->
            encodeValue(value, bufferFactory, isNdJson, declaredSerializer)
        }
    }

    private fun encodeValue(
        value: Any,
        bufferFactory: DataBufferFactory,
        isNdJson: Boolean,
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
