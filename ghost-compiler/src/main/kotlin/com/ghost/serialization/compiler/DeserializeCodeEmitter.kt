package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants.PROPERTY_MAX_SIZE
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main coordinator (Orchestrator) for the deserialization code generation process.
 *
 * This class implements the Strategy Pattern to select the most efficient deserialization
 * logic for a given DTO. It acts as the central hub that inspects the DTO's metadata
 * (sealed hierarchy, enum, size, or structural complexity) and delegates the generation
 * task to specialized emitters.
 *
 * ### Orchestration Strategy:
 * - **Polymorphic Types:** Direct delegation for Sealed or [Enum] types.
 * - **Large DTOs (> [PROPERTY_MAX_SIZE]):** Delegates to [FragmentedEmitter] to bypass JVM 64KB method limits.
 * - **Standard DTOs:** Delegates to [StandardEmitter] for highly-optimized, inlinable code.
 *
 * @property properties The list of property metadata to be deserialized.
 * @property originalClassName The canonical name of the class being generated.
 * @property readerClass The specific reader implementation (e.g., GhostJsonFlatReader).
 * @property isSealed Identifies if the class is part of a sealed hierarchy.
 * @property isValue Identifies if the class is a Kotlin Value Class (inline class).
 * @property isEnum Identifies if the class is an Enum.
 * @property isInferred Handles polymorphic types where the discriminator is absent,
 * relying on property presence to identify the subclass.
 */
