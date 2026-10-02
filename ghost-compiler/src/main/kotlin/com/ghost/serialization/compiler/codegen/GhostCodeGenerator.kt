package com.ghost.serialization.compiler.codegen

import com.ghost.serialization.compiler.codegen.emit.DeserializeCodeEmitter
import com.ghost.serialization.compiler.codegen.emit.EnvelopeRouterEmitter
import com.ghost.serialization.compiler.codegen.emit.SerializeCodeEmitter
import com.ghost.serialization.compiler.model.GhostEnvelopeModel
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.ghost.serialization.compiler.model.GhostSerializerContext
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG


/**
 * Orchestrates generation of a specialized GhostSerializer companion object.
 *
 * Delegates import planning to [SerializerImportResolver], companion constants to
 * [SerializerSetupEmitter], and serialize/deserialize bodies to the *Emitter types.
 */
internal class GhostCodeGenerator(
    properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration,
    textChannel: Boolean = false,
    envelopeModel: GhostEnvelopeModel? = null,
    hasYaml: Boolean = false,
) {
    private val context = GhostSerializerContext.from(
        properties = properties,
        classDeclaration = classDeclaration,
        textChannel = textChannel,
        envelopeModel = envelopeModel,
        hasYaml = hasYaml,
    )
    private val importResolver = SerializerImportResolver(ctx = context)
    private val setupEmitter = SerializerSetupEmitter(ctx = context)

    fun createSpec(): FileSpec {
        val fileBuilder = FileSpec.builder(context.packageName, context.serializerName)
            .addAnnotation(
                AnnotationSpec.builder(ClassName(CC.PKG_KOTLIN, CG.STR_OPT_IN))
                    .addMember(
                        CG.MARKER_CLASS,
                        ClassName(CC.PKG_GHOST, CG.STR_INTERNAL_GHOST_API)
                    )
                    .build()
            )

        importResolver.applyTo(fileBuilder = fileBuilder)

        return fileBuilder
            .apply {
                if (context.envelopeModel?.payloadMappings?.any { it.targetType != null } == true) {
                    addImport(CC.PKG_TYPES, CG.STR_RAW_JSON_DECODE)
                    addImport(CC.PKG_GHOST, CG.STR_GHOST)
                    addImport(CC.PKG_CONTRACT, CC.STR_GHOST_SERIALIZER)
                }
            }
            .addType(buildSerializerObject())
            .build()
    }

    private fun buildSerializerObject(): TypeSpec {
        val serializeEmitter = SerializeCodeEmitter(
            properties = context.properties,
            originalClassName = context.originalClassName,
            isSealed = context.isSealed,
            isValue = context.isValue,
            isEnum = context.isEnum,
            sealedSubclasses = context.sealedSubclasses,
            discriminator = context.discriminator,
            sealedDiscriminatorKey = context.sealedDiscriminatorKey
        )

        val deserializeEmitterStreaming = deserializeEmitterFor(readerClass = context.streamingReaderClass)
        val deserializeEmitterFlat = deserializeEmitterFor(readerClass = context.flatReaderClass)
        val deserializeEmitterString = if (context.textChannel) {
            deserializeEmitterFor(readerClass = context.stringReaderClass)
        } else {
            null
        }

        val typeSpecBuilder = TypeSpec.objectBuilder(context.serializerName)
            .addKdoc(CG.STR_KDOC_HIGH_PERF, context.originalClassName)
            .addKdoc(CG.STR_KDOC_GENERATED)
            .superclass(context.serializerBaseClass.parameterizedBy(context.originalClassName))

        if (context.hasYaml) {
            typeSpecBuilder.addSuperinterface(
                context.yamlSerializerInterface.parameterizedBy(context.originalClassName)
            )
        }

        typeSpecBuilder.addProperty(
            PropertySpec.builder(CG.STR_IS_PROTO, com.squareup.kotlinpoet.BOOLEAN)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(if (context.isProto) CC.STR_TRUE else CC.STR_FALSE)
                .build()
        )

        typeSpecBuilder
            .addProperty(
                PropertySpec.builder(CG.STR_TYPE_NAME_PROP, String::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(PC.STR_FORMAT_S, context.finalTypeName)
                    .build()
            )

        if (context.needsObjectParsingImports()) {
            setupEmitter.addPerfectHashOptions(typeSpecBuilder = typeSpecBuilder)
        }
        if (context.needsCachedByteStringHeaders()) {
            setupEmitter.addCachedHeaderProperties(typeSpecBuilder = typeSpecBuilder)
        }
        if (context.isEnum && context.enumValues != null) {
            setupEmitter.addEnumOptions(typeSpecBuilder = typeSpecBuilder)
        }

        FlattenOptionsGenerator.generateNestedOptions(
            typeSpecBuilder = typeSpecBuilder,
            properties = context.properties,
            fullPaths = context.fullPaths,
            textChannel = context.textChannel
        )

        deserializeEmitterStreaming.build(typeSpecBuilder, isFlatPath = false)
        deserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)
        if (context.textChannel) {
            deserializeEmitterString?.build(typeSpecBuilder, isFlatPath = true)
        }

        if (context.hasYaml) {
            val yamlDeserializeEmitterFlat = deserializeEmitterFor(
                readerClass = context.yamlFlatReaderClass,
                isResilientClass = false,
                supportsResilience = false,
            )
            yamlDeserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)
        }

        serializeEmitter.injectContextualSerializers(typeSpecBuilder = typeSpecBuilder)

        context.envelopeModel?.let { envelope ->
            EnvelopeRouterEmitter(
                envelope = envelope,
                originalClassName = context.originalClassName,
                readerClass = context.streamingReaderClass
            ).emit(typeSpecBuilder)
        }

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(context.streamingWriterClass, typeSpecBuilder))
            .apply {
                if (context.textChannel) {
                    addFunction(serializeEmitter.build(context.stringWriterClass, typeSpecBuilder))
                }
                if (context.hasYaml) {
                    addFunction(serializeEmitter.build(context.yamlWriterClass, typeSpecBuilder))
                }
            }
            .addFunction(setupEmitter.buildWarmUpMethod())
            .build()
    }

    private fun deserializeEmitterFor(
        readerClass: ClassName,
        isResilientClass: Boolean = context.isResilient,
        supportsResilience: Boolean = true,
    ): DeserializeCodeEmitter = DeserializeCodeEmitter(
        properties = context.properties,
        originalClassName = context.originalClassName,
        readerClass = readerClass,
        isSealed = context.isSealed,
        isValue = context.isValue,
        isEnum = context.isEnum,
        sealedSubclasses = context.sealedSubclasses,
        sealedDiscriminatorKey = context.sealedDiscriminatorKey,
        isResilientClass = isResilientClass,
        isInferred = context.isInferred,
        isObject = context.isObject,
        hasFallback = context.hasFallbackEnum,
        supportsResilience = supportsResilience,
    )
}
