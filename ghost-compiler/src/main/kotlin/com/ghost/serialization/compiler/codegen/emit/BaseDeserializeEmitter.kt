package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.analysis.isByteArray
import com.ghost.serialization.compiler.analysis.isEnum
import com.ghost.serialization.compiler.analysis.isGhost
import com.ghost.serialization.compiler.analysis.isKotlinUnsignedPrimitive
import com.ghost.serialization.compiler.analysis.isList
import com.ghost.serialization.compiler.analysis.isMap
import com.ghost.serialization.compiler.analysis.isPrimitiveBoolean
import com.ghost.serialization.compiler.analysis.isPrimitiveByte
import com.ghost.serialization.compiler.analysis.isPrimitiveChar
import com.ghost.serialization.compiler.analysis.isPrimitiveDouble
import com.ghost.serialization.compiler.analysis.isPrimitiveFloat
import com.ghost.serialization.compiler.analysis.isPrimitiveInt
import com.ghost.serialization.compiler.analysis.isPrimitiveLong
import com.ghost.serialization.compiler.analysis.isPrimitiveShort
import com.ghost.serialization.compiler.analysis.isPrimitiveULong
import com.ghost.serialization.compiler.analysis.isRawJson
import com.ghost.serialization.compiler.analysis.isSet
import com.ghost.serialization.compiler.analysis.isString
import com.ghost.serialization.compiler.analysis.isValueClassType
import com.ghost.serialization.compiler.analysis.resolveValueClassInnerType
import com.ghost.serialization.compiler.analysis.serializerClassName
import com.ghost.serialization.compiler.model.CustomCoderModel
import com.ghost.serialization.compiler.model.CustomCoderReaderKind
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Base class for deserialization emitters; manages shared property masks, flattened paths,
 * and contextual serializers, and turns KSP symbols into [CodeBlock] reader calls.
 */
