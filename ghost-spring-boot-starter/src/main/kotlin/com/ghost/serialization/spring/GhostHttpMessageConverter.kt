package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractHttpMessageConverter
import java.util.concurrent.ConcurrentHashMap
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
    private val supportsCache = ConcurrentHashMap<Class<*>, Boolean>()
    private val serializerCache = ConcurrentHashMap<Class<*>, GhostSerializer<Any>>()

    override fun supports(clazz: Class<*>): Boolean {
        val cached = supportsCache[clazz]
        if (cached != null) return cached

        val result = if (isExcludedType(clazz)) {
            false
        } else {
            Ghost.getSerializer(clazz.kotlin) != null
        }
        supportsCache[clazz] = result
        return result
    }

    /**
     * Prevents Ghost from intercepting standard primitive and java.lang types.
     * This ensures Spring falls back to its default converters (e.g. StringHttpMessageConverter)
     * for endpoints returning text/plain, raw bytes, or basic values, preserving their correct HTTP formats.
     *
     * Extracted to a clean helper to facilitate JVM JIT compilation and inlining.
     */
    private fun isExcludedType(clazz: Class<*>): Boolean {
        return clazz == String::class.java ||
                clazz == ByteArray::class.java ||
                clazz.isPrimitive ||
                clazz.name.startsWith("java.lang.")
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val bytes = inputMessage.body.readBytes()
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()
        return com.ghost.serialization.ghostInternalUseFlatReader(bytes) { reader ->
            reader.strictMode = isStrict
            if (isCoerce) {
                reader.coerceStringsToNumbers = true
                reader.coerceBooleans = true
            }
            @Suppress("UNCHECKED_CAST")
            val targetClass = clazz as Class<Any>
            var serializer = serializerCache[targetClass]
            if (serializer == null) {
                serializer = Ghost.getSerializer(targetClass.kotlin as KClass<Any>) as GhostSerializer<Any>?
                    ?: Ghost.throwError("${Ghost.NOT_FOUND} ${targetClass.simpleName}")
                serializerCache[targetClass] = serializer
            }
            serializer.deserialize(reader)
        }
    }

    override fun writeInternal(t: Any, outputMessage: HttpOutputMessage) {
        val clazz = t.javaClass
        var serializer = serializerCache[clazz]
        if (serializer == null) {
            @Suppress("UNCHECKED_CAST")
            serializer = Ghost.getSerializer(clazz.kotlin as KClass<Any>) as GhostSerializer<Any>?
                ?: throw IllegalArgumentException(
                    "${Ghost.NOT_FOUND} ${clazz.simpleName}. ${Ghost.MISSING_ANN}"
                )
            serializerCache[clazz] = serializer
        }

        @Suppress("UNCHECKED_CAST")
        GhostStarterHelper.writeToStream(
            t,
            serializer,
            outputMessage.body
        )
        outputMessage.body.flush()
    }
}
