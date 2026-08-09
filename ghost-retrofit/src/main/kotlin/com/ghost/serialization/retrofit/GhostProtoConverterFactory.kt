@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.acquireScratchBuffer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import com.ghost.serialization.releaseScratchBuffer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
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
 * Retrofit `Converter.Factory` for proto3 JSON mapping
 * (`@GhostProtoSerialization`).
 *
 * Differs from [GhostConverterFactory] only on the read path: response bodies are parsed
 * through `GhostProtoJsonFlatReader`, which additionally
 * accepts quoted-or-bare int64/uint64, lenient int32 (rejects fractional values), and quoted
 * `"NaN"`/`"Infinity"` literals per proto3 JSON rules — required for round-tripping payloads
 * produced by real protobuf/JSON libraries. Encoding (`requestBodyConverter`) reuses
 * `Ghost.encodeToBytes` since proto3 wire correctness (int64 quoting,
 * Base64 `bytes`, default-value omission) is generated directly into the
 * `@GhostProtoSerialization`
 * serializer's own `serialize()` method.
 *
 * Also unwraps `List<T>`/`Map<String, V>` request/response bodies when the element/value
 * serializer is registered — same pattern as [GhostConverterFactory], still using
 * `GhostProtoJsonFlatReader` on the read path.
 *
 * ```kotlin
 * Retrofit.Builder()
 *     .baseUrl(baseUrl)
 *     .addConverterFactory(GhostProtoConverterFactory.create())
 *     .build()
 * ```
 */
@OptIn(InternalGhostApi::class)
class GhostProtoConverterFactory private constructor() : Converter.Factory() {

    private val serializerCache = ConcurrentHashMap<Type, GhostSerializer<Any>>()

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        val serializer = getSerializerWithCache(type)
            ?: return null

        return Converter { body ->
            body.use {
                val stream = it.byteStream()
                var scratch = acquireScratchBuffer(BUFFER_SIZE)
                try {
                    var offset = 0
                    while (true) {
                        if (offset == scratch.size) {
                            val grown = acquireScratchBuffer(scratch.size * 2)
                            scratch.copyInto(grown, 0, 0, offset)
                            releaseScratchBuffer(scratch)
                            scratch = grown
                        }

                        val read = stream.read(scratch, offset, scratch.size - offset)
                        if (read == -1) break
                        offset += read
                    }

                    ghostProtoInternalUseFlatReader(scratch, length = offset) { reader ->
                        if (reader.isNextNullValue()) {
                            reader.consumeNull()
                            null
                        } else {
                            serializer.deserialize(reader)
                        }
                    }
                } finally {
                    releaseScratchBuffer(scratch)
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
            return Ghost.getSerializer(type.kotlin as KClass<Any>)
        }

        if (type is java.lang.reflect.ParameterizedType) {
            val rawType = type.rawType as? Class<*> ?: return null

            if (List::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.firstOrNull() ?: return null
                val itemSerializer = getSerializerWithCache(arg) ?: return null
                return ListSerializer(itemSerializer) as GhostSerializer<Any>
            }

            if (Map::class.java.isAssignableFrom(rawType)) {
                val arg = type.actualTypeArguments.getOrNull(1) ?: return null
                val valueSerializer = getSerializerWithCache(arg) ?: return null
                return MapSerializer(valueSerializer) as GhostSerializer<Any>
            }
        }
        return null
    }

    companion object {
        private val MEDIA_TYPE = GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType()
        private const val BUFFER_SIZE = 524288

        fun create(): GhostProtoConverterFactory = GhostProtoConverterFactory()
    }
}
