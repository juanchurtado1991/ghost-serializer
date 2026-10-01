package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.analysis.containsYamlIncompatibleType
import com.ghost.serialization.compiler.analysis.isByteArray
import com.ghost.serialization.compiler.analysis.isRawJson
import com.ghost.serialization.compiler.model.GhostEnvelopeModel
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC



/**
 * Decides whether a class gets YAML codegen and reports misuse of `@GhostYamlSerialization` —
 * the compatibility rules (resilient, sealed, envelope, inferred, custom codecs, structural
 * annotations, nested ghosts, ...). Split out of [GhostSerializationProcessor] since these rules
 * change whenever YAML's supported surface changes, independently of how KSP symbols are visited
 * or how the registry is generated.
 */
internal class GhostYamlEligibility(private val logger: KSPLogger) {

    fun reportOrphanGhostYamlSerialization(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(CC.STR_ANNOTATION_YAML_SERIALIZATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                val hasGhostSerialization = classDeclaration.annotations.any {
                    it.shortName.asString() == CC.ANNOTATION_GHOST_SERIALIZATION
                }
                val hasProtoSerialization = classDeclaration.annotations.any {
                    it.shortName.asString() == CC.ANNOTATION_GHOST_PROTO_SERIALIZATION
                }
                if (!hasGhostSerialization && !hasProtoSerialization) {
                    logger.error(PC.STR_ERR_YAML_ORPHAN, classDeclaration)
                }
            }
    }

    fun shouldGenerateYaml(
        resolver: Resolver,
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
        envelopeModel: GhostEnvelopeModel?,
    ): Boolean {
        val hasYamlAnnotation = classDeclaration.annotations.any {
            it.shortName.asString() == CC.ANNOTATION_GHOST_YAML_SERIALIZATION
        }
        if (!hasYamlAnnotation) {
            return false
        }
        if (resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(PC.STR_YAML_SERIALIZER_FQN)
            ) == null
        ) {
            return false
        }
        val hasGhostSerialization = classDeclaration.annotations.any {
            it.shortName.asString() == CC.ANNOTATION_GHOST_SERIALIZATION
        }
        val hasProtoSerialization = classDeclaration.annotations.any {
            it.shortName.asString() == CC.ANNOTATION_GHOST_PROTO_SERIALIZATION
        }
        if (!hasGhostSerialization && !hasProtoSerialization) {
            logger.error(PC.STR_ERR_YAML_ORPHAN, classDeclaration)
            return false
        }
        if (classHasResilient(classDeclaration = classDeclaration, propertiesModel = propertiesModel)) {
            logger.error(PC.STR_ERR_YAML_RESILIENT, classDeclaration)
            return false
        }
        if (classDeclaration.modifiers.contains(Modifier.SEALED)) {
            logger.error(PC.STR_ERR_YAML_SEALED, classDeclaration)
            return false
        }
        if (envelopeModel != null) {
            logger.error(PC.STR_ERR_YAML_ENVELOPE, classDeclaration)
            return false
        }
        val isInferred = classDeclaration.annotations
            .find { it.shortName.asString() == CC.ANNOTATION_GHOST_SERIALIZATION }
            ?.arguments
            ?.find { it.name?.asString() == PC.ARG_INFERRED }
            ?.value as? Boolean
            ?: false
        if (isInferred) {
            logger.error(PC.STR_ERR_YAML_INFERRED, classDeclaration)
            return false
        }
        propertiesModel.forEach { prop ->
            if (propertyDisablesYamlCodegen(prop = prop)) {
                logger.error(
                    yamlIncompatibilityReason(prop = prop) ?: PC.STR_ERR_YAML_NESTED_GHOST,
                    classDeclaration,
                )
            }
        }
        return propertiesModel.none { propertyDisablesYamlCodegen(prop = it) }
    }

    private fun classHasResilient(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
    ): Boolean {
        if (classDeclaration.annotations.any { it.shortName.asString() == AC.GHOST_RESILIENT }) {
            return true
        }
        return propertiesModel.any { it.isResilient }
    }

    private fun propertyDisablesYamlCodegen(prop: GhostPropertyModel): Boolean {
        val hasCustomCoder = prop.isContextual || prop.customDecoder != null || prop.customEncoder != null
        if (hasCustomCoder) {
            return true
        }
        val hasStructuralMapping = prop.wrappedKeys != null || prop.flattenPath != null || prop.wrapPath != null
        if (hasStructuralMapping) {
            return true
        }
        if (prop.isSealedClass) {
            return true
        }
        if (prop.isGhost) {
            return true
        }
        if (prop.listInnerIsGhost || prop.mapValueIsGhost) {
            return true
        }
        if (prop.type.containsYamlIncompatibleType(isProto = prop.isProto)) {
            return true
        }
        val valueClassProperty = prop.valueClassProperty
        val isValueClassOfIncompatibleType = valueClassProperty != null &&
            valueClassProperty.type.containsYamlIncompatibleType(isProto = prop.isProto)
        if (isValueClassOfIncompatibleType) {
            return true
        }
        return false
    }

    private fun yamlIncompatibilityReason(prop: GhostPropertyModel): String? {
        val hasCustomCoder = prop.isContextual || prop.customDecoder != null || prop.customEncoder != null
        if (hasCustomCoder) {
            return PC.STR_ERR_YAML_CUSTOM_CODEC
        }
        val hasStructuralMapping = prop.wrappedKeys != null || prop.flattenPath != null || prop.wrapPath != null
        if (hasStructuralMapping) {
            return PC.STR_ERR_YAML_STRUCTURAL
        }
        if (prop.type.isRawJson()) {
            return PC.STR_ERR_YAML_RAW_JSON
        }
        if (!prop.isProto && prop.type.isByteArray()) {
            return PC.STR_ERR_YAML_BYTE_ARRAY
        }
        if (prop.isGhost) {
            return PC.STR_ERR_YAML_NESTED_GHOST
        }
        return null
    }
}
