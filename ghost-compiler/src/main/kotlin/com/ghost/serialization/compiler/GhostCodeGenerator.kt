@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.ghost.serialization.compiler.GhostEmitterConstants as C

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
) {
    private val ctx = GhostSerializerContext.from(
        properties = properties,
        classDeclaration = classDeclaration,
        textChannel = textChannel,
        envelopeModel = envelopeModel,
    )
    private val importResolver = SerializerImportResolver(ctx)
    private val setupEmitter = SerializerSetupEmitter(ctx)

    fun createSpec(): FileSpec {
        val fileBuilder = FileSpec.builder(ctx.packageName, ctx.serializerName)
            .addAnnotation(
                AnnotationSpec.builder(ClassName(C.PKG_KOTLIN, C.STR_OPT_IN))
                    .addMember(
                        C.MARKER_CLASS,
                        ClassName(C.PKG_GHOST, C.STR_INTERNAL_GHOST_API)
                    )
                    .build()
            )

        importResolver.applyTo(fileBuilder)

        return fileBuilder
            .apply {
                if (ctx.envelopeModel?.payloadMappings?.any { it.targetType != null } == true) {
                    addImport(C.PKG_TYPES, C.STR_RAW_JSON_DECODE)
                    addImport(C.PKG_GHOST, C.STR_GHOST)
                    addImport(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER)
                }
            }
            .addType(buildSerializerObject())
            .build()
    }

    private fun buildSerializerObject(): TypeSpec {
        val serializeEmitter = SerializeCodeEmitter(
            ctx.properties,
            ctx.originalClassName,
            ctx.isSealed,
            ctx.isValue,
            ctx.isEnum,
            ctx.sealedSubclasses,
            ctx.discriminator,
            ctx.sealedDiscriminatorKey
        )

        val deserializeEmitterStreaming = DeserializeCodeEmitter(
            ctx.properties,
            ctx.originalClassName,
            ctx.streamingReaderClass,
            ctx.isSealed,
            ctx.isValue,
            ctx.isEnum,
            ctx.sealedSubclasses,
            ctx.sealedDiscriminatorKey,
            ctx.isResilient,
            ctx.isInferred,
            ctx.isObject,
            hasFallback = ctx.hasFallbackEnum
        )

        val deserializeEmitterFlat = DeserializeCodeEmitter(
            ctx.properties,
            ctx.originalClassName,
            ctx.flatReaderClass,
            ctx.isSealed,
            ctx.isValue,
            ctx.isEnum,
            ctx.sealedSubclasses,
            ctx.sealedDiscriminatorKey,
            ctx.isResilient,
            ctx.isInferred,
            ctx.isObject,
            hasFallback = ctx.hasFallbackEnum
        )

        val deserializeEmitterString = if (ctx.textChannel) {
            DeserializeCodeEmitter(
                ctx.properties,
                ctx.originalClassName,
                ctx.stringReaderClass,
                ctx.isSealed,
                ctx.isValue,
                ctx.isEnum,
                ctx.sealedSubclasses,
                ctx.sealedDiscriminatorKey,
                ctx.isResilient,
                ctx.isInferred,
                ctx.isObject,
                hasFallback = ctx.hasFallbackEnum
            )
        } else {
            null
        }

        val typeSpecBuilder = TypeSpec.objectBuilder(ctx.serializerName)
            .addKdoc(C.STR_KDOC_HIGH_PERF, ctx.originalClassName)
            .addKdoc(C.STR_KDOC_GENERATED)
            .addSuperinterface(ctx.serializerInterface.parameterizedBy(ctx.originalClassName))
            .addProperty(
                PropertySpec.builder(C.STR_TYPE_NAME_PROP, String::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.MARKER, ctx.finalTypeName)
                    .build()
            )

        if (ctx.isProto) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(C.STR_IS_PROTO, com.squareup.kotlinpoet.BOOLEAN)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.STR_TRUE)
                    .build()
            )
        }

        if (ctx.needsObjectParsingImports()) {
            setupEmitter.addPerfectHashOptions(typeSpecBuilder)
        }
        if (ctx.needsCachedByteStringHeaders()) {
            setupEmitter.addCachedHeaderProperties(typeSpecBuilder)
        }
        if (ctx.isEnum && ctx.enumValues != null) {
            setupEmitter.addEnumOptions(typeSpecBuilder)
        }

        FlattenOptionsGenerator.generateNestedOptions(
            typeSpecBuilder,
            ctx.properties,
            ctx.fullPaths,
            ctx.readerClass,
            ctx.textChannel
        )

        deserializeEmitterStreaming.build(typeSpecBuilder, isFlatPath = false)
        deserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)
        if (ctx.textChannel) {
            deserializeEmitterString?.build(typeSpecBuilder, isFlatPath = true)
        }

        serializeEmitter.injectContextualSerializers(typeSpecBuilder)

        ctx.envelopeModel?.let { envelope ->
            EnvelopeRouterEmitter(
                envelope = envelope,
                originalClassName = ctx.originalClassName,
                flatReaderClass = ctx.flatReaderClass
            ).emit(typeSpecBuilder)
        }

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(ctx.streamingWriterClass, typeSpecBuilder))
            .addFunction(serializeEmitter.build(ctx.flatWriterClass, typeSpecBuilder))
            .apply {
                if (ctx.textChannel) {
                    addFunction(serializeEmitter.build(ctx.stringWriterClass, typeSpecBuilder))
                }
            }
            .addFunction(setupEmitter.buildWarmUpMethod())
            .build()
    }
}
