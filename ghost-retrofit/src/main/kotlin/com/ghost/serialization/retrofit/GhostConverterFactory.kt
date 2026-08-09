@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostCoerce
import com.ghost.serialization.annotations.GhostStrict
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalUseFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Retrofit [Converter.Factory] for Ghost JSON serialization
 * (`@GhostSerialization`).
 *
 * Honors `@GhostStrict` and `@GhostCoerce` on endpoint methods.
 * Unwraps `List<T>` / `Map<String, V>` when element serializers are registered.
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostConverterFactory private constructor() : Converter.Factory() {

    private val serializerCache =
        ConcurrentHashMap<Type, GhostSerializer<Any>>()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = getSerializerWithCache(type)
            ?: return null

        val isStrict = annotations.any { it is GhostStrict }
        val isCoerce = annotations.any { it is GhostCoerce }

        return Converter { body ->
            body.use {
                GhostRetrofitBuffers.readToScratch(it.byteStream()) { scratch, offset ->
                    ghostInternalUseFlatReader(
                        scratch, offset
                    ) { reader ->
                        reader.strictMode = isStrict
                        if (isCoerce) {
                            reader.coerceStringsToNumbers = true
                            reader.coerceBooleans = true
                        }
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

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = getSerializerWithCache(type)
            ?: return null

        return Converter<Any, RequestBody> { value ->
            Ghost.encodeToBytes(serializer, value)
                .toRequestBody(MEDIA_TYPE)
        }
    }

    private fun getSerializerWithCache(type: Type): GhostSerializer<Any>? {
        val cached = serializerCache[type]

        if (cached != null) {
            return cached
        }

        val serializer = getSerializerForType(type) ?: return null
        val existing = serializerCache.putIfAbsent(type, serializer)
        return existing ?: serializer
    }

    private fun getSerializerForType(type: Type): GhostSerializer<Any>? {
        if (type is Class<*>) {
            return Ghost
                .getSerializer(type.kotlin as KClass<Any>)
        }

        if (type is ParameterizedType) {
            val rawType = type.rawType as? Class<*> ?: return null

            if (List::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.firstOrNull()
                    ?: return null

                val itemSerializer = getSerializerWithCache(arg)
                    ?: return null

                return ListSerializer(itemSerializer) as GhostSerializer<Any>
            }

            if (Map::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.getOrNull(1)
                    ?: return null

                val valueSerializer = getSerializerWithCache(arg)
                    ?: return null

                return MapSerializer(valueSerializer) as GhostSerializer<Any>
            }

        }
        return null
    }

    companion object {
        private val MEDIA_TYPE = GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType()

        fun create(): GhostConverterFactory = GhostConverterFactory()
    }
}
