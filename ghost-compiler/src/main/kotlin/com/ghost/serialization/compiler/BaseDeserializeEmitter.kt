package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Base class for all deserialization emitters.
 *
 * Provides shared state and common helper methods for generating code related to
 * reading types from GhostJsonReader and building calls to other serializers.
 */
internal abstract class BaseDeserializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val readerClass: ClassName
) {
    protected fun buildCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.isNullable) return buildNullableCall(prop)

        return when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                buildCall(prop.valueClassProperty)
            }

            prop.isSealedClass -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                serializerName(prop.type)
            )
            prop.isPrimitiveArray -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}${C.STR_SERIALIZER}"
                )
            )

            else -> buildTypeReaderCall(prop.type)
        }
    }

    protected fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        return when {
            prop.isGhost || prop.isEnum -> CodeBlock.of(
                C.STR_NULL_CHECK_1 + C.STR_NULL_CHECK_2,
                serializerName(prop.type)
            )

            prop.type.isPrimitiveInt() -> CodeBlock.of(C.STR_NULL_CHECK_INT)
            prop.type.isPrimitiveLong() -> CodeBlock.of(C.STR_NULL_CHECK_LONG)
            prop.type.isPrimitiveDouble() -> CodeBlock.of(C.STR_NULL_CHECK_DOUBLE)
            prop.type.isPrimitiveFloat() -> CodeBlock.of(C.STR_NULL_CHECK_FLOAT)
            prop.type.isPrimitiveBoolean() -> CodeBlock.of(C.STR_NULL_CHECK_BOOLEAN)
            prop.isPrimitiveArray -> nullGuarded(
                CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    ClassName(C.STR_SERIALIZERS_PKG, "${prop.primitiveArrayType}${C.STR_SERIALIZER}")
                )
            )

            else -> nullGuarded(buildTypeReaderCall(prop.type))
        }
    }

    protected fun buildTypeReaderCall(type: KSType): CodeBlock {
        return when {
            type.isGhost() || type.isEnum() -> CodeBlock.of(C.TEMPLATE_DESERIALIZE_T, serializerName(type))
            type.isPrimitiveInt() -> CodeBlock.of(C.STR_NEXT_INT)
            type.isPrimitiveBoolean() -> CodeBlock.of(C.STR_NEXT_BOOLEAN)
            type.isPrimitiveLong() -> CodeBlock.of(C.STR_NEXT_LONG)
            type.isPrimitiveDouble() -> CodeBlock.of(C.STR_NEXT_DOUBLE)
            type.isPrimitiveFloat() -> CodeBlock.of(C.STR_NEXT_FLOAT)
            type.isList() -> {
                val inner = type.arguments.firstOrNull()?.type?.resolve() ?: return CodeBlock.of(C.STR_NEXT_STRING)
                CodeBlock.of(C.STR_READ_LIST_TEMPLATE, buildTypeReaderCall(inner))
            }

            type.isMap() -> {
                val valueType = type.arguments.getOrNull(1)?.type?.resolve() ?: return CodeBlock.of(C.STR_NEXT_STRING)
                CodeBlock.of(C.STR_READ_MAP_TEMPLATE, buildTypeReaderCall(valueType))
            }

            else -> CodeBlock.of(C.STR_NEXT_STRING)
        }
    }

    protected fun nullGuarded(inner: CodeBlock): CodeBlock {
        return CodeBlock.of(C.TEMPLATE_NULL_CHECK_L, inner)
    }

    protected fun serializerName(type: KSType): ClassName =
        with(type.declaration as KSClassDeclaration) {
            val className = toClassName()
            return ClassName(
                className.packageName,
                "${className.simpleNames.joinToString(C.STR_UNDERSCORE)}${C.STR_SERIALIZER}"
            )
        }
}
