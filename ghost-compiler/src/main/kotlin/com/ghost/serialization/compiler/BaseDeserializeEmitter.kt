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
 * Abstract base class for all deserialization emitters within the Ghost compiler.
 *
 * This class serves as the foundation for generating deserialization logic. It manages
 * shared state and provides a suite of helper methods to transform KSP symbols into
 * executable [CodeBlock] instructions via KotlinPoet.
 *
 * ### Responsibilities:
 * - **Schema Validation:** Pre-calculates [requiredMasks] and [defaultMasks] using bitwise operations
 * to ensure data integrity during runtime with minimal CPU overhead.
 * - **Path Resolution:** Flattens nested property paths (e.g., "user.profile.id") to allow mapping
 * deeply nested JSON structures directly to flat DTOs.
 * - **Type Resolution:** Encapsulates the logic for determining which deserialization strategy
 * to apply (primitives vs. lists vs. recursive calls to other serializers).
 * - **Code Generation Helpers:** Provides common templates for `null` handling, custom decoders,
 * and contextual serializer injection.
 *
 * @param properties The list of [GhostPropertyModel] describing the fields of the target class.
 * @param originalClassName The [ClassName] of the DTO being deserialized.
 * @param readerClass The [ClassName] of the reader being used (e.g., GhostJsonReader or GhostJsonFlatReader).
 */
