package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.analysis.isEnum
import com.ghost.serialization.compiler.analysis.isGhost
import com.ghost.serialization.compiler.analysis.isKotlinUnsignedPrimitive
import com.ghost.serialization.compiler.analysis.isList
import com.ghost.serialization.compiler.analysis.isMap
import com.ghost.serialization.compiler.analysis.isRawJson
import com.ghost.serialization.compiler.analysis.isSet
import com.ghost.serialization.compiler.analysis.isValueClassType
import com.ghost.serialization.compiler.analysis.resolveValueClassInnerType
import com.ghost.serialization.compiler.analysis.serializerClassName
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Base class for serialization emitters; manages contextual serializers and generates code for
 * properties, collections, primitives, value/inline classes, and custom-encoded fields.
 */
internal abstract class BaseSerializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val writerClass: ClassName
) {
    private val contextualSerializerRegistry = ContextualSerializerRegistry()

    /**
     * Counter for unique loop variable names (`sizeN`, `iN`, `keyN`, `valN`). Nesting depth alone
     * isn't enough — sibling list/map fields at the same depth would collide on `size0`, `i0`, etc.
     */
    private var loopCounter = 0

    /** Whether the property can be written with a fused fast-path writer method, skipping [emitValue]. */
    protected fun isFusedType(prop: GhostPropertyModel): Boolean {
        if (prop.customEncoder != null) {
            return false
        }
        val type = prop.type.declaration.qualifiedName?.asString()
        // Proto3 requires int64 on the wire as a quoted string, so route Long through emitValue's
        // proto branch instead of the fused fast path, which always writes a bare number.
        val isProtoLongType = prop.isProto && (type == AC.K_LONG || type == AC.K_ULONG)
        if (isProtoLongType) {
            return false
        }
        return when (type) {
            AC.K_INT,
            AC.K_LONG,
            AC.K_ULONG,
            AC.K_STRING,
            AC.K_BOOLEAN,
            AC.K_DOUBLE,
            AC.K_FLOAT -> {
                true
            }

            else -> {
                false
            }
        }
    }

    /**
     * proto3 canonical JSON omits non-nullable scalar/collection fields holding their zero value
     * (`0`, `""`, `false`, empty). Nested Ghost/enum/RawJson/contextual types are left
     * unconditional since there's no reliable "is this the default instance" check.
     *
     * @return The guard condition, or `null` if this property isn't subject to omission.
     */
    private fun buildProtoNonDefaultCondition(
        prop: GhostPropertyModel,
        accessor: CodeBlock
    ): CodeBlock? {
        if (!prop.isProto || prop.customEncoder != null) {
            return null
        }

        val effective = if (prop.isValueClass && prop.valueClassProperty != null) {
            val inner = prop.valueClassProperty
            inner to CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, accessor, inner.kotlinName)
        } else {
            prop to accessor
        }
        val (targetProp, targetAccessor) = effective

        val isCollectionProperty = targetProp.isList || targetProp.isSet || targetProp.isMap
        if (isCollectionProperty) {
            return CodeBlock.of(C.TEMPLATE_IS_NOT_EMPTY, targetAccessor)
        }
        val typeName = targetProp.type.declaration.qualifiedName?.asString()
        return when (typeName) {
            AC.K_INT -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_INT, targetAccessor)
            AC.K_LONG -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_LONG, targetAccessor)
            AC.K_ULONG -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_ULONG, targetAccessor)
            AC.K_DOUBLE -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_DOUBLE, targetAccessor)
            AC.K_FLOAT -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_FLOAT, targetAccessor)
            AC.K_SHORT -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_SHORT, targetAccessor)
            AC.K_BYTE -> CodeBlock.of(C.TEMPLATE_NEQ_ZERO_BYTE, targetAccessor)
            AC.K_BOOLEAN -> CodeBlock.of(C.TEMPLATE_L, targetAccessor)
            AC.K_STRING -> CodeBlock.of(C.TEMPLATE_IS_NOT_EMPTY, targetAccessor)
            AC.K_BYTE_ARRAY -> CodeBlock.of(C.TEMPLATE_IS_NOT_EMPTY, targetAccessor)
            else -> null
        }
    }

    /** Writes a property's key, applies nullable/proto3-omission checks, then delegates to [emitValue]. */
    fun emitProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        if (prop.wrappedKeys != null) {
            emitWrappedKeysProperty(code = code, prop = prop)
            return
        }

        val cleanName = prop.jsonName
            .replace(CC.STR_DOT, CC.STR_UNDERSCORE)
            .uppercase()

        val isStringWriter = writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER
        val headerName = if (isStringWriter) {
            CG.STR_HS_PREFIX + cleanName
        } else {
            CG.STR_H_VAL_PREFIX + cleanName
        }
        val accessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)

        if (!prop.isNullable) {
            val nonDefaultCondition = buildProtoNonDefaultCondition(prop = prop, accessor = accessor)
            if (nonDefaultCondition != null) {
                code.beginControlFlow(C.TEMPLATE_IF_L, nonDefaultCondition)
                emitNonNullProperty(code = code, prop = prop, headerName = headerName, accessor = accessor)
                code.endControlFlow()
                return
            }
        }

        if (prop.isNullable) {
            emitNullableProperty(code = code, prop = prop, headerName = headerName, accessor = accessor)
            return
        }

        emitNonNullProperty(code = code, prop = prop, headerName = headerName, accessor = accessor)
    }

    private fun buildSealedSubclassFieldAccessor(
        wrapperName: String,
        subclassName: ClassName,
        path: List<String>
    ): CodeBlock {
        val wrapperAccessor = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, wrapperName)
        var expr = CodeBlock.of(C.TEMPLATE_CAST, wrapperAccessor, subclassName)
        for (segment in path) {
            expr = CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, expr, segment)
        }
        return expr
    }

    private fun buildWrappedPathAccessor(wrapperName: String, path: List<String>): CodeBlock {
        var expr = CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, C.STR_PARAM_VALUE, wrapperName)
        for (segment in path) {
            expr = CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, expr, segment)
        }
        return expr
    }

    private fun emitNonNullProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        headerName: String,
        accessor: CodeBlock
    ) {
        val canUseFused = isFusedType(prop = prop) && !prop.isContextual
        if (canUseFused) {
            code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
        } else {
            code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
            emitValue(code = code, prop = prop, accessor = accessor)
        }
    }

    private fun emitNullableProperty(
        code: CodeBlock.Builder,
        prop: GhostPropertyModel,
        headerName: String,
        accessor: CodeBlock
    ) {
        val canUseFused = isFusedType(prop = prop) && !prop.isContextual
        if (prop.hasDefaultValue) {
            code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
            if (canUseFused) {
                code.addStatement(C.STR_WRITE_FIELD, headerName, accessor)
            } else {
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitValue(code = code, prop = prop, accessor = accessor)
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
                emitValue(code = code, prop = prop, accessor = accessor)
                code.nextControlFlow(C.STR_ELSE)
                code.addStatement(C.STR_WRITER_NULL_VAL)
                code.endControlFlow()
            }
        }
    }

    /** Unwraps a `@GhostWrappedKeys` property, writing each wire field at the current JSON object level. */
    private fun emitWrappedKeysProperty(code: CodeBlock.Builder, prop: GhostPropertyModel) {
        val accessorRoot = CodeBlock.of(C.TEMPLATE_ACCESSOR, C.STR_PARAM_VALUE, prop.kotlinName)
        val isStringWriter = writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER
        val prefix = if (isStringWriter) {
            CG.STR_HS_PREFIX
        } else {
            CG.STR_H_VAL_PREFIX
        }

        if (prop.isNullable || prop.wrappedKeys?.omitIfEmpty == true) {
            code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessorRoot)
        }

        prop.wrappedKeys?.unwrapFields.orEmpty().forEach { field ->
            val headerName =
                prefix + field.jsonName.replace(CC.STR_DOT, CC.STR_UNDERSCORE).uppercase()

            // proto3 oneof: this wire key lives on one sealed subclass, not the parent — guard
            // with an `is` smart-cast instead of a plain path accessor.
            if (field.sealedSubclassName != null) {
                val accessor = buildSealedSubclassFieldAccessor(
                    wrapperName = prop.kotlinName,
                    subclassName = field.sealedSubclassName,
                    path = field.kotlinPath
                )
                code.beginControlFlow(
                    C.TEMPLATE_IF_L,
                    CodeBlock.of(C.TEMPLATE_IS_INSTANCE, accessorRoot, field.sealedSubclassName)
                )
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitTypeValue(code = code, type = field.type, accessor = accessor, skipNullCheck = true)
                code.endControlFlow()
                return@forEach
            }

            val accessor = buildWrappedPathAccessor(wrapperName = prop.kotlinName, path = field.kotlinPath)
            if (field.isNullable) {
                code.beginControlFlow(C.TEMPLATE_IF_NOT_NULL, accessor)
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitTypeValue(code = code, type = field.type, accessor = accessor, skipNullCheck = true)
                code.endControlFlow()
            } else {
                code.addStatement(C.STR_WRITE_NAME_RAW, headerName)
                emitTypeValue(code = code, type = field.type, accessor = accessor, skipNullCheck = true)
            }
        }

        if (prop.isNullable || prop.wrappedKeys?.omitIfEmpty == true) {
            code.endControlFlow()
        }
    }

    /**
     * Recursively resolves the serialization call for a [KSType].
     *
     * @param skipNullCheck True if the outer check has already guaranteed a non-null value.
     * @param isProto Propagated into collection element recursion so nested `Long`/`ByteArray`
     *   elements also get proto3 quoting/Base64 treatment.
     */
    protected fun emitTypeValue(
        code: CodeBlock.Builder,
        type: KSType,
        accessor: Any,
        skipNullCheck: Boolean = false,
        isProto: Boolean = false
    ) {
        val isNullable = type.isMarkedNullable
        if (isNullable && !skipNullCheck) {
            code.beginControlFlow(C.TEMPLATE_IF_NULL, accessor)
            code.addStatement(C.STR_WRITER_NULL_VAL)
            code.nextControlFlow(C.STR_ELSE)
        }

        if (type.isValueClassType() && !type.isKotlinUnsignedPrimitive()) {
            val innerType = type.resolveValueClassInnerType()
            if (innerType != null) {
                val valueClassProperty =
                    (type.declaration as? com.google.devtools.ksp.symbol.KSClassDeclaration)
                        ?.primaryConstructor?.parameters?.firstOrNull()?.name?.asString()
                if (valueClassProperty != null) {
                    val innerAccessor =
                        CodeBlock.of(C.TEMPLATE_CHAINED_MEMBER, accessor, valueClassProperty)
                    emitTypeValue(
                        code = code,
                        type = innerType,
                        accessor = innerAccessor,
                        skipNullCheck = true,
                        isProto = isProto
                    )
                    if (isNullable && !skipNullCheck) {
                        code.endControlFlow()
                    }
                    return
                }
            }
        }

        val scalarTemplate = ScalarWriteTemplates.templateFor(
            qualifiedName = type.declaration.qualifiedName?.asString(),
            isProto = isProto
        )
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

            scalarTemplate != null -> {
                code.addStatement(scalarTemplate, accessor)
            }

            type.isList() -> {
                emitList(code = code, type = type, accessor = accessor, isProto = isProto)
            }

            type.isSet() -> {
                emitSet(code = code, type = type, accessor = accessor, isProto = isProto)
            }

            type.isMap() -> {
                emitMap(code = code, type = type, accessor = accessor, isProto = isProto)
            }

            else -> {
                val name = getContextualSerializerName(type = type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }
        }

        if (isNullable && !skipNullCheck) {
            code.endControlFlow()
        }
    }

    /** Emits the value-write statement for a property, dispatching by kind (custom encoder, value class, sealed, etc). */
    fun emitValue(code: CodeBlock.Builder, prop: GhostPropertyModel, accessor: Any) {
        if (prop.customEncoder != null) {
            if (writerClass.simpleName == C.STR_GHOST_JSON_STRING_WRITER) {
                val flatWriter = ClassName(CC.PKG_WRITER_BYTES, C.STR_GHOST_JSON_WRITER)
                val flatBuffer = ClassName(CC.PKG_WRITER_BYTES, C.STR_FLAT_BYTE_ARRAY_WRITER)
                val bridgeWriterName = C.STR_TEMP_FLAT_WRITER
                val bridgeBufferName = C.STR_TEMP_FLAT_BUFFER
                code.addStatement(C.TEMPLATE_TEMP_FLAT_BUFFER_DECL, bridgeBufferName, flatBuffer)
                code.addStatement(
                    C.TEMPLATE_TEMP_FLAT_WRITER_DECL,
                    bridgeWriterName,
                    flatWriter,
                    bridgeBufferName
                )
                code.addStatement(
                    C.TEMPLATE_CUSTOM_ENCODER_BRIDGE_CALL,
                    prop.customEncoder.provider,
                    prop.customEncoder.functionName,
                    bridgeWriterName,
                    accessor
                )
                code.addStatement(C.TEMPLATE_WRITE_STRING_CHANNEL_BRIDGE, bridgeBufferName)
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
                emitValue(code = code, prop = prop.valueClassProperty, accessor = innerAccessor)
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
                    primitiveArraySerializerClass(
                        channelClass = writerClass,
                        primitiveArrayType = prop.primitiveArrayType
                    ),
                    accessor
                )
            }

            prop.isContextual -> {
                val name = getContextualSerializerName(type = prop.type)
                code.addStatement(C.STR_SERIALIZE_CALL, name, accessor)
            }

            prop.isProto && prop.type.declaration.qualifiedName?.asString() == AC.K_LONG -> {
                code.addStatement(C.STR_WRITER_VAL_LONG_AS_STRING, accessor)
            }

            prop.isProto && prop.type.declaration.qualifiedName?.asString() == AC.K_ULONG -> {
                code.addStatement(C.STR_WRITER_VAL_LONG_AS_STRING, accessor)
            }

            prop.isProto && prop.type.declaration.qualifiedName?.asString() == AC.K_BYTE_ARRAY -> {
                code.addStatement(C.STR_WRITER_VAL_BYTES_AS_BASE64, accessor)
            }

            else -> {
                emitTypeValue(
                    code = code,
                    type = prop.type,
                    accessor = accessor,
                    skipNullCheck = true,
                    isProto = prop.isProto
                )
            }
        }
    }

    private fun emitList(
        code: CodeBlock.Builder,
        type: KSType,
        accessor: Any,
        isProto: Boolean = false
    ) {
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
            emitTypeValue(code = code, type = innerType, accessor = itemVar, skipNullCheck = false, isProto = isProto)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, itemVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_ARR)
    }

    private fun emitMap(
        code: CodeBlock.Builder,
        type: KSType,
        accessor: Any,
        isProto: Boolean = false
    ) {
        val slot = loopCounter++
        val keyVar = C.STR_MAP_KEY_PREFIX + slot
        val valVar = C.STR_MAP_VAL_PREFIX + slot
        code.addStatement(C.STR_WRITER_BEGIN_OBJ)
        code.beginControlFlow(C.TEMPLATE_FOR_MAP, keyVar, valVar, accessor)
        code.addStatement(C.TEMPLATE_WRITER_NAME, keyVar)
        val valueType = type.arguments.getOrNull(1)?.type?.resolve()
        if (valueType != null) {
            emitTypeValue(code = code, type = valueType, accessor = valVar, skipNullCheck = false, isProto = isProto)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, valVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    /** Emits set serialization, iterating elements without materializing a [List]. */
    private fun emitSet(
        code: CodeBlock.Builder,
        type: KSType,
        accessor: Any,
        isProto: Boolean = false
    ) {
        val slot = loopCounter++
        val itemVar = C.STR_ITEM_PREFIX + slot
        code.addStatement(C.STR_WRITER_BEGIN_ARR)
        code.beginControlFlow("for (%L in %L)", itemVar, accessor)
        val innerType = type.arguments.firstOrNull()?.type?.resolve()

        if (innerType != null) {
            emitTypeValue(code = code, type = innerType, accessor = itemVar, skipNullCheck = false, isProto = isProto)
        } else {
            code.addStatement(C.TEMPLATE_WRITER_VALUE, itemVar)
        }
        code.endControlFlow()
        code.addStatement(C.STR_WRITER_END_ARR)
    }

    protected fun getContextualSerializerName(type: KSType): String =
        contextualSerializerRegistry.nameFor(type = type)

    /** Injects private fields for all resolved contextual serializers. */
    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) =
        contextualSerializerRegistry.injectInto(typeSpecBuilder = typeSpecBuilder)
}
