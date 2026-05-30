package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.ghostInternalEncodeWithWriter
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import kotlin.reflect.KClass

/**
 * Spring [org.springframework.http.converter.HttpMessageConverter]
 * implementation that uses Ghost Serialization.
 *
 * **Read path:** Extracts the request body as a [ByteArray] and feeds it
 * directly to the pooled [com.ghost.serialization.parser.GhostJsonReader],
 * avoiding intermediate Okio wrappers.
 *
 * **Write path:** Serializes through the pooled monomorphic
 * [com.ghost.serialization.writer.GhostJsonFlatWriter] and writes the
 * resulting [ByteArray] in a single bulk call to the output stream,
 * bypassing Okio sink wrapping entirely.
 */
class GhostHttpMessageConverter : AbstractHttpMessageConverter<Any>(
    MediaType.APPLICATION_JSON,
    MediaType("application", "*+json")
) {
    override fun supports(clazz: Class<*>): Boolean {
        return Ghost.getSerializer(clazz.kotlin) != null
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val bytes = inputMessage.body.readBytes()
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()
        @Suppress("UNCHECKED_CAST")
        return com.ghost.serialization.ghostInternalUseFlatReader(bytes) { reader ->
            reader.strictMode = isStrict
            if (isCoerce) {
                reader.coerceStringsToNumbers = true
                reader.coerceBooleans = true
            }
            val serializer = Ghost.getSerializer(clazz.kotlin as KClass<Any>)
                ?: Ghost.throwError("${Ghost.NOT_FOUND} ${clazz.simpleName}")
            serializer.deserialize(reader)
        }
    }

    override fun writeInternal(t: Any, outputMessage: HttpOutputMessage) {
        @Suppress("UNCHECKED_CAST")
        val kClass = t::class as KClass<Any>
        val serializer = Ghost.getSerializer(kClass)
            ?: throw IllegalArgumentException(
                "${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}"
            )

        val bytes = ghostInternalEncodeWithWriter { writer ->
            serializer.serialize(writer, t)
        }
        outputMessage.body.write(bytes)
        outputMessage.body.flush()
    }
}
