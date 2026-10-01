package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.CustomCoderModel
import com.ghost.serialization.compiler.model.CustomCoderReaderKind
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC

/**
 * Resolves `@GhostDecoder`/`@GhostEncoder` custom coders, including which reader channels
 * (string/flat/bytes) the provided function actually overloads for. Split out of [GhostAnalyzer]
 * since this changes for a different reason — custom-coder resolution — than class validation or
 * wrapped-keys handling does.
 */
internal class CustomCoderAnalyzer(private val logger: KSPLogger) {

    fun resolveCustomCoder(
        prop: KSPropertyDeclaration,
        annotationName: String
    ): CustomCoderModel? {
        return prop.annotations.find {
            it.shortName.asString() == annotationName
        }?.let { ann ->
            val provider =
                ann.arguments.find { it.name?.asString() == AC.PROVIDER_ARG }?.value as? KSType
            val function =
                ann.arguments.find { it.name?.asString() == AC.FUNCTION_NAME_ARG }?.value as? String
            if (provider != null && function != null) {
                CustomCoderModel(
                    provider = provider.toTypeName(),
                    functionName = function,
                    readerKinds = resolveCustomCoderReaderKinds(provider = provider, functionName = function),
                )
            } else null
        }
    }

    fun warnIfCustomCoder(
        propName: String,
        customDecoder: CustomCoderModel?,
        customEncoder: CustomCoderModel?
    ) {
        if (customDecoder != null || customEncoder != null) {
            logger.info(
                AC.STR_WARN_CUSTOM_CODER.format(
                    propName,
                    customDecoder,
                    customEncoder
                )
            )
        }
    }

    private fun resolveCustomCoderReaderKinds(
        provider: KSType,
        functionName: String,
    ): Set<CustomCoderReaderKind> {
        val declaration =
            provider.declaration as? KSClassDeclaration ?: return setOf(CustomCoderReaderKind.BYTES)
        val kinds = declaration.getAllFunctions()
            .filter { it.simpleName.asString() == functionName }
            .mapNotNull { fn ->
                when (fn.parameters.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString()) {
                    AC.STR_GHOST_JSON_STRING_READER_QUALIFIED -> CustomCoderReaderKind.STRING
                    AC.STR_GHOST_JSON_FLAT_READER_QUALIFIED -> CustomCoderReaderKind.FLAT
                    AC.STR_GHOST_JSON_READER_QUALIFIED -> CustomCoderReaderKind.BYTES
                    else -> null
                }
            }
            .toSet()
        return kinds.ifEmpty { setOf(CustomCoderReaderKind.BYTES) }
    }
}
