@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.GhostReflectTypeSerializers
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.yaml.ghostYamlInternalUseFlatReader
import com.ghost.serialization.ghostYamlEncodeToBytes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Retrofit `Converter.Factory` for YAML-backed types
 * (`GhostYamlSerializer`).
 *
 * Resolves serializers through [registry] (defaults to the global [Ghost] singleton) rather than
 * calling [Ghost] directly, so tests can substitute a fake [GhostRegistry].
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostYamlConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostYamlConverterFactory private constructor(
    private val registry: GhostRegistry
) : Converter.Factory() {

    private val serializers = GhostReflectTypeSerializers(
        registry = registry,
        yamlOnly = true
    )

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        val serializer = serializers.get(type = type) ?: return null
        if (serializer !is GhostYamlSerializer<*>) return null

        val yamlSerializer = serializer as GhostYamlSerializer<Any>

        return Converter<Any, RequestBody> { value ->
            val bytes = ghostYamlEncodeToBytes(
            serializer = yamlSerializer,
            value = value
        )
            bytes.toRequestBody(MEDIA_TYPE)
        }
    }

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = serializers.get(type = type) ?: return null
        if (serializer !is GhostYamlSerializer<*>) return null

        val yamlSerializer = serializer as GhostYamlSerializer<Any>

        return Converter { body ->
            body.use {
                GhostRetrofitBuffers.readToScratch(it.byteStream()) { scratch, offset ->
                    val bytesToParse =
                        if (offset == scratch.size) scratch else scratch.copyOf(offset)
                    ghostYamlInternalUseFlatReader(bytes = bytesToParse) { reader ->
                        yamlSerializer.deserialize(reader)
                    }
                }
            }
        }
    }

    companion object {
        private val MEDIA_TYPE = GhostRetrofitMediaTypes.APPLICATION_YAML_UTF8.toMediaType()

        fun create(registry: GhostRegistry = Ghost): GhostYamlConverterFactory =
            GhostYamlConverterFactory(registry = registry)
    }
}