internal class DeserializeCodeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val sealedDiscriminatorKey: String = C.DEFAULT_DISCRIMINATOR_KEY,
    private val isResilientClass: Boolean = false,
    private val isInferred: Boolean = false,
    private val isObject: Boolean = false
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    /**
     * Entry point for the code generation pipeline.
     *
     * Evaluates the DTO structure to determine the optimal generation strategy.
     * It manages the injection of contextual serializers and ensures [isResilientClass]
     * metadata is correctly propagated to the generated serializer.
     *
     * @param typeSpecBuilder The builder for the serializer object/class.
     * @param isFlatPath Whether the code is generating for a flat reader vs standard.
     */
    fun build(
        typeSpecBuilder: TypeSpec.Builder,
        isFlatPath: Boolean = false
    ) {
        val body = CodeBlock.builder()

        when {
            isObject -> {
                emitObjectReturn(body)
            }
            isSealed && isInferred -> {
                emitInferredSealed(body)
            }
            isSealed -> {
                emitSealed(body)
            }
            isValue -> {
                emitValue(body)
            }
            isEnum -> {
                emitEnum(body)
            }
            properties.size > PROPERTY_MAX_SIZE -> {
                emitFragmented(body, typeSpecBuilder, isFlatPath)
            }
            else -> {
                emitStandard(body, typeSpecBuilder, isFlatPath)
            }
        }

        addDeserializeFunction(typeSpecBuilder, body.build())
        injectResilienceProperty(typeSpecBuilder, isFlatPath)
    }

    /**
     * Instantiates and delegates code generation to [FragmentedEmitter].
     */
    private fun emitFragmented(
        body: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder,
        isFlatPath: Boolean
    ) {
        val emitter = FragmentedEmitter(
            properties,
            originalClassName,
            readerClass
        )
        emitter.emit(body, typeSpecBuilder, isFlatPath = isFlatPath)
        if (!isFlatPath) {
            emitter.injectContextualSerializers(typeSpecBuilder)
        }
    }

    /**
     * Instantiates and delegates code generation to [StandardEmitter].
     */
    private fun emitStandard(
        body: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder,
        isFlatPath: Boolean
    ) {
        val emitter = StandardEmitter(
            properties,
            originalClassName,
            readerClass
        )
        emitter.emit(body, typeSpecBuilder)
        if (!isFlatPath) {
            emitter.injectContextualSerializers(typeSpecBuilder)
        }
    }

    /**
     * Emits deserialization logic for object types (singletons). Consumes the JSON object
     * body and returns the singleton instance.
     */
    private fun emitObjectReturn(body: CodeBlock.Builder) {
        body.addStatement(C.STR_BEGIN_OBJECT)
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)
        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.beginControlFlow(C.STR_MINUS_TWO_ARROW)
        body.addStatement(C.STR_SKIP_VALUE)
        body.endControlFlow()
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement(C.STR_END_OBJECT)
        body.addStatement("return %T", originalClassName)
    }

    /**
     * Reads @GhostSerialization(name) from a class declaration, falling back to simple class name.
     */
    private fun getSubclassDiscriminator(subclass: KSClassDeclaration): String {
        val customName = subclass.annotations
            .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
            ?.arguments?.find { it.name?.asString() == C.ARG_NAME }?.value as? String
        return if (!customName.isNullOrEmpty()) customName else subclass.simpleName.asString()
    }

    /**
     * Conditionally injects the `isResilient` property
     * if the class is resilient and not flat.
     */
    private fun injectResilienceProperty(
        typeSpecBuilder: TypeSpec.Builder,
        isFlatPath: Boolean
    ) {
        if (isResilientClass && !isFlatPath) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_IS_RESILIENT,
                    BOOLEAN
                )
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.STR_TRUE)
                    .build()
            )
        }
    }

    /**
     * Adds the final deserialize function to the generated serializer.
     * This method fulfills the contract of GhostSerializer.
     */
    private fun addDeserializeFunction(
        typeSpecBuilder: TypeSpec.Builder,
        body: CodeBlock
    ) {
        typeSpecBuilder.addFunction(
            FunSpec.builder(C.STR_DESERIALIZE)
                .addKdoc(C.STR_KDOC_DESERIALIZE, originalClassName)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(C.STR_READER, readerClass)
                .returns(originalClassName)
                .addCode(body)
                .build()
        )
    }

    /**
     * Emits deserialization logic for sealed hierarchies via discriminator key checks.
     * It generates a `when` expression that inspects the JSON discriminator field to
     * decide which specialized serializer to invoke. Includes support for fallback
     * subclasses when a discriminator value doesn't match known types.
     */
    private fun emitSealed(body: CodeBlock.Builder) {
        val fallbackSubclass = sealedSubclasses.find { subclass ->
            subclass.annotations.any {
                it.shortName.asString() == C.STR_FALLBACK_ANNOTATION
            }
        }

        val regularSubclasses = sealedSubclasses.filter {
            it != fallbackSubclass
        }

        if (fallbackSubclass != null) {
            body.addStatement(
                C.TEMPLATE_PEEK_STRING_FIELD,
                sealedDiscriminatorKey
            )
        } else {
            body.addStatement(
                C.TEMPLATE_PEEK_TYPE,
                sealedDiscriminatorKey,
                C.STR_MISSING_TYPE
            )
        }

        body.beginControlFlow(C.STR_WHEN_TYPENAME)

        regularSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = subClassName.serializerClassName()
            val discriminatorValue = getSubclassDiscriminator(subclass)
            body.addStatement(
                C.TEMPLATE_DESERIALIZE_BRANCH,
                discriminatorValue,
                serializerName
            )
        }
        if (fallbackSubclass != null) {
            val fallbackClassName = fallbackSubclass.toClassName()
            val fallbackSerializerName = fallbackClassName.serializerClassName()
            body.beginControlFlow(C.STR_ELSE_BRANCH)
            body.addStatement(C.TEMPLATE_DESERIALIZE_T, fallbackSerializerName)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_UNKNOWN_TYPE)
        }
        body.endControlFlow()
        body.addStatement(C.STR_RETURN_RESULT)
    }

    /**
     * Emits deserialization logic for Inferred Polymorphism.
     *
     * This is an advanced strategy where the subclass is determined by the presence
     * of specific fields in the JSON. It uses a **Property-to-Class bitmask** to identify
     * candidate subclasses and resolves them using a voting-like logic on the eligibility bitmask.
     */
    private fun emitInferredSealed(body: CodeBlock.Builder) {
        val context = createInferredSealedContext(properties)
        if (context == null) {
            body.addStatement(C.TEMPLATE_THROW_S, C.STR_ERR_NO_SUBCLASSES)
            return
        }

        emitInferredSealedLocalVariables(body, context)
        emitInferredSealedMainLoop(body, context)
        emitInferredSealedRequiredMasks(body, context)
        emitInferredSealedDecisionBlock(body, context)
    }

    /**
     * Context data holder containing all analyzed property and subclass metadata
     * needed for inferred sealed class deserialization code generation.
     */
    private class InferredSealedContext(
        val inferredInfo: List<InferredSubclassModel>,
        val names: List<String>,
        val allProps: List<GhostPropertyModel>,
        val nameToIndex: Map<String, Int>,
        val propertyToClassMask: Map<String, Long>
    )

    /**
     * Resolves and builds the [InferredSealedContext] from the property models.
     * Returns null if no inferred subclasses are declared.
     */
    private fun createInferredSealedContext(properties: List<GhostPropertyModel>): InferredSealedContext? {
        val inferredInfo = properties.firstOrNull()?.inferredSubclasses ?: emptyList()
        if (inferredInfo.isEmpty()) {
            return null
        }

        val names = inferredInfo.flatMap { it.properties }.map { it.jsonName }.distinct()
        val allProps = names.map { name ->
            inferredInfo.flatMap { it.properties }.find { it.jsonName == name }!!
        }

        val nameToIndex = names.mapIndexed { index, name -> name to index }.toMap()

        val propertyToClassMask = names.associateWith { name ->
            var mask = C.VAL_ZERO_L
            inferredInfo.forEachIndexed { index, subclass ->
                if (subclass.properties.any { it.jsonName == name }) {
                    mask = mask or (C.VAL_ONE_L shl index)
                }
            }
            mask
        }

        return InferredSealedContext(
            inferredInfo = inferredInfo,
            names = names,
            allProps = allProps,
            nameToIndex = nameToIndex,
            propertyToClassMask = propertyToClassMask
        )
    }

    /**
     * Emits the local variable declarations for tracking property values and
     * managing the seen and eligibility bitmasks.
     */
    private fun emitInferredSealedLocalVariables(
        body: CodeBlock.Builder,
        context: InferredSealedContext
    ) {
        body.addStatement(C.STR_BEGIN_OBJECT)
        context.allProps.forEachIndexed { index, prop ->
            body.addStatement(
                C.TEMPLATE_VAR_NULL_DECL,
                C.STR_V_VAR_PREFIX,
                index,
                prop.typeName.copy(nullable = true),
                C.STR_NULL
            )
        }

        body.addStatement(
            C.TEMPLATE_VAR_LONG_INIT,
            C.STR_ELIGIBILITY_MASK,
            (C.VAL_ONE_L shl context.inferredInfo.size) - C.VAL_ONE,
            C.STR_L_SUFFIX
        )

        body.addStatement(
            C.TEMPLATE_VAR_INIT,
            C.STR_SEEN_MASK,
            C.STR_ZERO_L
        )
    }

    /**
     * Emits the main loops for parsing and consuming JSON field names.
     * When a field matches a known subclass signature, its value is parsed,
     * the eligibility mask is updated, and the seen mask is updated.
     */
    private fun emitInferredSealedMainLoop(
        body: CodeBlock.Builder,
        context: InferredSealedContext
    ) {
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        context.names.forEachIndexed { index, name ->
            val prop = context.allProps[index]
            val classMask = context.propertyToClassMask[name] ?: C.VAL_ZERO_L

            body.beginControlFlow(
                C.TEMPLATE_WHEN_BRANCH,
                index
            )
            body.addStatement(
                C.TEMPLATE_VAR_ASSIGN,
                C.STR_V_VAR_PREFIX,
                index,
                buildCall(prop)
            )
            body.addStatement(
                C.TEMPLATE_MASK_AND_ASSIGN,
                C.STR_ELIGIBILITY_MASK,
                C.STR_ELIGIBILITY_MASK,
                classMask,
                C.STR_L_SUFFIX
            )
            body.addStatement(
                C.TEMPLATE_MASK_OR_SHL_ASSIGN,
                C.STR_SEEN_MASK,
                C.STR_SEEN_MASK,
                C.STR_ONE_L,
                C.STR_SHL,
                index
            )
            body.endControlFlow()
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.addStatement(
            C.STR_ELSE_BRANCH +
                    C.STR_SPACE +
                    C.STR_SKIP_VALUE
        )
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement(C.STR_END_OBJECT)
    }

    /**
     * Precomputes and emits the required property masks
     * for each subclass candidate.
     */
    private fun emitInferredSealedRequiredMasks(
        body: CodeBlock.Builder,
        context: InferredSealedContext
    ) {
        context.inferredInfo.forEachIndexed { index, subclass ->
            var reqMask = C.VAL_ZERO_L
            subclass.properties.forEach { prop ->
                if (!prop.isNullable && !prop.hasDefaultValue) {
                    val pIdx = context.nameToIndex[prop.jsonName]!!
                    reqMask = reqMask or (C.VAL_ONE_L shl pIdx)
                }
            }
            body.addStatement(
                C.TEMPLATE_VAL_LONG_INIT,
                C.STR_REQ_MASK_PREFIX,
                index,
                reqMask,
                C.STR_L_SUFFIX
            )
        }
    }

    /**
     * Emits the decision block logic to determine the matched subclass and
     * instantiate it using the parsed arguments. Throws a GhostJsonException if no
     * unique matching subclass can be resolved.
     */
    private fun emitInferredSealedDecisionBlock(
        body: CodeBlock.Builder,
        context: InferredSealedContext
    ) {
        val jsonExClass = ClassName(
            C.PKG_EXCEPTION,
            C.STR_GHOST_JSON_EXCEPTION
        )

        body.beginControlFlow(C.TEMPLATE_RESULT_WHEN)
        context.inferredInfo.forEachIndexed { subclassIndex, subclass ->
            val subclassClassName = subclass.declaration.toClassName()
            val maskBit = C.VAL_ONE_L shl subclassIndex
            val reqMaskVar = "${C.STR_REQ_MASK_PREFIX}$subclassIndex"

            body.beginControlFlow(
                C.TEMPLATE_INFERRED_DECISION_BRANCH,
                C.STR_ELIGIBILITY_MASK,
                maskBit,
                C.STR_L_SUFFIX,
                C.STR_ZERO_L,
                C.STR_SEEN_MASK,
                reqMaskVar,
                reqMaskVar
            )

            val requiredProps = subclass.properties.filter { !it.hasDefaultValue }
            val defaultProps = subclass.properties.filter { it.hasDefaultValue }

            val requiredArgs = CodeBlock.builder()
            requiredProps.forEachIndexed { i, prop ->
                val pIdx = context.nameToIndex[prop.jsonName]!!
                val vVar = "${C.STR_V_VAR_PREFIX}$pIdx"
                if (!prop.isNullable) {
                    val msg = C.STR_REQUIRED_FIELD_MISSING.format(
                        prop.jsonName,
                        subclassClassName.simpleName
                    )
                    requiredArgs.add(
                        C.TEMPLATE_REQUIRED_ARG,
                        prop.kotlinName,
                        vVar,
                        jsonExClass,
                        msg
                    )
                } else {
                    requiredArgs.add(
                        C.TEMPLATE_OPTIONAL_ARG,
                        prop.kotlinName,
                        vVar
                    )
                }

                if (i < requiredProps.size - C.VAL_ONE) {
                    requiredArgs.add(C.STR_COMMA_SPACE)
                }
            }

            if (defaultProps.isEmpty()) {
                body.addStatement(
                    C.TEMPLATE_CONSTRUCTOR,
                    subclassClassName,
                    requiredArgs.build()
                )
            } else {
                body.addStatement(
                    C.TEMPLATE_DATA_CLASS_COPY_INIT,
                    C.STR_INSTANCE_VAR,
                    subclassClassName,
                    requiredArgs.build()
                )
                defaultProps.forEach { prop ->
                    val pIdx = context.nameToIndex[prop.jsonName]!!
                    val vVar = "${C.STR_V_VAR_PREFIX}$pIdx"
                    body.addStatement(
                        C.TEMPLATE_IF_NOT_NULL_COPY,
                        vVar,
                        C.STR_INSTANCE_VAR,
                        C.STR_INSTANCE_VAR,
                        C.STR_COPY,
                        prop.kotlinName,
                        vVar
                    )
                }
                body.addStatement(
                    C.TEMPLATE_VARIABLE,
                    C.STR_INSTANCE_VAR
                )
            }
            body.endControlFlow()
        }
        body.addStatement(
            C.STR_ELSE_BRANCH + C.STR_SPACE + C.TEMPLATE_THROW_EXCEPTION,
            jsonExClass,
            C.STR_INFERRED_ERROR_MSG
        )
        body.endControlFlow()
        body.addStatement(C.STR_RETURN_RESULT)
    }

    /**
     * Emits deserialization logic for value class types.
     */
    private fun emitValue(body: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val call = buildCall(prop)
        body.addStatement(
            C.TEMPLATE_RETURN_CONSTRUCTOR,
            originalClassName,
            call
        )
    }

    /**
     * Emits deserialization logic for enum types using integer lookups.
     */
    private fun emitEnum(body: CodeBlock.Builder) {
        body.addStatement(C.STR_ENUM_SELECT_OPTIONS)
        body.beginControlFlow(C.STR_ENUM_WHEN)

        properties
            .firstOrNull()
            ?.enumValues
            ?.entries
            ?.forEachIndexed { index, entry ->
                body.addStatement(
                    C.TEMPLATE_ENUM_BRANCH,
                    index,
                    originalClassName,
                    entry.key
                )
            }

        body.addStatement(C.STR_ERR_INVALID_ENUM_INDEX)
        body.addStatement(C.STR_ERR_UNEXPECTED_INDEX)
        body.endControlFlow()
    }
}
