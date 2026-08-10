@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatWriter
import com.ghost.serialization.yaml.serializer.GhostYamlListSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlMapSerializer
import com.ghost.serialization.yaml.serializer.GhostYamlSetSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Retrofit `Converter.Factory` for YAML-backed types
 * (`GhostYamlSerializer`).
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostYamlConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostYamlConverterFactory private constructor() : Converter.Factory() {

    private val serializerCache = ConcurrentHashMap<Type, GhostSerializer<Any>>()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = getSerializerWithCache(type) ?: return null
        if (serializer !is GhostYamlSerializer<*>) return null

        val yamlSerializer = serializer as GhostYamlSerializer<Any>

        return Converter { body ->
            body.use {
                GhostRetrofitBuffers.readToScratch(it.byteStream()) { scratch, offset ->
                    val bytesToParse =
                        if (offset == scratch.size) scratch else scratch.copyOf(offset)
                    ghostYamlInternalUseFlatReader(bytesToParse) { reader ->
                        yamlSerializer.deserialize(reader)
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
        val serializer = getSerializerWithCache(type) ?: return null
        if (serializer !is GhostYamlSerializer<*>) return null

        val yamlSerializer = serializer as GhostYamlSerializer<Any>

        return Converter<Any, RequestBody> { value ->
            val bytes = ghostYamlInternalUseFlatWriter { writer ->
                yamlSerializer.serialize(writer, value)
                writer.buffer.toByteArray()
            }
            bytes.toRequestBody(MEDIA_TYPE)
        }
    }

    private fun getSerializerWithCache(type: Type): GhostSerializer<Any>? {
        val cached = serializerCache[type]
        if (cached != null) return cached
        val serializer = getSerializerForType(type) ?: return null
        val existing = serializerCache.putIfAbsent(type, serializer)
        return existing ?: serializer
    }

    private fun getSerializerForType(type: Type): GhostSerializer<Any>? {
        if (type is Class<*>) {
            return Ghost.getSerializer(type.kotlin as KClass<Any>)
        }

        if (type is java.lang.reflect.ParameterizedType) {
            val rawType = type.rawType as? Class<*> ?: return null

            if (List::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.firstOrNull() ?: return null
                val itemSerializer = getSerializerWithCache(arg) ?: return null
                if (itemSerializer !is GhostYamlSerializer<*>) return null
                return GhostYamlListSerializer(itemSerializer) as GhostSerializer<Any>
            }

            if (Set::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.firstOrNull() ?: return null
                val itemSerializer = getSerializerWithCache(arg) ?: return null
                if (itemSerializer !is GhostYamlSerializer<*>) return null
                return GhostYamlSetSerializer(itemSerializer) as GhostSerializer<Any>
            }

            if (Map::class.java.isAssignableFrom(rawType)) {
                val keyArg = type.actualTypeArguments.getOrNull(0) ?: return null
                if (!isStringMapKeyType(keyArg)) return null
                val valueArg = type.actualTypeArguments.getOrNull(1) ?: return null
                val valueSerializer = getSerializerWithCache(valueArg) ?: return null
                if (valueSerializer !is GhostYamlSerializer<*>) return null
                return GhostYamlMapSerializer(valueSerializer) as GhostSerializer<Any>
            }
        }
        return null
    }

    companion object {
        private val MEDIA_TYPE = GhostRetrofitMediaTypes.APPLICATION_YAML_UTF8.toMediaType()

        fun create(): GhostYamlConverterFactory = GhostYamlConverterFactory()
    }
}