internal abstract class BaseDeserializeEmitter(
    protected val properties: List<GhostPropertyModel>,
    protected val originalClassName: ClassName,
    protected val readerClass: ClassName
) {

    protected val contextualSerializers = mutableMapOf<KSType, String>()

    protected val maskCount = (properties.size + C.MASK_SIZE_BITS_MINUS_ONE) /
            C.MASK_SIZE_BITS.toInt()

    /**
     * Resolves the flatten path for nested properties.
     * This allows Ghost to map deeply nested JSON structures (e.g., "user.profile.id")
     * directly to a flat DTO field without needing intermediate data classes.
     * This is an optimization to keep the domain model clean while maintaining
     * compatibility with non-flat JSON structures.
     */
    protected val fullPaths = properties.map {
        it.flattenPath ?: (it.wrapPath?.let { path ->
            path + it.jsonName
        } ?: listOf(it.jsonName))
    }

    /**
     * Maps each property model to its unique zero-based index.
     * This is essential for calculating the maskIdx and bitIdx used in bitwise operations.
     */
    protected val propertyIndices = properties
        .mapIndexed { index, prop -> prop to index }
        .toMap()

    /**
     * Calculates the validation bitmask for required fields of the DTO.
     *
     * This property identifies which fields must be present in the JSON during deserialization.
     * A field is considered mandatory if:
     * 1. It is not nullable (`!isNullable`).
     * 2. It does not have a default value (`!hasDefaultValue`).
     *
     * **Validation Architecture:**
     * The bits are distributed across a [LongArray] to support schemas with more than 64 properties.
     * Each field is assigned to a position (maskIdx, bitIdx) based on its index in the property list.
     *
     * During runtime, the generated serializer uses this mask to perform high-performance schema validation.
     * By comparing the received mask against this required mask using a binary AND operation
     * (`mask & requiredMask == requiredMask`), we validate the object integrity in a single CPU cycle.
     *
     * @return A [LongArray] where each set bit represents a mandatory field that must be validated
     * after the deserialization process completes.
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
     * Calculates the bitmask for properties that have defined default values.
     *
     * This mask identifies fields for which the generated deserializer can safely
     * fall back to the class-level default values if the property is missing from the JSON input.
     *
     * **Architectural Purpose:**
     * During the final return/instantiation stage, the generated deserializer uses this mask
     * to perform "Constructor Dispatching". If a field is missing, instead of throwing an error
     * (as it would for [requiredMasks]), it relies on this mask to know that it is safe
     * to omit the argument from the constructor call, allowing Kotlin's default
     * parameter logic to take over.
     *
     * @return A [LongArray] where each set bit represents a property that possesses a
     * default value, assisting in optimal constructor selection and object instantiation.
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

    /**
     * Formats a bitmask into a constant string literal for code generation.
     * Handles edge cases like [Long.MIN_VALUE] which requires specific formatting.
     */
    protected fun formatMaskString(mask: Long): String {
        return if (mask == Long.MIN_VALUE) {
            C.STR_BIT_MASK_MIN_LONG
        } else {
            C.FMT_LONG_LITERAL.format(mask)
        }
    }

    /**
     * The main entry point for generating the reader call for a specific property.
     * Handles dispatching to specialized logic based on property attributes
     * (e.g., custom decoders, nullability, sealed classes).
     */
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
                ClassName(
                    C.STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
                )
            )

            prop.isContextual -> {
                val name = getContextualSerializerName(prop.type)
                CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
            }

            else -> buildTypeReaderCall(prop.type)
        }
    }

    /**
     * Generates the code block for a nullable property.
     * It handles null-safety by wrapping the reader call in a null check template.
     */
    protected fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.customDecoder != null) {
            return nullGuarded(buildCustomDecoderCall(prop))
        }

        if (prop.isPrimitiveArray) {
            return nullGuarded(
                CodeBlock.of(
                    C.TEMPLATE_DESERIALIZE_T,
                    ClassName(
                        C.STR_SERIALIZERS_PKG,
                        "${prop.primitiveArrayType}${C.STR_SERIALIZER_SUFFIX}"
                    )
                )
            )
        }

        return buildTypeReaderCall(prop.type)
    }

    /**
     * Generates code for custom decoder implementations.
     * Handles the transition between standard reader and the custom decoding logic.
     */
    protected fun buildCustomDecoderCall(prop: GhostPropertyModel): CodeBlock {
        val coder = prop.customDecoder!!
        if (readerClass.simpleName == C.STR_GHOST_JSON_FLAT_READER) {
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
        if (readerClass.simpleName == C.STR_GHOST_JSON_STRING_READER) {
            return CodeBlock.builder()
                .add(C.STR_RUN_OPEN)
                .add(C.STR_CUSTOM_DECODER_TEMP_READER_STRING)
                .add(C.TEMPLATE_CUSTOM_DECODER_TEMP_CALL, coder.provider, coder.functionName)
                .add(C.STR_CUSTOM_DECODER_UPDATE_POS_STRING)
                .add(C.STR_CUSTOM_DECODER_RETURN_RES)
                .add(C.STR_RUN_CLOSE)
                .build()
        }
        return CodeBlock.of(C.TEMPLATE_L_READER, coder.provider, coder.functionName)
    }

    /**
     * Dispatches the correct [CodeBlock] to read a specific [KSType] from the JSON reader.
     * * This acts as the recursive entry point for the emitter. It follows these resolution rules:
     * 1. **Ghost/Enum types:** Delegates to an existing serializer (recursive call).
     * 2. **Primitives:** Maps directly to optimized `GhostJsonReader` methods (e.g., [kotlin.random.Random.Default.nextInt]).
     * 3. **Collections (List/Map):** Applies a recursive template to handle generic types.
     * 4. **Fallbacks:** Uses contextual resolution for unknown or custom types.
     *
     * @param type The KSP type being resolved.
     * @return A [CodeBlock] containing the optimized reader instruction.
     */
    protected fun buildTypeReaderCall(type: KSType): CodeBlock {
        val readerCall = when {
            type.isRawJson() -> CodeBlock.of(C.STR_RAW_JSON_FROM_CAPTURE)

            type.isByteArray() -> CodeBlock.of(C.STR_CAPTURE_RAW_JSON_BYTES)

            type.isGhost() || type.isEnum() -> CodeBlock.of(
                C.TEMPLATE_DESERIALIZE_T,
                type.serializerClassName()
            )

            type.isPrimitiveInt() -> CodeBlock.of(C.STR_NEXT_INT)
            type.isPrimitiveBoolean() -> CodeBlock.of(C.STR_NEXT_BOOLEAN)
            type.isPrimitiveLong() -> CodeBlock.of(C.STR_NEXT_LONG)
            type.isPrimitiveDouble() -> CodeBlock.of(C.STR_NEXT_DOUBLE)
            type.isPrimitiveFloat() -> CodeBlock.of(C.STR_NEXT_FLOAT)

            type.isList() -> {
                val inner = type.arguments.firstOrNull()?.type?.resolve()
                    ?: return CodeBlock.of(C.STR_NEXT_STRING)

                CodeBlock.of(
                    C.STR_READ_LIST_TEMPLATE,
                    buildTypeReaderCall(inner)
                )

            }

            type.isMap() -> {
                val valueType = type
                    .arguments
                    .getOrNull(1)
                    ?.type?.resolve()
                    ?: return CodeBlock.of(C.STR_NEXT_STRING)

                CodeBlock.of(
                    C.STR_READ_MAP_TEMPLATE,
                    buildTypeReaderCall(valueType)
                )
            }

            else -> {
                if (type.isString()) {
                    CodeBlock.of(C.STR_NEXT_STRING)
                } else {
                    val name = getContextualSerializerName(type)
                    CodeBlock.of(C.TEMPLATE_DESERIALIZE_L, name)
                }
            }
        }

        return if (type.isMarkedNullable) {
            nullGuarded(readerCall)
        } else {
            readerCall
        }
    }

    /**
     * Generates a unique variable name for a contextual serializer.
     * Example: "User" -> "contextualUserSerializer".
     */
    private fun getContextualSerializerName(type: KSType): String {
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
     * Registers descriptive private bitmask constants on the companion/object builder
     * for every property and all validation/defaults masks, completely eliminating magic numbers.
     *
     * @param typeSpecBuilder The [TypeSpec.Builder] where the serializer properties will be added.
     */
    protected fun emitPropertyMaskConstants(typeSpecBuilder: TypeSpec.Builder) {
        properties.forEach { prop ->
            val index = propertyIndices[prop]!!
            val bitIdx = index % C.MASK_SIZE_BITS.toInt()
            val bitMask = C.VAL_ONE_L shl bitIdx
            val bitMaskStr = formatMaskString(bitMask)
            val name = "MASK_" + prop.kotlinName.uppercase()
            if (typeSpecBuilder.propertySpecs.none { it.name == name }) {
                typeSpecBuilder.addProperty(
                    PropertySpec.builder(name, com.squareup.kotlinpoet.LONG)
                        .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                        .initializer("%L", bitMaskStr)
                        .build()
                )
            }
        }

        for (i in requiredMasks.indices) {
            val reqMask = requiredMasks[i]
            if (reqMask != C.VAL_ZERO_L) {
                val reqMaskStr = formatMaskString(reqMask)
                val name = "MASK_REQUIRED_$i"
                if (typeSpecBuilder.propertySpecs.none { it.name == name }) {
                    typeSpecBuilder.addProperty(
                        PropertySpec.builder(name, com.squareup.kotlinpoet.LONG)
                            .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                            .initializer("%L", reqMaskStr)
                            .build()
                    )
                }
            }
        }

        for (i in defaultMasks.indices) {
            val defMask = defaultMasks[i]
            if (defMask != C.VAL_ZERO_L) {
                val defMaskStr = formatMaskString(defMask)
                val name = "MASK_DEFAULTS_$i"
                if (typeSpecBuilder.propertySpecs.none { it.name == name }) {
                    typeSpecBuilder.addProperty(
                        PropertySpec.builder(name, com.squareup.kotlinpoet.LONG)
                            .addModifiers(KModifier.PRIVATE, KModifier.CONST)
                            .initializer("%L", defMaskStr)
                            .build()
                    )
                }
            }
        }
    }

    /**
     * Injects private property references for contextual serializers into the generated class.
     *
     * This implements a static Dependency Injection strategy. Instead of looking up
     * serializers at runtime (which would require reflection), we pre-resolve and
     * inject them as private final fields during the compilation phase.
     *
     * @param typeSpecBuilder The [TypeSpec.Builder] where the serializer properties will be added.
     */
    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(
            C.STR_GHOST_PKG,
            C.STR_GHOST_OBJ
        )

        contextualSerializers.forEach { (type, name) ->
            val nonNullableType = type.makeNotNullable()
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    name,
                    ClassName(
                        C.STR_CONTRACT_PKG,
                        C.STR_GHOST_SERIALIZER
                    )
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

    /**
     * Wraps a deserialization reader instruction with a null-check condition.
     *
     * In the generated code, this wraps the reader call with a check:
     * `if (reader.isNextNullValue()) { reader.consumeNull(); null } else { ... }`
     */
    protected fun nullGuarded(inner: CodeBlock): CodeBlock =
        CodeBlock.of(C.TEMPLATE_NULL_CHECK_L, inner)
}
