package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.ghostInternalEncodeAndDrainTo
import com.ghost.serialization.ghostInternalUseSource
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Retrofit Converter Factory for Ghost Serialization.
 */
@OptIn(InternalGhostApi::class)
class GhostConverterFactory private constructor() : Converter.Factory() {

    private val serializerCache = ConcurrentHashMap<Type, GhostSerializer<Any>>()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = getSerializerWithCache(type) ?: return null

        return Converter { body ->
            body.use {
                ghostInternalUseSource(it.source()) { reader ->
                    serializer.deserialize(reader)
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
        val serializer = getSerializerWithCache(type) ?: return null

        return Converter<Any, RequestBody> { value ->
            val buffer = Buffer()
            ghostInternalEncodeAndDrainTo(buffer) { writer ->
                serializer.serialize(writer, value)
            }
            // Use readByteArray() to avoid ByteString overhead if possible
            buffer.readByteArray().toRequestBody(MEDIA_TYPE)
        }
    }

    private fun getSerializerWithCache(type: Type): GhostSerializer<Any>? {
        return serializerCache.getOrPut(type) {
            getSerializerForType(type) ?: return null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getSerializerForType(type: Type): GhostSerializer<Any>? {
        if (type is Class<*>) {
            return Ghost.getSerializer(type.kotlin as KClass<Any>)
        }
        if (type is ParameterizedType) {
            val rawType = type.rawType as? Class<*> ?: return null
            if (List::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.firstOrNull() ?: return null
                val itemSerializer = getSerializerForType(arg) ?: return null
                return ListSerializer(itemSerializer) as GhostSerializer<Any>
            }
            if (Map::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.getOrNull(1) ?: return null
                val valueSerializer = getSerializerForType(arg) ?: return null
                return MapSerializer(valueSerializer) as GhostSerializer<Any>
            }
        }
        return null
    }

    companion object {
        private const val STR_MEDIA_TYPE = "application/json; charset=UTF-8"
        private val MEDIA_TYPE = STR_MEDIA_TYPE.toMediaType()

        fun create(): GhostConverterFactory = GhostConverterFactory()
    }
}
