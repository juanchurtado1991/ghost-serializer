@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.GhostReflectTypeSerializers
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type


/**
 * Retrofit `Converter.Factory` for proto3 JSON mapping (`@GhostProtoSerialization`).
 *
 * Differs from [GhostConverterFactory] only on the read path: bodies are parsed through
 * `GhostProtoJsonFlatReader`, which accepts quoted-or-bare int64/uint64, lenient int32, and
 * quoted `"NaN"`/`"Infinity"` per proto3 JSON rules — needed to round-trip payloads from real
 * protobuf/JSON libraries. Encoding reuses `Ghost.encodeToBytes`, since proto3 wire correctness
 * is generated into the serializer's own `serialize()`.
 *
 * Also unwraps `List<T>`/`Set<T>`/`Map<String, V>` bodies when the element/value serializer is
 * registered, same as [GhostConverterFactory].
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostProtoConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostProtoConverterFactory private constructor(
    private val registry: GhostRegistry
) : Converter.Factory() {

    private val serializers = GhostReflectTypeSerializers(
        registry = registry
    )

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = serializers.get(type = type)
            ?: return null

        return Converter<Any, RequestBody> { value ->
            Ghost.encodeToBytes(serializer = serializer, value = value)
                .toRequestBody(MEDIA_TYPE)
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = serializers.get(type = type)
            ?: return null

        return Converter { body ->
            body.use {
                GhostRetrofitBuffers.readToScratch(it.byteStream()) { scratch, offset ->
                    ghostProtoInternalUseFlatReader(bytes = scratch, length = offset) { reader ->
                        if (reader.isNextNullValue()) {
                            reader.consumeNull()
                            null
                        } else {
                            serializer.deserialize(reader)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val MEDIA_TYPE = GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType()

        fun create(registry: GhostRegistry = Ghost): GhostProtoConverterFactory =
            GhostProtoConverterFactory(registry = registry)
    }
}
