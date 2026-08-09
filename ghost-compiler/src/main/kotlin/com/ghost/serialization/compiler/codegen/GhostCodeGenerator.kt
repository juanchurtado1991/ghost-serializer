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
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


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
    private val importResolver = SerializerImportResolver(context)
    private val setupEmitter = SerializerSetupEmitter(context)

    fun createSpec(): FileSpec {
        val fileBuilder = FileSpec.builder(context.packageName, context.serializerName)
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
                if (context.envelopeModel?.payloadMappings?.any { it.targetType != null } == true) {
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
            context.properties,
            context.originalClassName,
            context.isSealed,
            context.isValue,
            context.isEnum,
            context.sealedSubclasses,
            context.discriminator,
            context.sealedDiscriminatorKey
        )

        val deserializeEmitterStreaming = DeserializeCodeEmitter(
            context.properties,
            context.originalClassName,
            context.streamingReaderClass,
            context.isSealed,
            context.isValue,
            context.isEnum,
            context.sealedSubclasses,
            context.sealedDiscriminatorKey,
            context.isResilient,
            context.isInferred,
            context.isObject,
            hasFallback = context.hasFallbackEnum
        )

        val deserializeEmitterFlat = DeserializeCodeEmitter(
            context.properties,
            context.originalClassName,
            context.flatReaderClass,
            context.isSealed,
            context.isValue,
            context.isEnum,
            context.sealedSubclasses,
            context.sealedDiscriminatorKey,
            context.isResilient,
            context.isInferred,
            context.isObject,
            hasFallback = context.hasFallbackEnum
        )

        val deserializeEmitterString = if (context.textChannel) {
            DeserializeCodeEmitter(
                context.properties,
                context.originalClassName,
                context.stringReaderClass,
                context.isSealed,
                context.isValue,
                context.isEnum,
                context.sealedSubclasses,
                context.sealedDiscriminatorKey,
                context.isResilient,
                context.isInferred,
                context.isObject,
                hasFallback = context.hasFallbackEnum
            )
        } else {
            null
        }

        val typeSpecBuilder = TypeSpec.objectBuilder(context.serializerName)
            .addKdoc(C.STR_KDOC_HIGH_PERF, context.originalClassName)
            .addKdoc(C.STR_KDOC_GENERATED)
            .addSuperinterface(context.serializerInterface.parameterizedBy(context.originalClassName))

        if (context.hasYaml) {
            typeSpecBuilder.addSuperinterface(
                context.yamlSerializerInterface.parameterizedBy(context.originalClassName)
            )
        }

        typeSpecBuilder
            .addProperty(
                PropertySpec.builder(C.STR_TYPE_NAME_PROP, String::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.MARKER, context.finalTypeName)
                    .build()
            )

        if (context.isProto) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(C.STR_IS_PROTO, com.squareup.kotlinpoet.BOOLEAN)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.STR_TRUE)
                    .build()
            )
        }

        if (context.needsObjectParsingImports()) {
            setupEmitter.addPerfectHashOptions(typeSpecBuilder)
        }
        if (context.needsCachedByteStringHeaders()) {
            setupEmitter.addCachedHeaderProperties(typeSpecBuilder)
        }
        if (context.isEnum && context.enumValues != null) {
            setupEmitter.addEnumOptions(typeSpecBuilder)
        }

        FlattenOptionsGenerator.generateNestedOptions(
            typeSpecBuilder,
            context.properties,
            context.fullPaths,
            context.textChannel
        )

        deserializeEmitterStreaming.build(typeSpecBuilder, isFlatPath = false)
        deserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)
        if (context.textChannel) {
            deserializeEmitterString?.build(typeSpecBuilder, isFlatPath = true)
        }

        if (context.hasYaml) {
            val yamlDeserializeEmitterFlat = DeserializeCodeEmitter(
                context.properties,
                context.originalClassName,
                context.yamlFlatReaderClass,
                context.isSealed,
                context.isValue,
                context.isEnum,
                context.sealedSubclasses,
                context.sealedDiscriminatorKey,
                isResilientClass = false,
                context.isInferred,
                context.isObject,
                hasFallback = context.hasFallbackEnum,
                supportsResilience = false,
            )
            yamlDeserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)
        }

        serializeEmitter.injectContextualSerializers(typeSpecBuilder)

        context.envelopeModel?.let { envelope ->
            EnvelopeRouterEmitter(
                envelope = envelope,
                originalClassName = context.originalClassName,
                flatReaderClass = context.flatReaderClass
            ).emit(typeSpecBuilder)
        }

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(context.streamingWriterClass, typeSpecBuilder))
            .addFunction(serializeEmitter.build(context.flatWriterClass, typeSpecBuilder))
            .apply {
                if (context.textChannel) {
                    addFunction(serializeEmitter.build(context.stringWriterClass, typeSpecBuilder))
                }
                if (context.hasYaml) {
                    addFunction(serializeEmitter.build(context.yamlWriterClass, typeSpecBuilder))
                    addFunction(
                        serializeEmitter.build(
                            context.yamlFlatWriterClass,
                            typeSpecBuilder
                        )
                    )
                }
            }
            .addFunction(setupEmitter.buildWarmUpMethod())
            .build()
    }
}
