package com.ghost.serialization.retrofit

import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.serializers.IntSerializer
import com.ghost.serialization.serializers.LongSerializer
import com.ghost.serialization.serializers.BooleanSerializer
import com.ghost.serialization.serializers.DoubleSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okio.Buffer
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Robust-grade Retrofit Converter Factory for GhostSerialization.
 * Provides zero-overhead streaming serialization directly from Okio sources/sinks.
 */
class GhostConverterFactory private constructor(
    private val registry: GhostRegistry
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = resolveSerializer(type) ?: return null

        return Converter<ResponseBody, Any> { value ->
            value.use { body ->
                serializer.deserialize(body.source())
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = resolveSerializer(type) ?: return null

        return Converter<Any, RequestBody> { value ->
            val buffer = Buffer()
            serializer.serialize(buffer, value)
            buffer.readByteString().toRequestBody(MEDIA_TYPE)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveSerializer(type: Type): GhostSerializer<Any>? {
        return when (type) {
            is Class<*> -> resolveClassSerializer(type)
            is ParameterizedType -> resolveParameterizedSerializer(type)
            else -> null
        } as? GhostSerializer<Any>
    }

    private fun resolveClassSerializer(clazz: Class<*>): GhostSerializer<*>? {
        val primitive = PRIMITIVES[clazz]
        if (primitive != null) return primitive

        return registry.getSerializer(clazz.run { kotlin as KClass<Any> })
    }

    private fun resolveParameterizedSerializer(type: ParameterizedType): GhostSerializer<*>? {
        val rawType = type.rawType as? Class<*> ?: return null
        val typeArgs = type.actualTypeArguments

        return when {
            List::class.java.isAssignableFrom(rawType) -> {
                val itemType = typeArgs.getOrNull(0) ?: return null
                val itemSerializer = resolveSerializer(itemType) ?: return null
                ListSerializer(itemSerializer)
            }
            Map::class.java.isAssignableFrom(rawType) -> {
                val valueType = typeArgs.getOrNull(1) ?: return null
                val valueSerializer = resolveSerializer(valueType) ?: return null
                MapSerializer(valueSerializer)
            }
            else -> null
        }
    }

    companion object {
        private const val STR_MEDIA_TYPE = "application/json; charset=UTF-8"
        private val MEDIA_TYPE = STR_MEDIA_TYPE.toMediaType()

        private val PRIMITIVES = mapOf<Class<*>, GhostSerializer<*>>(
            String::class.java to StringSerializer,
            Int::class.java to IntSerializer,
            java.lang.Integer::class.java to IntSerializer,
            Long::class.java to LongSerializer,
            java.lang.Long::class.java to LongSerializer,
            Boolean::class.java to BooleanSerializer,
            java.lang.Boolean::class.java to BooleanSerializer,
            Double::class.java to DoubleSerializer,
            java.lang.Double::class.java to DoubleSerializer
        )

        fun create(registry: GhostRegistry): GhostConverterFactory {
            return GhostConverterFactory(registry)
        }
    }
}
