package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Code generation helpers for [@GhostWrappedKeys][com.ghost.serialization.annotations.GhostWrappedKeys].
 */
internal object WrappedKeysEmitter {

    fun captureVariableName(prop: GhostPropertyModel): String =
        C.STR_WRAPPED_CAPTURE_PREFIX + prop.kotlinName

    fun hasWrappedProperties(properties: List<GhostPropertyModel>): Boolean =
        properties.any { it.wrappedSourceKeys != null }

    fun wrappedKeyDispatch(properties: List<GhostPropertyModel>): Map<String, Pair<GhostPropertyModel, Int>> {
        val map = linkedMapOf<String, Pair<GhostPropertyModel, Int>>()
        properties.forEach { prop ->
            val keys = prop.wrappedSourceKeys ?: return@forEach
            keys.forEachIndexed { index, key ->
                map[key] = prop to index
            }
        }
        return map
    }

    fun emitCaptureVariables(body: CodeBlock.Builder, properties: List<GhostPropertyModel>) {
        properties.forEach { prop ->
            val keys = prop.wrappedSourceKeys ?: return@forEach
            body.addStatement(
                C.TEMPLATE_WRAPPED_CAPTURE_VAR,
                captureVariableName(prop),
                ClassName(C.PKG_PARSER, C.STR_GHOST_WRAPPED_KEYS_CAPTURE),
                keys.size,
            )
        }
    }

    fun emitWrappedKeyCapture(
        body: CodeBlock.Builder,
        prop: GhostPropertyModel,
        keyIndex: Int,
    ) {
        body.addStatement(
            C.TEMPLATE_CAPTURE_WRAPPED_KEY,
            captureVariableName(prop),
            keyIndex,
        )
    }

    fun emitPostLoopDeserialization(
        body: CodeBlock.Builder,
        properties: List<GhostPropertyModel>,
        propertyIndices: Map<GhostPropertyModel, Int>,
        readerClass: ClassName,
    ) {
        properties.forEach { prop ->
            val keys = prop.wrappedSourceKeys ?: return@forEach
            val index = propertyIndices[prop] ?: return@forEach
            val captureName = captureVariableName(prop)
            val maskIdx = index / C.MASK_SIZE_BITS.toInt()
            val constName = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
            val varName = C.TEMPLATE_VAR_NAME.format(prop.kotlinName)
            val keyLiteralsName = wrappedKeyLiteralsConstantName(prop)
            val omitAbsentName = wrappedOmitAbsentConstantName(prop)

            body.addStatement(
                C.TEMPLATE_WRAPPED_JSON_MATERIALIZE,
                prop.kotlinName,
                captureName,
                keyLiteralsName,
                prop.wrappedOmitIfEmpty,
                omitAbsentName,
            )
            body.beginControlFlow(C.TEMPLATE_WRAPPED_JSON_IF_NOT_NULL, prop.kotlinName)
            body.addStatement(
                C.TEMPLATE_WRAPPED_READER_VAR,
                readerClass,
                prop.kotlinName,
            )
            val call = CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_WRAPPED_READER,
                prop.type.serializerClassName(),
            )
            if (prop.isResilient) {
                body.beginControlFlow(C.TEMPLATE_DECODE_RESILIENT, call)
                body.addStatement(varName + C.TEMPLATE_ASSIGN_L, C.STR_IT)
                body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
                body.endControlFlow()
            } else {
                body.addStatement(varName + C.TEMPLATE_ASSIGN_L, call)
                body.addStatement(C.STR_MASK_BITWISE_OR, maskIdx, maskIdx, constName)
            }
            body.nextControlFlow(C.STR_ELSE)
            if (prop.isNullable) {
                body.addStatement(C.TEMPLATE_NULL_ASSIGN, varName)
            }
            body.endControlFlow()
        }
    }

    fun addWrappedKeyConstants(
        typeSpecBuilder: TypeSpec.Builder,
        properties: List<GhostPropertyModel>,
    ) {
        properties.forEach { prop ->
            val keys = prop.wrappedSourceKeys ?: return@forEach
            val literalsName = wrappedKeyLiteralsConstantName(prop)
            if (typeSpecBuilder.propertySpecs.any { it.name == literalsName }) {
                return@forEach
            }

            val initializer = CodeBlock.builder()
                .add(C.TEMPLATE_ARRAY_OF_OPEN)
            keys.forEachIndexed { index, key ->
                if (index > 0) {
                    initializer.add(C.STR_COMMA_NEWLINE)
                }
                val quotedKey = C.STR_JSON_KEY_QUOTE + key + C.STR_JSON_KEY_COLON_SUFFIX
                initializer.add(C.TEMPLATE_WRAPPED_KEY_LITERAL_BYTE, quotedKey)
            }
            initializer.add(C.STR_NEWLINE_CLOSE_PAREN)

            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    literalsName,
                    Array::class.asClassName().parameterizedBy(ByteArray::class.asClassName()),
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(initializer.build())
                    .build(),
            )

            val omitAbsentName = wrappedOmitAbsentConstantName(prop)
            val absentIndices = prop.wrappedOmitIfAbsent.map { absentKey ->
                keys.indexOf(absentKey)
            }.filter { it >= 0 }

            typeSpecBuilder.addProperty(
                PropertySpec.builder(omitAbsentName, IntArray::class)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(
                        C.TEMPLATE_INT_ARRAY_OF,
                        absentIndices.joinToString(C.STR_COMMA),
                    )
                    .build(),
            )
        }
    }

    private fun wrappedKeyLiteralsConstantName(prop: GhostPropertyModel): String =
        C.STR_WRAPPED_KEY_LITERALS_PREFIX + prop.kotlinName.uppercase()

    private fun wrappedOmitAbsentConstantName(prop: GhostPropertyModel): String =
        C.STR_WRAPPED_OMIT_ABSENT_PREFIX + prop.kotlinName.uppercase()
}
