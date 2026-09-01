package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import org.springframework.http.HttpInputMessage
import org.springframework.http.HttpOutputMessage
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractGenericHttpMessageConverter
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.http.converter.HttpMessageNotWritableException
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Spring `HttpMessageConverter` implementation that uses Ghost Serialization.
 *
 * Resolves `List` / `Set` / `Map` generics via [GhostSpringTypeSerializers] (parity with
 * Retrofit / Ktor). Read/write use the declared [Type], not only the erased [Class].
 *
 * **Read path:** Extracts the request body as a [ByteArray] and feeds it
 * directly to the pooled flat reader, avoiding intermediate Okio wrappers.
 *
 * **Write path:** Serializes through the pooled in-memory `GhostJsonWriter`
 * and writes the resulting [ByteArray] in a single bulk call.
 */
class GhostHttpMessageConverter : AbstractGenericHttpMessageConverter<Any>(
    MediaType.APPLICATION_JSON,
    GhostSpringMediaTypes.APPLICATION_JSON_SUFFIX
) {

    override fun supports(clazz: Class<*>): Boolean =
        GhostSpringTypeSerializers.getJsonSerializer(clazz) != null

    override fun canRead(type: Type, contextClass: Class<*>?, mediaType: MediaType?): Boolean =
        canRead(mediaType) && GhostSpringTypeSerializers.getJsonSerializer(type) != null

    override fun canWrite(type: Type?, clazz: Class<*>, mediaType: MediaType?): Boolean =
        canWrite(mediaType) &&
            GhostSpringTypeSerializers.getJsonSerializer(type ?: clazz) != null

    override fun read(
        type: Type,
        contextClass: Class<*>?,
        inputMessage: HttpInputMessage
    ): Any {
        val serializer = GhostSpringTypeSerializers.getJsonSerializer(type)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} $type",
                inputMessage
            )
        return deserialize(serializer, inputMessage.body.readBytes())
    }

    override fun readInternal(clazz: Class<out Any>, inputMessage: HttpInputMessage): Any {
        val serializer = GhostSpringTypeSerializers.getJsonSerializer(clazz)
            ?: throw HttpMessageNotReadableException(
                "${Ghost.NOT_FOUND} ${clazz.simpleName}",
                inputMessage
            )
        return deserialize(serializer, inputMessage.body.readBytes())
    }

    override fun writeInternal(t: Any, type: Type?, outputMessage: HttpOutputMessage) {
        val serializer = resolveWriteSerializer(t, type)
        GhostStarterHelper.writeToStream(t, serializer, outputMessage.body)
        outputMessage.body.flush()
    }

    private fun resolveWriteSerializer(t: Any, type: Type?): GhostSerializer<Any> {
        type?.let { declared ->
            GhostSpringTypeSerializers.getJsonSerializer(declared)?.let { return it }
        }
        @Suppress("UNCHECKED_CAST")
        return GhostSpringTypeSerializers.getJsonSerializer(t.javaClass)
            ?: Ghost.getSerializer(t::class as KClass<Any>)
            ?: throw HttpMessageNotWritableException(
                "${Ghost.NOT_FOUND} ${t.javaClass.simpleName}. ${Ghost.MISSING_ANN}"
            )
    }

    private fun deserialize(serializer: GhostSerializer<Any>, bytes: ByteArray): Any {
        val isStrict = GhostSpringConfig.strict.get()
        val isCoerce = GhostSpringConfig.coerce.get()

        // @GhostProtoSerialization classes need GhostProtoJsonFlatReader's proto3 leniency.
        // The annotation is BINARY-retained; `serializer.isProto` is the runtime signal.
        if (serializer.isProto) {
            return ghostProtoInternalUseFlatReader(bytes) { reader ->
                reader.strictMode = isStrict
                if (isCoerce) {
                    reader.coerceStringsToNumbers = true
                    reader.coerceBooleans = true
                }
                serializer.deserialize(reader)
            }
        }

        return ghostInternalUseFlatReader(bytes) { reader ->
            reader.strictMode = isStrict
            if (isCoerce) {
                reader.coerceStringsToNumbers = true
                reader.coerceBooleans = true
            }
            serializer.deserialize(reader)
        }
    }
}
