@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.applyOptions
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostCoerce
import com.ghost.serialization.annotations.GhostStrict
import com.ghost.serialization.GhostReflectTypeSerializers
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Retrofit [Converter.Factory] for Ghost JSON serialization
 * (`@GhostSerialization`).
 *
 * Honors `@GhostStrict` and `@GhostCoerce` on endpoint methods.
 * Unwraps `List<T>` / `Set<T>` / `Map<String, V>` when element serializers are registered.
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostConverterFactory private constructor(
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

        val isStrict = annotations.any { it is GhostStrict }
        val isCoerce = annotations.any { it is GhostCoerce }

        return Converter { body ->
            body.use {
                GhostRetrofitBuffers.readToScratch(it.byteStream()) { scratch, offset ->
                    ghostInternalUseFlatReader(
                        bytes = scratch, limit = offset
                    ) { reader ->
                        reader.applyOptions(isStrict = isStrict, isCoerce = isCoerce)
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

        fun create(registry: GhostRegistry = Ghost): GhostConverterFactory =
            GhostConverterFactory(registry = registry)
    }
}
