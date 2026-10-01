package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
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

/**
 * Reactive Encoder for Ghost Serialization. Resolves serializers from the full
 * [ResolvableType] so collection bodies use the declared generic serializer, not
 * `value::class`.
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 */
class GhostReactiveEncoder(
    private val registry: GhostRegistry = Ghost
) : AbstractEncoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    GhostSpringMediaTypes.MIME_APPLICATION_X_NDJSON
) {

    private val typeSerializers = GhostSpringTypeSerializers(registry = registry)
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        return super.canEncode(elementType, mimeType) &&
            typeSerializers.getJsonSerializer(elementType) != null
    }

    override fun encode(
        inputStream: Publisher<out Any>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: MutableMap<String, Any>?
    ): Flux<DataBuffer> {
        val isNdJson = GhostSpringMediaTypes.isNdJson(mimeType = mimeType)
        val declaredSerializer = typeSerializers.getJsonSerializer(elementType)

        return Flux.from(inputStream).map { value ->
            encodeValue(
                value = value,
                bufferFactory = bufferFactory,
                isNdJson = isNdJson,
                declaredSerializer = declaredSerializer
            )
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
                registry.getSerializer(value::class as KClass<Any>)
            }
            ?: throw IllegalArgumentException(
                "${Ghost.NOT_FOUND} ${value::class.simpleName}. ${Ghost.MISSING_ANN}"
            )

        val encoded = Ghost.encodeToBytes(serializer = serializer, value = value)

        if (!isNdJson) return bufferFactory.wrap(encoded)

        val bytes = ByteArray(encoded.size + 1)
        encoded.copyInto(bytes)
        bytes[encoded.size] = GhostSpringMediaTypes.NDJSON_NEWLINE
        return bufferFactory.wrap(bytes)
    }
}