internal abstract class BaseDeserializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val readerClass: ClassName
) {

    private val contextualSerializerRegistry = ContextualSerializerRegistry()

    protected val maskCount = (properties.size + C.MASK_SIZE_BITS_MINUS_ONE) /
            C.MASK_SIZE_BITS.toInt()

    /** Flattened JSON path per property, e.g. `["user", "profile", "id"]` for a flat DTO field. */
    protected val fullPaths = properties.map {
        it.flattenPath ?: (it.wrapPath?.let { path ->
            path + it.jsonName
        } ?: listOf(it.jsonName))
    }

    /** Zero-based index per property, used to compute maskIdx/bitIdx for bitmasks. */
    protected val propertyIndices = properties
        .mapIndexed { index, prop -> prop to index }
        .toMap()

    /**
     * Bitmask of mandatory fields (non-nullable, no default), split across a [LongArray] to
     * support >64 properties. Generated code validates presence with one
     * `mask & requiredMask == requiredMask` check.
     */
    protected val requiredMasks: LongArray by lazy {
        val masks = LongArray(maskCount)
        properties.forEachIndexed { index, prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val maskIdx = index / C.MASK_SIZE_BITS.toInt()
                val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                masks[maskIdx] = masks[maskIdx] or (1L shl bitIdx)
            }
        }
        masks
    }

    /**
     * Bitmask of fields with a default value. When missing and its bit is set, the generated
     * deserializer omits the constructor argument so Kotlin's default parameter applies.
     */
    protected val defaultMasks: LongArray by lazy {
        val masks = LongArray(maskCount)
        properties.forEachIndexed { index, prop ->
            if (prop.hasDefaultValue) {
                val maskIdx = index / C.MASK_SIZE_BITS.toInt()
                val bitIdx = index % C.MASK_SIZE_BITS.toInt()
                masks[maskIdx] = masks[maskIdx] or (1L shl bitIdx)
            }
        }
        masks
    }

    /** Formats a bitmask as a literal; [Long.MIN_VALUE] can't be written as `-9223...L`, so it's special-cased. */
    protected fun formatMaskString(mask: Long): String {
        return if (mask == Long.MIN_VALUE) {
            C.STR_BIT_MASK_MIN_LONG
        } else {
            C.FMT_LONG_LITERAL.format(mask)
        }
    }

    /** Builds the reader call for a property, dispatching on decoder/nullability/type. */
    protected fun buildCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.customDecoder != null) {
            return buildCustomDecoderCall(prop)
        }
        if (prop.isNullable) {
            return buildNullableCall(prop)
        }

        return when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                buildCall(prop.valueClassProperty)
            }

            prop.isSealedClass -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                prop.type.serializerClassName()
            )

            prop.isPrimitiveArray -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                if (readerClass.simpleName.startsWith(C.STR_GHOST_YAML_PREFIX)) {
                    ClassName(
                        C.PKG_YAML_SERIALIZER,
                        C.TEMPLATE_YAML_ARRAY_SERIALIZER.format(prop.primitiveArrayType)
                    )
                } else {
                    ClassName(
                        C.STR_SERIALIZERS_PKG,
                        "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
                    )
                }
            )

            prop.isProto && prop.type.isPrimitiveLong() -> CodeBlock.of(C.STR_NEXT_LONG_PROTO_COERCED)

            prop.isProto && prop.type.isPrimitiveULong() -> CodeBlock.of(C.STR_NEXT_ULONG_PROTO_COERCED)

            prop.isProto && prop.type.isByteArray() -> CodeBlock.of(C.STR_DECODE_BASE64_STRING_CALL)

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
            }

            else -> buildTypeReaderCall(prop.type, prop.isProto)
        }
    }

    /** Reader call for a nullable property, wrapped in a null check. */
    protected fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        // customDecoder is handled in buildCall before nullability — never reaches here.
        if (prop.isPrimitiveArray) {
            val serializerClass = if (readerClass.simpleName.startsWith(C.STR_GHOST_YAML_PREFIX)) {
                ClassName(
                    C.PKG_YAML_SERIALIZER,
                    C.TEMPLATE_YAML_ARRAY_SERIALIZER.format(prop.primitiveArrayType)
                )
            } else {
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
                )
            }
            return nullGuarded(
                CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    serializerClass
                )
            )
        }

        if (prop.isProto && prop.type.isPrimitiveLong()) {
            return CodeBlock.of(C.STR_NEXT_LONG_PROTO_COERCED)
        }

        if (prop.isProto && prop.type.isPrimitiveULong()) {
            return CodeBlock.of(C.STR_NEXT_ULONG_PROTO_COERCED)
        }

        if (prop.isProto && prop.type.isByteArray()) {
            return CodeBlock.of(C.STR_DECODE_BASE64_STRING_CALL)
        }

        return buildTypeReaderCall(prop.type, prop.isProto)
    }

    /** Reader call that bridges to a property's custom decoder. */
    protected fun buildCustomDecoderCall(prop: GhostPropertyModel): CodeBlock {
        val coder = prop.customDecoder!!
        if (usesDirectCustomDecoderCall(coder)) {
            return CodeBlock.of(C.TEMPLATE_L_READER, coder.provider, coder.functionName)
        }
        return when (readerClass.simpleName) {
            C.STR_GHOST_JSON_FLAT_READER -> buildFlatReaderCustomDecoderBridge(coder)
            C.STR_GHOST_JSON_STRING_READER -> buildStringReaderCustomDecoderBridge(coder)
            else -> buildFlatReaderCustomDecoderBridge(coder)
        }
    }

    private fun usesDirectCustomDecoderCall(coder: CustomCoderModel): Boolean {
        val channelKind = when (readerClass.simpleName) {
            C.STR_GHOST_JSON_STRING_READER -> CustomCoderReaderKind.STRING
            C.STR_GHOST_JSON_FLAT_READER -> CustomCoderReaderKind.FLAT
            else -> CustomCoderReaderKind.BYTES
        }
        return coder.supports(channelKind)
    }

    private fun buildFlatReaderCustomDecoderBridge(coder: CustomCoderModel): CodeBlock {
        return CodeBlock.builder()
            .add(C.STR_RUN_OPEN)
            .add(C.STR_CUSTOM_DECODER_TEMP_READER)
            .add(C.TEMPLATE_CUSTOM_DECODER_TEMP_CALL, coder.provider, coder.functionName)
            .add(C.STR_CUSTOM_DECODER_UPDATE_POS)
            .add(C.STR_RESET_TOKEN_BYTE_CALL)
            .add(C.STR_CUSTOM_DECODER_RETURN_RES)
            .add(C.STR_RUN_CLOSE)
            .build()
    }

    private fun buildStringReaderCustomDecoderBridge(coder: CustomCoderModel): CodeBlock {
        return CodeBlock.builder()
            .add(C.STR_RUN_OPEN)
            .add(C.STR_CUSTOM_DECODER_TEMP_READER_STRING)
            .add(C.TEMPLATE_CUSTOM_DECODER_TEMP_CALL, coder.provider, coder.functionName)
            .add(C.STR_CUSTOM_DECODER_UPDATE_POS_STRING)
            .add(C.STR_CUSTOM_DECODER_RETURN_RES)
            .add(C.STR_RUN_CLOSE)
            .build()
    }

    /**
     * Recursively builds the reader call for a [KSType]: delegates to an existing serializer for
     * Ghost/enum types, maps primitives to optimized reader methods, recurses into collections.
     *
     * @param isProto Propagated into collection element recursion so nested `Long`/`ByteArray`
     *   elements also get proto3 quoted-int64/Base64 decoding.
     */
    protected fun buildTypeReaderCall(type: KSType, isProto: Boolean = false): CodeBlock {
        return when {
            type.isRawJson() -> {
                val call = CodeBlock.of(C.STR_RAW_JSON_FROM_CAPTURE)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isByteArray() -> {
                val call = if (isProto) {
                    CodeBlock.of(C.STR_DECODE_BASE64_STRING_CALL)
                } else {
                    CodeBlock.of(C.STR_CAPTURE_RAW_JSON_BYTES)
                }
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isValueClassType() && !type.isKotlinUnsignedPrimitive() -> {
                val innerType = type.resolveValueClassInnerType()
                val call = if (innerType != null) {
                    val constructorCall = buildTypeReaderCall(innerType, isProto)
                    val className =
                        type.declaration.qualifiedName?.asString()?.let { ClassName.bestGuess(it) }
                            ?: type.toTypeName()
                    CodeBlock.of(C.TEMPLATE_CONSTRUCTOR, className, constructorCall)
                } else {
                    val name = getContextualSerializerName(type)
                    CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
                }
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isGhost() || type.isEnum() -> {
                val call = CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    type.serializerClassName()
                )
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isPrimitiveInt() -> scalarReaderCall(
                C.STR_NEXT_INT,
                C.STR_NEXT_INT_OR_NULL,
                type.isMarkedNullable
            )

            type.isPrimitiveBoolean() -> scalarReaderCall(
                C.STR_NEXT_BOOLEAN,
                C.STR_NEXT_BOOLEAN_OR_NULL,
                type.isMarkedNullable
            )

            type.isPrimitiveLong() -> if (isProto) {
                // Quoted int64 coercion — keep generic null guard around the run block.
                val call = CodeBlock.of(C.STR_NEXT_LONG_PROTO_COERCED)
                if (type.isMarkedNullable) nullGuarded(call) else call
            } else {
                scalarReaderCall(
                    C.STR_NEXT_LONG,
                    C.STR_NEXT_LONG_OR_NULL,
                    type.isMarkedNullable
                )
            }

            type.isPrimitiveULong() -> if (isProto) {
                val call = CodeBlock.of(C.STR_NEXT_ULONG_PROTO_COERCED)
                if (type.isMarkedNullable) nullGuarded(call) else call
            } else {
                scalarReaderCall(
                    C.STR_NEXT_ULONG,
                    C.STR_NEXT_ULONG_OR_NULL,
                    type.isMarkedNullable
                )
            }

            type.isPrimitiveDouble() -> {
                val call = CodeBlock.of(C.STR_NEXT_DOUBLE)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isPrimitiveFloat() -> {
                val call = CodeBlock.of(C.STR_NEXT_FLOAT)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isPrimitiveByte() -> {
                val call = CodeBlock.of(C.STR_NEXT_BYTE)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isPrimitiveShort() -> {
                val call = CodeBlock.of(C.STR_NEXT_SHORT)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isPrimitiveChar() -> {
                val call = CodeBlock.of(C.STR_NEXT_CHAR)
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isSet() -> {
                val inner = type.arguments.firstOrNull()?.type?.resolve()
                    ?: return scalarReaderCall(
                        C.STR_NEXT_STRING,
                        C.STR_NEXT_STRING_OR_NULL,
                        type.isMarkedNullable
                    )

                val call = CodeBlock.of(
                    C.STR_READ_SET_TEMPLATE,
                    buildTypeReaderCall(inner, isProto)
                )
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isList() -> {
                val inner = type.arguments.firstOrNull()?.type?.resolve()
                    ?: return scalarReaderCall(
                        C.STR_NEXT_STRING,
                        C.STR_NEXT_STRING_OR_NULL,
                        type.isMarkedNullable
                    )

                val call = CodeBlock.of(
                    C.STR_READ_LIST_TEMPLATE,
                    buildTypeReaderCall(inner, isProto)
                )
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            type.isMap() -> {
                val valueType = type
                    .arguments
                    .getOrNull(1)
                    ?.type?.resolve()
                    ?: return scalarReaderCall(
                        C.STR_NEXT_STRING,
                        C.STR_NEXT_STRING_OR_NULL,
                        type.isMarkedNullable
                    )

                val call = CodeBlock.of(
                    C.STR_READ_MAP_TEMPLATE,
                    buildTypeReaderCall(valueType, isProto)
                )
                if (type.isMarkedNullable) nullGuarded(call) else call
            }

            else -> {
                if (type.isString()) {
                    scalarReaderCall(
                        C.STR_NEXT_STRING,
                        C.STR_NEXT_STRING_OR_NULL,
                        type.isMarkedNullable
                    )
                } else {
                    val name = getContextualSerializerName(type)
                    val call = CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
                    if (type.isMarkedNullable) nullGuarded(call) else call
                }
            }
        }
    }

    /**
     * Emits a fused `nextXOrNull()`/`nextX()` call, avoiding a separate
     * `isNextNullValue` + `consumeNull` + `else nextX` branch for scalars.
     */
    private fun scalarReaderCall(
        nonNullCall: String,
        orNullCall: String,
        nullable: Boolean
    ): CodeBlock =
        CodeBlock.of(if (nullable) orNullCall else nonNullCall)

    /** Unique variable name for a contextual serializer, e.g. "User" -> "contextualUserSerializer". */
    private fun getContextualSerializerName(type: KSType): String =
        contextualSerializerRegistry.nameFor(type)

    /**
     * Registers named private bitmask constants for every property, avoiding magic numbers in
     * generated code.
     *
     * @param emitRequiredAggregateMasks When false, skip aggregate `MASK_REQUIRED_N` constants
     * (e.g. a single required field validates via the property mask alone).
     */
    protected fun emitPropertyMaskConstants(
        typeSpecBuilder: TypeSpec.Builder,
        emitRequiredAggregateMasks: Boolean = true,
    ) {
        properties.forEach { prop ->
            val index = propertyIndices[prop]!!
            val bitIdx = index % C.MASK_SIZE_BITS.toInt()
            val bitMask = C.VAL_ONE_L shl bitIdx
            val bitMaskStr = formatMaskString(bitMask)
            val name = C.STR_MASK_PREFIX + prop.kotlinName.uppercase()
            if (typeSpecBuilder.propertySpecs.none { it.name == name }) {
                typeSpecBuilder.addProperty(
                    PropertySpec.builder(name, com.squareup.kotlinpoet.LONG)
                        .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                        .initializer(C.TEMPLATE_L, bitMaskStr)
                        .build()
                )
            }
        }

        if (!emitRequiredAggregateMasks) {
            return
        }

        for (i in requiredMasks.indices) {
            val reqMask = requiredMasks[i]
            if (reqMask != C.VAL_ZERO_L) {
                val reqMaskStr = formatMaskString(reqMask)
                val name = C.STR_MASK_REQUIRED_PREFIX + i
                if (typeSpecBuilder.propertySpecs.none { it.name == name }) {
                    typeSpecBuilder.addProperty(
                        PropertySpec.builder(name, com.squareup.kotlinpoet.LONG)
                            .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                            .initializer(C.TEMPLATE_L, reqMaskStr)
                            .build()
                    )
                }
            }
        }
    }

    /** Registers a `MASK_DEFAULTS_N` constant for the copy-based default-value return path. */
    protected fun emitDefaultMaskConstant(
        typeSpecBuilder: TypeSpec.Builder,
        maskIndex: Int,
    ): String {
        val defMask = defaultMasks[maskIndex]
        val constName = "${C.STR_MASK_DEFAULTS_PREFIX}$maskIndex"
        if (defMask != C.VAL_ZERO_L &&
            typeSpecBuilder.propertySpecs.none { it.name == constName }
        ) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(constName, com.squareup.kotlinpoet.LONG)
                    .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                    .initializer(C.TEMPLATE_L, formatMaskString(defMask))
                    .build()
            )
        }
        return constName
    }

    /** Injects private fields for contextual serializers, resolved at compile time instead of via reflection. */
    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) =
        contextualSerializerRegistry.injectInto(typeSpecBuilder)

    /** Wraps a reader call in `if (reader.isNextNullValue()) null else ...`. */
    protected fun nullGuarded(inner: CodeBlock): CodeBlock =
        CodeBlock.of(C.TEMPLATE_NULL_CHECK_L, inner)
}
