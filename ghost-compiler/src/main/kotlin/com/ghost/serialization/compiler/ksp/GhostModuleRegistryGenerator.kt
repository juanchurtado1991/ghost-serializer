package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC



/**
 * Generates the per-module class-to-serializer registry (sharded into chunks to stay under JVM
 * method limits when large). Split out of [GhostSerializationProcessor] since the sharding and
 * lookup strategy changes independently of KSP visiting or build-tooling output.
 */
internal class GhostModuleRegistryGenerator(
    private val codeGenerator: CodeGenerator,
    private val registryClassName: String,
    private val originatingFiles: Set<KSFile>,
    private val emitJvmField: Boolean,
) {

    /** Generates the class-to-serializer registry, sharded into chunks to avoid JVM method limits when large. */
    fun generate(classToSerializer: Map<ClassName, ClassName>) {
        val serializerType = ClassName(CC.PKG_CONTRACT, CC.STR_GHOST_SERIALIZER)
        val kClassType = ClassName(CC.STR_REFLECT_PKG, PC.STR_KCLASS)
        val type = TypeVariableName(PC.STR_TYPE_T, Any::class)
        val mapType = ClassName(CC.STR_COLLECTIONS_PKG, PC.STR_MAP)
            .parameterizedBy(
                kClassType.parameterizedBy(STAR),
                serializerType.parameterizedBy(STAR)
            )

        val entries = classToSerializer.entries.toList().sortedBy { it.key.canonicalName }
        val registrySpec = TypeSpec.classBuilder(registryClassName)
            .addKdoc(PC.STR_KDOC_REGISTRY)
            .addSuperinterface(ClassName(CC.PKG_CONTRACT, PC.STR_GHOST_REGISTRY))

        val chunks = entries.chunked(PC.REGISTRY_CHUNK_SIZE)

        generateSerializersMapProperty(
            registrySpec = registrySpec,
            chunks = chunks,
            entries = entries,
            mapType = mapType
        )
        generateGetSerializerMethod(
            registrySpec = registrySpec,
            chunks = chunks,
            entries = entries,
            serializerType = serializerType,
            type = type
        )
        generateShardMethods(
            registrySpec = registrySpec,
            chunks = chunks,
            mapType = mapType,
            serializerType = serializerType
        )
        generateMetadataMethodsAndCompanion(registrySpec = registrySpec, entriesCount = entries.size, mapType = mapType)

        writeRegistryFile(registrySpec = registrySpec.build())
    }

    private fun buildMapBlock(
        entries: List<Map.Entry<ClassName, ClassName>>
    ): CodeBlock {
        val builder = CodeBlock.builder().add(PC.STR_MAP_OF)
        entries.forEachIndexed { index, entry ->
            builder.add(
                PC.STR_MAP_ENTRY,
                entry.key,
                entry.value
            )
            if (index < entries.size - 1) {
                builder.add(PC.STR_COMMA_NEWLINE)
            }
        }
        builder.add(PC.STR_PAREN_CLOSE)
        return builder.build()
    }

    private fun buildWhenBlock(
        entries: List<Map.Entry<ClassName, ClassName>>,
        serializerType: ClassName,
        type: TypeName
    ): CodeBlock {
        val builder = CodeBlock
            .builder()
            .add(PC.STR_WHEN_CLAZZ_START)

        entries.forEach { entry ->
            builder.add(
                PC.STR_WHEN_ENTRY,
                entry.key,
                entry.value
            )
        }

        builder.add(PC.STR_WHEN_ELSE_NULL)
        builder.add(
            PC.STR_WHEN_CLOSE_CAST,
            serializerType
                .parameterizedBy(type)
                .copy(nullable = true)
        )
        return builder.build()
    }

    private fun generateGetSerializerMethod(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        entries: List<Map.Entry<ClassName, ClassName>>,
        serializerType: ClassName,
        type: TypeVariableName
    ) {
        val getMethodBuilder = FunSpec.builder(PC.STR_FUN_GET_SERIALIZER)
            .addTypeVariable(type)
            .addParameter(
                PC.STR_PARAM_CLAZZ,
                KClass::class.asClassName().parameterizedBy(type)
            )
            .returns(serializerType.parameterizedBy(type).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember(PC.STR_FORMAT_S, PC.STR_UNCHECKED_CAST)
                    .build()
            )

        val getCode = CodeBlock.builder()
        if (chunks.size > 1) {
            for (index in chunks.indices) {
                getCode.addStatement(
                    PC.TEMPLATE_GET_SHARD_CALL,
                    index,
                    serializerType.parameterizedBy(type).copy(nullable = true)
                )
            }
            getCode.addStatement(PC.STR_RETURN_NULL)
        } else {
            getCode.add(buildWhenBlock(entries, serializerType, type))
        }
        getMethodBuilder.addCode(getCode.build())
        registrySpec.addFunction(getMethodBuilder.build())
    }

    private fun generateMetadataMethodsAndCompanion(
        registrySpec: TypeSpec.Builder,
        entriesCount: Int,
        mapType: TypeName
    ) {
        registrySpec.addFunction(
            FunSpec.builder(PC.STR_FUN_PREWARM)
                .addModifiers(KModifier.OVERRIDE)
                .addStatement(PC.STR_SERIALIZERS_SIZE)
                .build()
        )
        registrySpec.addFunction(
            FunSpec.builder(PC.STR_FUN_REG_COUNT)
                .addModifiers(KModifier.OVERRIDE)
                .returns(Int::class)
                .addStatement(PC.STR_RETURN_L, entriesCount)
                .build()
        )
        registrySpec.addFunction(
            FunSpec.builder(PC.STR_FUN_GET_ALL_SERIALIZERS)
                .addModifiers(KModifier.OVERRIDE)
                .returns(mapType)
                .addStatement(PC.STR_RETURN_SERIALIZERS)
                .build()
        )
        registrySpec.addType(
            TypeSpec.companionObjectBuilder()
                .addProperty(
                    PropertySpec.builder(
                        PC.STR_INSTANCE,
                        ClassName(CC.STR_GENERATED_PKG, registryClassName)
                    )
                        .initializer(
                            PC.STR_INIT_INSTANCE,
                            ClassName(CC.STR_GENERATED_PKG, registryClassName)
                        )
                        .apply { if (emitJvmField) addAnnotation(JvmField::class) }
                        .build()
                )
                .build()
        )
    }

    private fun generateSerializersMapProperty(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        entries: List<Map.Entry<ClassName, ClassName>>,
        mapType: TypeName
    ) {
        val allSerializersDelegate = CodeBlock.builder()
            .add(PC.STR_LAZY_START)
            .indent()

        if (chunks.size > 1) {
            chunks.forEachIndexed { index, _ ->
                allSerializersDelegate.add(PC.TEMPLATE_GET_SHARD_MAP_CALL, index)
                if (index < chunks.size - 1) {
                    allSerializersDelegate.add(PC.STR_PLUS_SPACED)
                }
            }
        } else {
            allSerializersDelegate.add(buildMapBlock(entries))
        }

        allSerializersDelegate.unindent().add(PC.STR_NEWLINE_CLOSE_CURLY)

        registrySpec.addProperty(
            PropertySpec.builder(PC.STR_PROP_SERIALIZERS_MAP, mapType)
                .addModifiers(KModifier.PRIVATE)
                .delegate(allSerializersDelegate.build())
                .build()
        )
    }

    private fun generateShardMethods(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        mapType: TypeName,
        serializerType: ClassName
    ) {
        if (chunks.size > 1) {
            chunks.forEachIndexed { i, chunk ->
                registrySpec.addFunction(
                    FunSpec.builder(PC.TEMPLATE_SHARD_MAP_NAME.format(i))
                        .addModifiers(KModifier.PRIVATE)
                        .returns(mapType)
                        .addCode(PC.STR_RETURN_L, buildMapBlock(entries = chunk))
                        .build()
                )

                registrySpec.addFunction(
                    FunSpec.builder(PC.TEMPLATE_SHARD_NAME.format(i))
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter(
                            PC.STR_PARAM_CLAZZ,
                            KClass::class.asClassName().parameterizedBy(STAR)
                        )
                        .returns(serializerType.parameterizedBy(STAR).copy(nullable = true))
                        .addCode(buildWhenBlock(entries = chunk, serializerType = serializerType, type = STAR))
                        .build()
                )
            }
        }
    }

    private fun writeRegistryFile(registrySpec: TypeSpec) {
        GeneratedSourceTrimmer.write(
            fileSpec = FileSpec.builder(CC.STR_GENERATED_PKG, registryClassName)
                .addType(registrySpec)
                .build(),
            codeGenerator = codeGenerator,
            dependencies = Dependencies(aggregating = true, *originatingFiles.toTypedArray()),
        )
    }
}
