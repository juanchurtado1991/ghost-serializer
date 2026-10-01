package com.ghost.serialization.compiler.codegen

import com.ghost.serialization.compiler.analysis.DispatchNamesResolver
import com.ghost.serialization.compiler.analysis.isGhost
import com.ghost.serialization.compiler.analysis.isList
import com.ghost.serialization.compiler.analysis.isMap
import com.ghost.serialization.compiler.analysis.isPrimitiveBoolean
import com.ghost.serialization.compiler.analysis.isPrimitiveByte
import com.ghost.serialization.compiler.analysis.isPrimitiveChar
import com.ghost.serialization.compiler.analysis.isPrimitiveDouble
import com.ghost.serialization.compiler.analysis.isPrimitiveFloat
import com.ghost.serialization.compiler.analysis.isPrimitiveInt
import com.ghost.serialization.compiler.analysis.isPrimitiveLong
import com.ghost.serialization.compiler.analysis.isPrimitiveShort
import com.ghost.serialization.compiler.analysis.isSet
import com.ghost.serialization.compiler.analysis.isString
import com.ghost.serialization.compiler.hash.PerfectHashConfig
import com.ghost.serialization.compiler.hash.PerfectHashFinder
import com.ghost.serialization.compiler.model.GhostSerializerContext
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG


/**
 * Emits serializer companion setup: perfect-hash OPTIONS, cached field headers, enum tables, warmUp.
 */
internal class SerializerSetupEmitter(
    private val ctx: GhostSerializerContext,
) {

    fun addCachedHeaderProperties(typeSpecBuilder: TypeSpec.Builder) {
        for (name in ctx.getAllJsonNames()) {
            val cleanName = name.replace(CC.STR_DOT, CC.STR_UNDERSCORE).uppercase()

            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    CG.STR_H_VAL_PREFIX + cleanName,
                    CG.BYTE_STRING_CLASS,
                    KModifier.PRIVATE
                )
                    .initializer(CG.TEMPLATE_ENCODE_UTF8, CG.FMT_JSON_FIELD.format(name))
                    .build()
            )

            if (ctx.textChannel) {
                typeSpecBuilder.addProperty(
                    PropertySpec.builder(
                        CG.STR_HS_PREFIX + cleanName,
                        String::class,
                        KModifier.PRIVATE
                    )
                        .initializer(PC.STR_FORMAT_S, CG.FMT_JSON_FIELD.format(name))
                        .build()
                )
            }
        }
    }

    fun addEnumOptions(typeSpecBuilder: TypeSpec.Builder) {
        val values = ctx.enumValues!!.values.toList()
        val hashConfig = PerfectHashFinder.findPerfectHash(names = values)
        val optionsClass = ClassName(CC.PKG_PARSER_COMMON_JSON, CG.STR_OPTIONS_CLASS)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(CG.STR_ENUM_OPTIONS, optionsClass)
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

    fun addPerfectHashOptions(typeSpecBuilder: TypeSpec.Builder) {
        val names = if (ctx.isSealed && ctx.isInferred) {
            ctx.properties
                .firstOrNull()
                ?.inferredSubclasses
                ?.flatMap { it.properties }
                ?.map { it.jsonName }?.distinct()
                ?: emptyList()
        } else {
            DispatchNamesResolver.topLevelNames(properties = ctx.properties)
        }
        val hashConfig = PerfectHashFinder.findPerfectHash(names = names)
        val optionsClass = ClassName(CC.PKG_PARSER_COMMON_JSON, CG.STR_OPTIONS_CLASS)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(CG.STR_OPTIONS, optionsClass)
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

    fun buildWarmUpMethod(): FunSpec {
        val warmupJson = generateMinimalJson()
        val warmUpBlock = CodeBlock.builder()
            .beginControlFlow(CG.STR_TRY)
            .addStatement(
                CG.TEMPLATE_WARM_UP_READER_INIT,
                CG.STR_READER1,
                ctx.streamingReaderClass,
                warmupJson
            )
            .addStatement(CG.TEMPLATE_WARM_UP_DESERIALIZE, CG.STR_READER1)
            .nextControlFlow(CG.STR_CATCH_EXCEPTION)
            .endControlFlow()
        if (ctx.textChannel) {
            warmUpBlock
                .beginControlFlow(CG.STR_TRY)
                .addStatement(
                    CG.TEMPLATE_WARM_UP_STRING_READER_INIT,
                    CG.STR_READER3,
                    ctx.stringReaderClass,
                    warmupJson
                )
                .addStatement(CG.TEMPLATE_WARM_UP_DESERIALIZE, CG.STR_READER3)
                .nextControlFlow(CG.STR_CATCH_EXCEPTION)
                .endControlFlow()
        }
        return FunSpec.builder(CG.STR_WARM_UP)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(warmUpBlock.build())
            .build()
    }

    private fun buildReaderOptionsInitializer(
        optionsClass: ClassName,
        hashConfig: PerfectHashConfig,
        names: List<String>,
    ): CodeBlock {
        return GeneratedCallFormat.jsonReaderOptionsOf(
            optionsClass = optionsClass,
            shift = hashConfig.shift,
            multiplier = hashConfig.multiplier,
            tableSize = hashConfig.tableSize,
            textChannel = ctx.textChannel,
            extendedKeyHash = hashConfig.extendedKeyHash,
            names = names,
        )
    }

    private fun generateMinimalJson(): String {
        val isSealedEnumOrValue = ctx.isSealed || ctx.isEnum || ctx.isValue
        if (isSealedEnumOrValue) {
            return CG.STR_EMPTY_JSON
        }
        val sb = StringBuilder()
        sb.append(CG.STR_CURLY_OPEN)
        val entries = mutableListOf<String>()
        ctx.properties.forEach { prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val key = AC.STR_DOUBLE_QUOTE + prop.jsonName + AC.STR_DOUBLE_QUOTE
                val value = when {
                    prop.type.isPrimitiveInt() || prop.type.isPrimitiveLong() ||
                            prop.type.isPrimitiveByte() || prop.type.isPrimitiveShort() -> AC.STR_ZERO

                    prop.type.isPrimitiveDouble() || prop.type.isPrimitiveFloat() -> AC.STR_ZERO_D
                    prop.type.isPrimitiveBoolean() -> CC.STR_FALSE
                    prop.type.isPrimitiveChar() -> CG.STR_JSON_CHAR_NULL
                    prop.type.isString() -> CG.STR_EMPTY_STRING
                    prop.type.isList() || prop.type.isSet() -> CG.STR_EMPTY_ARRAY
                    prop.type.isMap() -> CG.STR_EMPTY_JSON
                    prop.type.isGhost() -> CG.STR_EMPTY_JSON
                    else -> CC.STR_NULL
                }
                entries.add(key + CG.STR_COLON + value)
            }
        }
        sb.append(entries.joinToString(CG.STR_COMMA))
        sb.append(CG.STR_CURLY_CLOSE)
        return sb.toString()
    }
}
