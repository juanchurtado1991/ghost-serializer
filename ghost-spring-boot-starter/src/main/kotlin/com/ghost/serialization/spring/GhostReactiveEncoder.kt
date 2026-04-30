package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.annotations.GhostSerialization
import okio.buffer
import okio.sink
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.codec.AbstractEncoder
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.util.MimeType
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Flux

/**
 * Reactive Encoder for Ghost Serialization.
 */
class GhostReactiveEncoder : AbstractEncoder<Any>(
    MimeTypeUtils.APPLICATION_JSON,
    MimeType("application", "x-ndjson")
) {
    override fun canEncode(elementType: ResolvableType, mimeType: MimeType?): Boolean {
        val clazz = elementType.toClass()
        return super.canEncode(elementType, mimeType) &&
                (clazz.isAnnotationPresent(GhostSerialization::class.java) || 
                 Ghost.getSerializer(clazz.kotlin) != null)
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
        value: Any, bufferFactory: DataBufferFactory,
        isNdJson: Boolean)
    : DataBuffer {
        val buffer = bufferFactory
            .allocateBuffer(OPTIMAL_BUFFER_CAPACITY)

        val outputStream = buffer.asOutputStream()
        val sink = outputStream.sink().buffer()
        
        try {
            Ghost.encodeToSink(sink, value)
            if (isNdJson) {
                sink.writeByte('\n'.code)
            }
            sink.flush()
        } catch (e: Exception) {
            DataBufferUtils.release(buffer)
            throw e
        }
        
        return buffer
    }

    private fun isNdJson(mimeType: MimeType?): Boolean {
        return mimeType?.toString()?.contains("x-ndjson") == true
    }

    companion object {
        private const val OPTIMAL_BUFFER_CAPACITY = 4096
    }
}
