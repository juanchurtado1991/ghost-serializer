package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Abstract base class for all serialization emitters within the Ghost compiler.
 *
 * It manages shared state such as contextual serializers and provides common code
 * generation utilities for serializing properties, collections (lists, maps), primitive types,
 * value/inline classes, and custom-encoded fields.
 *
 * @property properties The list of [GhostPropertyModel] representing properties to serialize.
 * @property originalClassName The [ClassName] of the DTO class being serialized.
 * @property writerClass The [ClassName] of the JSON writer used (e.g., GhostJsonWriter).
 */
internal abstract class BaseSerializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val writerClass: ClassName
) {
    protected val contextualSerializers = mutableMapOf<KSType, String>()

    /**
     * Monotonically increasing counter to generate unique loop variable names
     * (`sizeN`, `iN`, `keyN`, `valN`) within a single serialization function scope.
     *
     * Using [depth] alone caused collisions when a DTO had multiple list/map fields
     * at the same nesting level (all start at depth=0 → duplicate `size0`, `i0`).
     */
    private var loopCounter = 0

    /**
     * Determines whether the property is of a primitive or basic type that can be serialized
     * using optimized fast-path writer methods directly.
     *
     * @param prop The property metadata model.
     * @return True if the property can be written using a direct/fused writer method call.
     */
    protected fun isFusedType(prop: GhostPropertyModel): Boolean {
        if (prop.customEncoder != null) {
            return false
        }
        val type = prop.type.declaration.qualifiedName?.asString()
        // Proto3 JSON mapping requires int64 fields on the wire as quoted decimal strings —
        // route Long through emitValue()'s dedicated proto branch instead of the fused
        // writer.writeField(header, Long) fast path, which always writes a bare number.
        if (prop.isProto && type == C.K_LONG) {
            return false
        }
        return when (type) {
            C.K_INT,
            C.K_LONG,
            C.K_STRING,
            C.K_BOOLEAN,
            C.K_DOUBLE,
            C.K_FLOAT -> {
                true
            }
            else -> {
                false
            }
        }
    }

    /**
     * Generates a single property serialization step.
     *
     * It writes the key name, resolves nullable checks, wraps conditional default arguments,
     * and delegates values writing to [emitValue].
     *
     * @param code The target [CodeBlock.Builder].
     * @param prop The property metadata model.
     */
    fun emitProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        if (prop.wrappedSourceKeys != null) {
            emitWrappedKeysProperty(code, prop)
            return
        }

        val cleanName = prop.jsonName
            .replace(C.STR_DOT, C.STR_UNDERSCORE)
            .uppercase()

        val isStringWriter = writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER
        val headerName = if (isStringWriter) {
            C.STR_HS_PREFIX + cleanName
        } else {
            C.STR_H_VAL_PREFIX + cleanName
        }
        val accessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)

        if (prop.isNullable) {
            emitNullableProperty(code, prop, headerName, accessor)
            return
        }

        emitNonNullProperty(code, prop, headerName, accessor)
    }

    /**
     * Unwraps a [@GhostWrappedKeys][com.ghost.serialization.annotations.GhostWrappedKeys]
     * property by writing each wire field at the current JSON object level.
     */
    private fun emitWrappedKeysProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        val accessorRoot = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)
        val isStringWriter = writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER
        val prefix = if (isStringWriter) {
            C.STR_HS_PREFIX
        } else {
            C.STR_H_VAL_PREFIX
        }

        if (prop.isNullable || prop.wrappedOmitIfEmpty) {
            code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessorRoot)
        }

        prop.wrappedUnwrapFields.forEach { field ->
            val headerName = prefix + field.jsonName.replace(C.STR_DOT, C.STR_UNDERSCORE).uppercase()
            val accessor = buildWrappedPathAccessor(prop.kotlinName, field.kotlinPath)
            if (field.isNullable) {
                code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitTypeValue(code, field.type, accessor, skipNullCheck = true)
                code.endControlFlow()
            } else {
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitTypeValue(code, field.type, accessor, skipNullCheck = true)
            }
        }

        if (prop.isNullable || prop.wrappedOmitIfEmpty) {
            code.endControlFlow()
        }
    }

    private fun buildWrappedPathAccessor(wrapperName: String, path: List<String>): CodeBlock {
        var expr = CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, C.STR_PARAM_VALUE, wrapperName)
        for (segment in path) {
            expr = CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, expr, segment)
        }
        return expr
    }

    /**
     * Helper to serialize a nullable property.
     */
    private fun emitNullableProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        headerName: String,
        accessor: CodeBlock
    ) {
        val canUseFused = isFusedType(prop) && !prop.isContextual
        if (prop.hasDefaultValue) {
            code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
            if (canUseFused) {
                code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
            } else {
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitValue(code, prop, accessor)
            }
            code.endControlFlow()
        } else {
            if (canUseFused) {
                code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
                code.nextControlFlow(C.STR_ELSE)
                code.addStatement(C.STR_WRITE_NAME_RAW_NULL, headerName)
                code.endControlFlow()
            } else {
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                emitValue(code, prop, accessor)
                code.nextControlFlow(C.STR_ELSE)
                code.addStatement(C.STR_WRITER_NULL_VAL)
                code.endControlFlow()
            }
        }
    }

    /**
     * Helper to serialize a non-nullable property.
     */
    private fun emitNonNullProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        headerName: String,
        accessor: CodeBlock
    ) {
        val canUseFused = isFusedType(prop) && !prop.isContextual
        if (canUseFused) {
            code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
        } else {
            code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
            emitValue(code, prop, accessor)
        }
    }

    /**
     * Outputs the value serialization statement.
     * Supports value classes, sealed classes, custom encoders, contextual serializers, and primitives.
     *
     * @param code The target [CodeBlock.Builder].
     * @param prop The property metadata model.
     * @param accessor The accessor expression string/object (e.g. `value.age`).
     */
    fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: Any) {
        if (prop.customEncoder != null) {
            if (writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER) {
                code.addStatement("val tempFlatWriter = com.ghost.serialization.writer.GhostJsonFlatWriter(com.ghost.serialization.writer.FlatByteArrayWriter())")
                code.addStatement(
                    "%T.%L(tempFlatWriter, %L)",
                    prop.customEncoder.provider,
                    prop.customEncoder.functionName,
                    accessor
                )
                code.addStatement("writer.buffer.writeString(tempFlatWriter.buffer.toStringUtf8())")
            } else {
                code.addStatement(
                    C.STR_CUSTOM_ENCODER_CALL,
                    prop.customEncoder.provider,
                    prop.customEncoder.functionName,
                    accessor
                )
            }
            return
        }
        when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                val innerAccessor = CodeBlock.of(
                    C.TEMPLATE_ACCESSOR,
                    accessor,
                    prop.valueClassProperty.kotlinName
                )
                emitValue(code, prop.valueClassProperty, innerAccessor)
            }

            prop.isSealedClass -> {
                code.addStatement(
                    C.STR_T_SERIALIZE_WRITER_ACC,
                    prop.type.serializerClassName(),
                    accessor
                )
            }

            prop.isPrimitiveArray -> {
                code.addStatement(
                    C.STR_T_SERIALIZE_WRITER_ACC,
                    ClassName(
                        C.STR_SERIALIZERS_PKG,
                        prop.primitiveArrayType + C.STR_SERIALIZER_SUFFIX
                    ),
                    accessor
                )
            }

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }

            prop.isProto && prop.type.declaration.qualifiedName?.asString() == C.K_LONG -> {
                code.addStatement(C.STR_WRITER_VAL_LONG_AS_STRING, accessor)
            }

            prop.isProto && prop.type.declaration.qualifiedName?.asString() == C.K_BYTE_ARRAY -> {
                code.addStatement(C.STR_WRITER_VAL_BYTES_AS_BASE64, accessor)
            }

            else -> {
                emitTypeValue(code, prop.type, accessor, skipNullCheck = true)
            }
        }
    }

    /**
     * Resolves the serialization call for raw type objects recursively.
     *
     * @param code The target [CodeBlock.Builder].
     * @param type The [KSType] of the target value.
     * @param accessor Accessor key expression.
     * @param skipNullCheck True if the outer check has already guaranteed non-null value.
     */
    protected fun emitTypeValue(
        code: CodeBlock.Builder,
        type: KSType,
        accessor: Any,
        skipNullCheck: Boolean = false
    ) {
        val isNullable = type.isMarkedNullable
        if (isNullable && !skipNullCheck) {
            code.beginControlFlow(C.TEMPLATE_IF_NULL, accessor)
            code.addStatement(C.STR_NULL_VAL_CALL)
            code.nextControlFlow(C.STR_ELSE)
        }

        val typeName = type.declaration.qualifiedName?.asString()
        when {
            type.isRawJson() -> {
                code.addStatement(
                    C.STR_WRITER_RAW_VALUE_SLICE,
                    accessor,
                    accessor,
                    accessor
                )
            }

            type.isGhost() -> {
                code.addStatement(
                    C.STR_T_SERIALIZE_WRITER_ACC,
                    type.serializerClassName(),
                    accessor
                )
            }

            type.isEnum() -> {
                code.addStatement(
                    C.STR_T_SERIALIZE_WRITER_ACC,
                    type.serializerClassName(),
                    accessor
                )
            }

            typeName == C.K_INT -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_LONG -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_STRING -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_BOOLEAN -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_DOUBLE -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_FLOAT -> {
                code.addStatement(C.STR_WRITER_VAL_FLOAT, accessor)
            }
            typeName == C.K_BYTE -> {
                code.addStatement("writer.value(%L.toInt())", accessor)
            }
            typeName == C.K_SHORT -> {
                code.addStatement("writer.value(%L.toInt())", accessor)
            }
            typeName == C.K_CHAR -> {
                code.addStatement(C.STR_WRITER_VAL_L, accessor)
            }
            typeName == C.K_BYTE_ARRAY -> {
                code.addStatement(C.STR_WRITER_RAW_VALUE_L, accessor)
            }
            type.isList() -> {
                emitList(code, type, accessor)
            }
            type.isSet() -> {
                emitSet(code, type, accessor)
            }
            type.isMap() -> {
                emitMap(code, type, accessor)
            }

            else -> {
                val name = getContextualSerializerName(type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }
        }

        if (isNullable && !skipNullCheck) {
            code.endControlFlow()
        }
    }

    /**
     * Emits list collection serialization statements.
     *
     * Variables are named using [loopCounter] (e.g. `size2`, `i2`, `item2`) to prevent
     * name collisions when a DTO contains multiple list fields or nested list structures
     * within the same generated function scope.
     */
    private fun emitList(code: CodeBlock.Builder, type: KSType, accessor: Any) {
        val slot = loopCounter++
        val sizeVar = "size$slot"
        val indexVar = "i$slot"
        val itemVar = C.STR_ITEM_PREFIX + slot
        code.addStatement(C.STR_WRITER_BEGIN_ARR)
        code.addStatement("val %L = %L.size", sizeVar, accessor)
        code.beginControlFlow("for (%L in 0 until %L)", indexVar, sizeVar)
        code.addStatement("val %L = %L[%L]", itemVar, accessor, indexVar)
        val innerType = type.arguments.firstOrNull()?.type?.resolve()

        if (innerType != null) {
            emitTypeValue(code, innerType, itemVar, skipNullCheck = false)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, itemVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_ARR)
    }

    /**
     * Emits set collection serialization — iterates elements without materializing a [List].
     */
    private fun emitSet(code: CodeBlock.Builder, type: KSType, accessor: Any) {
        val slot = loopCounter++
        val itemVar = C.STR_ITEM_PREFIX + slot
        code.addStatement(C.STR_WRITER_BEGIN_ARR)
        code.beginControlFlow("for (%L in %L)", itemVar, accessor)
        val innerType = type.arguments.firstOrNull()?.type?.resolve()

        if (innerType != null) {
            emitTypeValue(code, innerType, itemVar, skipNullCheck = false)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, itemVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_ARR)
    }

    /**
     * Emits map collection serialization statements.
     *
     * Variables are named using [loopCounter] (e.g. `key2`, `val2`) to prevent
     * name collisions when a DTO contains multiple map fields or nested map structures
     * within the same generated function scope.
     */
    private fun emitMap(code: CodeBlock.Builder, type: KSType, accessor: Any) {
        val slot = loopCounter++
        val keyVar = C.STR_MAP_KEY_PREFIX + slot
        val valVar = C.STR_MAP_VAL_PREFIX + slot
        code.addStatement(C.STR_WRITER_BEGIN_OBJ)
        code.beginControlFlow(C.TEMPLATE_FOR_MAP, keyVar, valVar, accessor)
        code.addStatement(C.TEMPLATE_WRITER_NAME, keyVar)
        val valueType = type.arguments.getOrNull(1)?.type?.resolve()
        if (valueType != null) {
            emitTypeValue(code, valueType, valVar, skipNullCheck = false)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, valVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    /**
     * Registers and caches the contextual serializer for the target type.
     */
    protected fun getContextualSerializerName(type: KSType): String {
        return contextualSerializers.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            val nullableSuffix = if (type.isMarkedNullable) "Nullable" else ""
            C.STR_CONTEXTUAL_PREFIX +
                simpleName.replaceFirstChar { it.lowercase() } +
                nullableSuffix +
                C.STR_SERIALIZER_SUFFIX
        }
    }

    /**
     * Injects the required private fields for all resolved contextual serializers.
     *
     * @param typeSpecBuilder The KotlinPoet companion [TypeSpec.Builder].
     */
    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(C.STR_GHOST_PKG, C.STR_GHOST_OBJ)

        contextualSerializers.forEach { (type, name) ->
            val nonNullableType = type.makeNotNullable()
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    name,
                    ClassName(C.STR_CONTRACT_PKG, C.STR_GHOST_SERIALIZER)
                        .parameterizedBy(nonNullableType.toTypeName()),
                    KModifier.PRIVATE
                )
                    .initializer(
                        C.TEMPLATE_RESOLVE_SERIALIZER,
                        ghostClass,
                        nonNullableType.toTypeName()
                    )
                    .build()
            )
        }
    }
}
