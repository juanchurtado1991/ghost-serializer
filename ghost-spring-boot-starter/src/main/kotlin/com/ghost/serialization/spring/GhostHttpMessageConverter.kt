package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import okio.buffer
import okio.sink
import okio.source
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import kotlin.reflect.KClass

/**
 * Spring [org.springframework.http.converter.HttpMessageConverter]
 * implementation that uses Ghost Serialization.
 * Optimized for Zero-Copy by using Okio directly from the stream.
 */
class GhostHttpMessageConverter : AbstractHttpMessageConverter<Any>(
    MediaType.APPLICATION_JSON,
    MediaType("application", "*+json")
) {
    override fun supports(clazz: Class<*>): Boolean {
        return Ghost.getSerializer(clazz.kotlin) != null
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val source = inputMessage.body.source().buffer()
        return Ghost.decodeFromSource(source, clazz.kotlin)
    }

    override fun writeInternal(t: Any, outputMessage: HttpOutputMessage) {
        val sink = outputMessage.body.sink().buffer()
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Ghost.encodeToSink(sink, t, t::class as KClass<Any>)
            sink.flush()
        }
    }
}
