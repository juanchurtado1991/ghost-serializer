package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Resolves [@GhostJsonEnvelope][com.ghost.serialization.annotations.GhostJsonEnvelope] metadata
 * and validates payload field conventions.
 */
internal class EnvelopeAnalyzer(private val logger: KSPLogger) {

    fun analyze(
        classDeclaration: KSClassDeclaration,
        properties: List<GhostPropertyModel>
    ): GhostEnvelopeModel? {
        val envelopeAnnotation = classDeclaration.annotations.find {
            it.shortName.asString() == C.STR_GHOST_JSON_ENVELOPE
        } ?: return null

        val discriminatorField = envelopeAnnotation.arguments
            .find { it.name?.asString() == C.ARG_ENVELOPE_DISCRIMINATOR }
            ?.value as? String
            ?: C.STR_DEFAULT_DISCRIMINATOR

        val timeField = (envelopeAnnotation.arguments
            .find { it.name?.asString() == C.ARG_ENVELOPE_TIME_FIELD }
            ?.value as? String)
            ?.takeIf { it.isNotEmpty() }

        val dataField = (envelopeAnnotation.arguments
            .find { it.name?.asString() == C.ARG_ENVELOPE_DATA_FIELD }
            ?.value as? String)
            ?.takeIf { it.isNotEmpty() }

        val discriminatorProperty = resolveProperty(properties, discriminatorField)
            ?: run {
                logger.error(
                    "${C.STR_ERR_ENVELOPE_DISC_1}$discriminatorField${C.STR_ERR_ENVELOPE_DISC_2}${
                        classDeclaration.simpleName.asString()
                    }${C.STR_ERR_ENVELOPE_DISC_3}",
                    classDeclaration
                )
                return null
            }

        if (!isStringType(discriminatorProperty.type)) {
            logger.error(
                "${C.STR_ERR_ENVELOPE_DISC_TYPE_1}${discriminatorProperty.kotlinName}${C.STR_ERR_ENVELOPE_DISC_TYPE_2}",
                classDeclaration
            )
        }

        val timeKotlinName = timeField?.let { fieldName ->
            resolveProperty(properties, fieldName)?.kotlinName
                ?: run {
                    logger.warn(
                        "${C.STR_WARN_ENVELOPE_TIME_1}$fieldName${C.STR_WARN_ENVELOPE_TIME_2}",
                        classDeclaration
                    )
                    null
                }
        }

        val propertyDeclarations = classDeclaration.getAllProperties()
            .filterNot { prop -> prop.annotations.any { it.shortName.asString() == C.GHOST_IGNORE } }
            .associateBy { it.simpleName.asString() }

        val fallbackDeclarations = propertyDeclarations.values.filter { prop ->
            prop.annotations.any { it.shortName.asString() == C.STR_GHOST_ENVELOPE_FALLBACK }
        }
        if (fallbackDeclarations.size > C.VAL_ONE) {
            logger.error(C.STR_ERR_ENVELOPE_MULTI_FALLBACK, classDeclaration)
        }

        val fallbackMapping = fallbackDeclarations.firstOrNull()?.let { prop ->
            buildFallbackMapping(prop, properties)
        }

        val isGenericMode = dataField != null
        val genericDataProperty = if (isGenericMode) {
            resolveProperty(properties, dataField!!)
                ?: run {
                    logger.error(
                        "${C.STR_ERR_ENVELOPE_DATA_1}$dataField${C.STR_ERR_ENVELOPE_DATA_2}",
                        classDeclaration
                    )
                    return null
                }
        } else {
            null
        }

        if (isGenericMode) {
            validateOpaquePayload(genericDataProperty!!, classDeclaration)
        }

