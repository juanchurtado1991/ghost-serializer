package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Emits serializer companion setup: perfect-hash OPTIONS, cached field headers, enum tables, warmUp.
 */
internal class SerializerSetupEmitter(
    private val ctx: GhostSerializerContext,
) {

    fun addPerfectHashOptions(typeSpecBuilder: TypeSpec.Builder) {
        val names = if (ctx.isSealed && ctx.isInferred) {
            ctx.properties
                .firstOrNull()
                ?.inferredSubclasses
                ?.flatMap { it.properties }
                ?.map { it.jsonName }?.distinct()
                ?: emptyList()
        } else {
            ctx.properties.map {
                it.flattenPath
                    ?.firstOrNull()
                    ?: it.wrapPath?.firstOrNull()
                    ?: it.jsonName
            }.distinct()
        }
        val hashConfig = PerfectHashFinder.findPerfectHash(names)
        val optionsClass = ctx.readerClass.peerClass(C.STR_OPTIONS_CLASS)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(C.STR_OPTIONS, optionsClass)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    buildReaderOptionsInitializer(
                        optionsClass = optionsClass,
                        hashConfig = hashConfig,
                        names = names,
                    )
                )
                .build()
        )
    }

    fun addCachedHeaderProperties(typeSpecBuilder: TypeSpec.Builder) {
        for (name in ctx.getAllJsonNames()) {
            val cleanName = name.replace(C.STR_DOT, C.STR_UNDERSCORE).uppercase()

            typeSpecBuilder.addProperty(
                PropertySpec.builder(C.STR_H_VAL_PREFIX + cleanName, C.BYTE_STRING_CLASS, KModifier.PRIVATE)
                    .initializer(C.TEMPLATE_ENCODE_UTF8, C.FMT_JSON_FIELD.format(name))
                    .build()
            )

            if (ctx.textChannel) {
                typeSpecBuilder.addProperty(
                    PropertySpec.builder(C.STR_HS_PREFIX + cleanName, String::class, KModifier.PRIVATE)
                        .initializer("%S", C.FMT_JSON_FIELD.format(name))
                        .build()
                )
            }
        }
    }

    fun addEnumOptions(typeSpecBuilder: TypeSpec.Builder) {
        val values = ctx.enumValues!!.values.toList()
        val hashConfig = PerfectHashFinder.findPerfectHash(values)
        val optionsClass = ctx.readerClass.peerClass(C.STR_OPTIONS_CLASS)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(C.STR_ENUM_OPTIONS, optionsClass)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    buildReaderOptionsInitializer(
                        optionsClass = optionsClass,
                        hashConfig = hashConfig,
                        names = values,
                    )
                )
                .build()
        )
    }

    fun buildWarmUpMethod(): FunSpec {
        val warmupJson = generateMinimalJson()
        return FunSpec.builder(C.STR_WARM_UP)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow(C.STR_TRY)
                    .addStatement(
                        C.TEMPLATE_WARM_UP_READER_INIT,
                        C.STR_READER1,
                        ctx.streamingReaderClass,
                        warmupJson
                    )
                    .addStatement(C.TEMPLATE_WARM_UP_DESERIALIZE, C.STR_READER1)
                    .nextControlFlow(C.STR_CATCH_EXCEPTION)
                    .endControlFlow()
                    .beginControlFlow(C.STR_TRY)
                    .addStatement(
                        C.TEMPLATE_WARM_UP_READER_INIT,
                        C.STR_READER2,
                        ctx.flatReaderClass,
                        warmupJson
                    )
                    .addStatement(C.TEMPLATE_WARM_UP_DESERIALIZE, C.STR_READER2)
                    .nextControlFlow(C.STR_CATCH_EXCEPTION)
                    .endControlFlow()
                    .build()
            )
            .build()
    }

    private fun buildReaderOptionsInitializer(
        optionsClass: ClassName,
        hashConfig: PerfectHashConfig,
        names: List<String>,
    ): CodeBlock {
        val optionsBuilder = CodeBlock.builder()
        if (hashConfig.extendedKeyHash) {
            optionsBuilder.add(
                C.TEMPLATE_OPTIONS_OF_SEEDS_EXTENDED_START,
                optionsClass,
                hashConfig.shift,
                hashConfig.multiplier,
                hashConfig.tableSize,
                ctx.textChannel,
                true
            )
        } else {
            optionsBuilder.add(
                C.TEMPLATE_OPTIONS_OF_SEEDS_START,
                optionsClass,
                hashConfig.shift,
                hashConfig.multiplier,
                hashConfig.tableSize,
                ctx.textChannel
            )
        }
        names.forEach { name ->
            optionsBuilder.add(C.TEMPLATE_COMMA_FORMAT_S, name)
        }
        optionsBuilder.add(")")
        return optionsBuilder.build()
    }

    private fun generateMinimalJson(): String {
        if (ctx.isSealed || ctx.isEnum || ctx.isValue) {
            return C.STR_EMPTY_JSON
        }
        val sb = StringBuilder()
        sb.append(C.STR_CURLY_OPEN)
        val entries = mutableListOf<String>()
        ctx.properties.forEach { prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val key = C.STR_DOUBLE_QUOTE + prop.jsonName + C.STR_DOUBLE_QUOTE
                val value = when {
                    prop.type.isPrimitiveInt() || prop.type.isPrimitiveLong() ||
                        prop.type.isPrimitiveByte() || prop.type.isPrimitiveShort() -> C.STR_ZERO
                    prop.type.isPrimitiveDouble() || prop.type.isPrimitiveFloat() -> C.STR_ZERO_D
                    prop.type.isPrimitiveBoolean() -> C.STR_FALSE
                    prop.type.isPrimitiveChar() -> C.STR_JSON_CHAR_NULL
                    prop.type.isString() -> C.STR_EMPTY_STRING
                    prop.type.isList() || prop.type.isSet() -> C.STR_EMPTY_ARRAY
                    prop.type.isMap() -> C.STR_EMPTY_JSON
                    prop.type.isGhost() -> C.STR_EMPTY_JSON
                    else -> C.STR_NULL
                }
                entries.add(key + C.STR_COLON + value)
            }
        }
        sb.append(entries.joinToString(C.STR_COMMA))
        sb.append(C.STR_CURLY_CLOSE)
        return sb.toString()
    }
}