        val payloadMappings = if (isGenericMode) {
            val genericProp = genericDataProperty!!
            properties.filter { prop ->
                prop.kotlinName != discriminatorProperty.kotlinName &&
                    prop.kotlinName != timeKotlinName &&
                    prop.kotlinName != fallbackMapping?.kotlinName
            }.mapNotNull { prop ->
                val declaration = propertyDeclarations[prop.kotlinName] ?: return@mapNotNull null
                val payloadAnnotation = declaration.annotations.find {
                    it.shortName.asString() == C.STR_GHOST_ENVELOPE_PAYLOAD
                }
                if (payloadAnnotation == null) {
                    if (prop.kotlinName == genericProp.kotlinName) {
                        null
                    } else {
                        null
                    }
                } else {
                    buildPayloadMapping(payloadAnnotation, prop, classDeclaration)
                }
            }
        } else {
            properties.mapNotNull { prop ->
                if (prop.kotlinName == discriminatorProperty.kotlinName ||
                    prop.kotlinName == timeKotlinName ||
                    prop.kotlinName == fallbackMapping?.kotlinName
                ) {
                    return@mapNotNull null
                }
                val declaration = propertyDeclarations[prop.kotlinName] ?: return@mapNotNull null
                val payloadAnnotation = declaration.annotations.find {
                    it.shortName.asString() == C.STR_GHOST_ENVELOPE_PAYLOAD
                } ?: run {
                    if (prop.isNullable && prop.type.isRawJson()) {
                        logger.warn(
                            "${C.STR_WARN_ENVELOPE_UNTAGGED_1}${prop.kotlinName}${C.STR_WARN_ENVELOPE_UNTAGGED_2}",
                            declaration
                        )
                    }
                    return@mapNotNull null
                }
                validateOpaquePayload(prop, classDeclaration)
                buildPayloadMapping(payloadAnnotation, prop, classDeclaration)
            }
        }

        if (!isGenericMode && payloadMappings.isEmpty()) {
            logger.error(C.STR_ERR_ENVELOPE_NO_PAYLOADS, classDeclaration)
        }

        val duplicateValues = payloadMappings.groupBy { it.discriminatorValue }.filter { it.value.size > 1 }
        duplicateValues.forEach { (value, mappings) ->
            logger.error(
                "${C.STR_ERR_ENVELOPE_DUP_VALUE_1}$value${C.STR_ERR_ENVELOPE_DUP_VALUE_2}${
                    mappings.joinToString { it.kotlinName }
                }",
                classDeclaration
            )
        }

        return GhostEnvelopeModel(
            discriminatorKotlinName = discriminatorProperty.kotlinName,
            discriminatorJsonName = discriminatorProperty.jsonName,
            timeKotlinName = timeKotlinName,
            isGenericMode = isGenericMode,
            genericDataKotlinName = genericDataProperty?.kotlinName,
            payloadMappings = payloadMappings,
            fallbackMapping = fallbackMapping
        )
    }

    private fun resolveProperty(
        properties: List<GhostPropertyModel>,
        fieldName: String
    ): GhostPropertyModel? =
        properties.find { it.jsonName == fieldName || it.kotlinName == fieldName }

    private fun isStringType(type: KSType): Boolean =
        type.declaration.qualifiedName?.asString() == C.STRING_QUALIFIED

    private fun validateOpaquePayload(prop: GhostPropertyModel, classDeclaration: KSClassDeclaration) {
        if (!prop.isNullable || !prop.type.isRawJson()) {
            logger.error(
                "${C.STR_ERR_ENVELOPE_PAYLOAD_TYPE_1}${prop.kotlinName}${C.STR_ERR_ENVELOPE_PAYLOAD_TYPE_2}",
                classDeclaration
            )
        }
    }

    private fun buildFallbackMapping(
        prop: KSPropertyDeclaration,
        properties: List<GhostPropertyModel>
    ): EnvelopePayloadMapping? {
        val model = properties.find { it.kotlinName == prop.simpleName.asString() } ?: return null
        validateOpaquePayload(model, prop.parentDeclaration as KSClassDeclaration)
        return EnvelopePayloadMapping(
            discriminatorValue = C.STR_EMPTY,
            kotlinName = model.kotlinName,
            isRawJson = model.type.isRawJson(),
            targetType = null
        )
    }

    private fun buildPayloadMapping(
        payloadAnnotation: com.google.devtools.ksp.symbol.KSAnnotation,
        prop: GhostPropertyModel,
        classDeclaration: KSClassDeclaration
    ): EnvelopePayloadMapping? {
        val value = payloadAnnotation.arguments
            .find { it.name?.asString() == C.STR_VALUE_ARG }
            ?.value as? String
            ?: run {
                logger.error(C.STR_ERR_ENVELOPE_PAYLOAD_VALUE, classDeclaration)
                return null
            }

        val targetType = payloadAnnotation.arguments
            .find { it.name?.asString() == C.ARG_ENVELOPE_TARGET }
            ?.value as? KSType

        val targetClassName = if (targetType != null && !isSentinelTarget(targetType)) {
            targetType.toClassName()
        } else {
            null
        }

        return EnvelopePayloadMapping(
            discriminatorValue = value,
            kotlinName = prop.kotlinName,
            isRawJson = prop.type.isRawJson(),
            targetType = targetClassName
        )
    }

    private fun isSentinelTarget(type: KSType): Boolean {
        val name = type.declaration.qualifiedName?.asString()
        return name == C.K_UNIT || name == C.K_NOTHING
    }
}
