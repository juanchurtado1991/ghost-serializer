@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Code generator that orchestrates the generation of a specialized GhostSerializer companion object.
 *
 * It acts as the central coordinator that resolves KSP declarations, compiles target metadata,
 * generates KotlinPoet type/file specifications, configures perfect-hash lookup tables for JSON fields,
 * and delegates the actual body code generation to [SerializeCodeEmitter] and [DeserializeCodeEmitter].
 *
 * @property properties List of analyzed property configurations.
 * @param classDeclaration The KSP class metadata of the target serializable class.
 */
internal class GhostCodeGenerator(
    private val properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration
) {

    private val fullPaths = properties.map {
        it.flattenPath
            ?: (it.wrapPath?.let { p -> p + it.jsonName }
                ?: listOf(it.jsonName))
    }

    private val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
    private val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
            classDeclaration.modifiers.contains(Modifier.INLINE)

    private val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS
    private val isResilient =
        classDeclaration.annotations.any { it.shortName.asString() == C.GHOST_RESILIENT }

    private val sealedSubclasses = if (isSealed) {
        classDeclaration.getSealedSubclasses().toList()
    } else {
        emptyList()
    }

    private val packageName = classDeclaration.packageName.asString()
    private val originalClassName = classDeclaration.toClassName()
    private val baseClassName = originalClassName
        .simpleNames
        .joinToString(C.STR_UNDERSCORE)

    // For a sealed subclass, this is the value written as the discriminator (i.e. the subclass name).
    // This is different from sealedDiscriminatorKey which is the JSON field name on the parent sealed class.
    private val discriminator = if (
        !isSealed &&
        !isValue &&
        classDeclaration.parentDeclaration is KSClassDeclaration &&
        (classDeclaration.parentDeclaration as KSClassDeclaration)
            .modifiers.contains(Modifier.SEALED)
    ) {
        classDeclaration.simpleName.asString()
    } else {
        null
    }

    // Reads the discriminator key from @GhostSerialization(discriminator = "...").
    // When the current class is a sealed subclass, the discriminator key lives on the PARENT
    // sealed class annotation, not on the subclass. When the current class IS the sealed class,
    // it reads from its own annotation. Defaults to "type" if not set.
    private val sealedDiscriminatorKey: String = run {
        val annotationSource = if (discriminator != null) {
            // Current class is a subclass — look at the parent sealed class
            classDeclaration.parentDeclaration as? KSClassDeclaration
        } else {
            // Current class is the sealed class itself
            classDeclaration
        }
        annotationSource?.annotations
            ?.find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
            ?.arguments
            ?.find { it.name?.asString() == C.ARG_DISCRIMINATOR }
            ?.value as? String
            ?: C.STR_DEFAULT_DISCRIMINATOR
    }

    private val customTypeName: String = classDeclaration.annotations
        .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
        ?.arguments
        ?.find { it.name?.asString() == C.ARG_NAME }
        ?.value as? String ?: C.STR_EMPTY

    private val isInferred: Boolean = (if (discriminator != null) {
        classDeclaration.parentDeclaration as? KSClassDeclaration
    } else {
        classDeclaration
    })?.annotations
        ?.find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
        ?.arguments
        ?.find { it.name?.asString() == C.ARG_INFERRED }
        ?.value as? Boolean
        ?: false

    private val finalTypeName: String =
        customTypeName.ifEmpty { classDeclaration.simpleName.asString() }

    private val enumValues = properties.firstOrNull { it.isEnum }?.enumValues

    private val serializerInterface = ClassName(
        C.PKG_CONTRACT,
        C.STR_GHOST_SERIALIZER
    )

    private val streamingWriterClass = ClassName(
        C.PKG_WRITER,
        C.STR_GHOST_JSON_WRITER
    )

    private val flatWriterClass = ClassName(
        C.PKG_WRITER,
        C.STR_GHOST_JSON_FLAT_WRITER
    )

    private val streamingReaderClass = ClassName(
        C.PKG_PARSER,
        C.STR_GHOST_JSON_READER
    )

    private val flatReaderClass = ClassName(
        C.PKG_PARSER,
        C.STR_GHOST_JSON_FLAT_READER
    )

    private val readerClass = streamingReaderClass

    /**
     * Builds the [FileSpec] representing the generated serializer source file.
     * Configures file-level annotations including [Suppress] for lints and [OptIn]
     * for internal API usage.
     *
     * @return The constructed [FileSpec] containing the serializer companion class.
     */
    fun createSpec(): FileSpec {
        val serializerName = baseClassName + C.STR_SERIALIZER_SUFFIX
        val suppressBuilder = AnnotationSpec.builder(Suppress::class)
        listOf(
            C.LINT_UNUSED_VARIABLE,
            C.LINT_UNCHECKED_CAST,
            C.LINT_UNUSED
        ).forEach { rule ->
            suppressBuilder.addMember(C.STR_FORMAT_S, rule)
        }

        val fileBuilder = FileSpec.builder(packageName, serializerName)
            .addAnnotation(suppressBuilder.build())
            .addAnnotation(
                AnnotationSpec.builder(
                    ClassName(
                        C.PKG_KOTLIN,
                        C.STR_OPT_IN
                    )
                )
                    .addMember(
                        C.MARKER_CLASS,
                        ClassName(
                            C.PKG_GHOST,
                            C.STR_INTERNAL_GHOST_API
                        )
                    )
                    .build()
            )

        // Core imports always used
        if (!isEnum && !isValue) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_BEGIN_OBJECT_NAME,
                C.STR_END_OBJECT_NAME,
                C.STR_SELECT_NAME_AND_CONSUME_NAME,
                C.STR_SKIP_VALUE_NAME
            )
        }

        val (allTypes, hasNullable) = resolveAllTypes()
        resolveAndAddImports(fileBuilder, allTypes, hasNullable)

        return fileBuilder
            .addImport(C.OKIO_PACKAGE, C.STR_BYTESTRING_IMPORT)
            .addType(buildSerializerObject(serializerName))
            .build()
    }

    /**
     * Scans properties and collects all fully resolved type string representations.
     * Also detects if any property types are marked as nullable.
     */
    private fun resolveAllTypes(): Pair<List<String>, Boolean> {
        var hasNullable = properties.any { it.isNullable }
        val allTypes = properties.flatMap { prop ->
            val types = mutableListOf<String>()
            fun collectTypes(type: KSType) {
                types.add(type.toString())
                if (type.isMarkedNullable) {
                    hasNullable = true
                }
                for (arg in type.arguments) {
                    val resolved = arg.type?.resolve()
                    if (resolved != null) {
                        collectTypes(resolved)
                    }
                }
            }
            collectTypes(prop.type)
            prop.valueClassProperty?.let { collectTypes(it.type) }

            prop.inferredSubclasses.forEach { sub ->
                sub.properties.forEach { subProp ->
                    collectTypes(subProp.type)
                    subProp.valueClassProperty?.let { collectTypes(it.type) }
                }
            }
            types
        }
        return allTypes to hasNullable
    }

    /**
     * Resolves and registers KSP reader/exception helper
     * imports conditionally based on property types.
     */
    private fun resolveAndAddImports(
        fileBuilder: FileSpec.Builder,
        allTypes: List<String>,
        hasNullable: Boolean
    ) {
        val hasList = properties.any { it.isList } ||
                allTypes.any { it.contains(C.STR_LIST) || it.contains(C.STR_SET) }

        val hasMap = properties.any { it.isMap } || allTypes.any { it.contains(C.STR_MAP) }

        if (hasList) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_READ_LIST,
                C.STR_BEGIN_ARRAY,
                C.STR_END_ARRAY
            )
        }
        if (hasMap) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_READ_MAP,
                C.STR_NEXT_KEY
            )
        }
        if (hasNullable) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_CONSUME_NULL_NAME,
                C.STR_IS_NEXT_NULL_VALUE_NAME
            )
        }
        if (properties.any { it.flattenPath != null } || isSealed) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_PEEK_STRING_FIELD
            )
        }
        if (isEnum) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_SELECT_STRING
            )
        }
        if (properties.any { it.isResilient }) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.DECODE_RESILIENT
            )
        }

        val allTypeStrings = allTypes.joinToString()
        if (allTypeStrings.contains(C.STR_INT)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_INT_NAME
            )
        }
        if (allTypeStrings.contains(C.STR_LONG_TYPE)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_LONG_NAME
            )
        }
        if (allTypeStrings.contains(C.STR_STRING)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_STRING_NAME
            )
        }
        if (allTypeStrings.contains(C.STR_DOUBLE)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_DOUBLE_NAME
            )
        }
        if (allTypeStrings.contains(C.STR_FLOAT)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_FLOAT_NAME
            )
        }
        if (allTypeStrings.contains(C.STR_BOOLEAN)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_NEXT_BOOLEAN_NAME
            )
        }

        if (isEnum || isSealed || properties.any { it.isResilient }) {
            fileBuilder.addImport(
                C.PKG_EXCEPTION,
                C.STR_GHOST_JSON_EXCEPTION
            )
        }
    }

    private fun getAllJsonNames(properties: List<GhostPropertyModel>): List<String> {
        val names = mutableSetOf<String>()
        val allProps = if (isSealed && isInferred) {
            properties.firstOrNull()?.inferredSubclasses?.flatMap { it.properties } ?: emptyList()
        } else {
            properties
        }
        allProps.forEach { prop ->
            if (prop.flattenPath != null) {
                names.addAll(prop.flattenPath)
            } else if (prop.wrapPath != null) {
                names.addAll(prop.wrapPath)
            }
            names.add(prop.jsonName)
        }
        return names.toList().sorted()
    }

    /**
     * Builds the [TypeSpec] of the generated companion serializer object.
     */
    private fun buildSerializerObject(serializerName: String): TypeSpec {
        val serializeEmitter = SerializeCodeEmitter(
            properties,
            originalClassName,
            isSealed,
            isValue,
            isEnum,
            sealedSubclasses,
            discriminator,
            sealedDiscriminatorKey
        )

        val deserializeEmitterStreaming = DeserializeCodeEmitter(
            properties,
            originalClassName,
            streamingReaderClass,
            isSealed,
            isValue,
            isEnum,
            sealedSubclasses,
            sealedDiscriminatorKey,
            isResilient,
            isInferred
        )

        val deserializeEmitterFlat = DeserializeCodeEmitter(
            properties,
            originalClassName,
            flatReaderClass,
            isSealed,
            isValue,
            isEnum,
            sealedSubclasses,
            sealedDiscriminatorKey,
            isResilient,
            isInferred
        )

        val typeSpecBuilder = TypeSpec.objectBuilder(serializerName)
            .addKdoc(C.STR_KDOC_HIGH_PERF, originalClassName)
            .addKdoc(C.STR_KDOC_GENERATED)
            .addSuperinterface(
                serializerInterface
                    .parameterizedBy(originalClassName)
            )
            .addProperty(
                PropertySpec.builder(C.STR_TYPE_NAME_PROP, String::class)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.MARKER, finalTypeName)
                    .build()
            )

        if (!isEnum && !isValue) {
            addPerfectHashOptions(typeSpecBuilder, readerClass)
            addCachedHeaderProperties(typeSpecBuilder)
        }

        if (isEnum && enumValues != null) {
            addEnumOptions(typeSpecBuilder, readerClass)
        }

        // Generate nested OPTIONS for GhostFlatten
        FlattenOptionsGenerator
            .generateNestedOptions(typeSpecBuilder, properties, fullPaths, readerClass)

        deserializeEmitterStreaming.build(typeSpecBuilder, isFlatPath = false)
        deserializeEmitterFlat.build(typeSpecBuilder, isFlatPath = true)

        serializeEmitter.injectContextualSerializers(typeSpecBuilder)

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(streamingWriterClass, typeSpecBuilder))
            .addFunction(serializeEmitter.build(flatWriterClass, typeSpecBuilder))
            .addFunction(buildWarmUpMethod())
            .build()
    }

    /**
     * Builds and registers the global companion perfect hash lookup options property.
     *
     * @param typeSpecBuilder The target class builder to inject the options property into.
     * @param readerClass The JSON reader class reference.
     */
    private fun addPerfectHashOptions(
        typeSpecBuilder: TypeSpec.Builder,
        readerClass: ClassName
    ) {
        val names = if (isSealed && isInferred) {
            properties
                .firstOrNull()
                ?.inferredSubclasses
                ?.flatMap { it.properties }
                ?.map { it.jsonName }?.distinct()
                ?: emptyList()
        } else {
            properties.map {
                it.flattenPath
                    ?.firstOrNull()
                    ?: it.wrapPath?.firstOrNull()
                    ?: it.jsonName
            }.distinct()
        }
        val (shift, multiplier) = PerfectHashFinder.findPerfectHash(names)

        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(C.TEMPLATE_OPTIONS_OF_SEEDS, optionsClass, shift, multiplier)

        names.forEach { name ->
            optionsBuilder.add(C.TEMPLATE_COMMA_FORMAT_S, name)
        }
        optionsBuilder.add(C.STR_PAREN)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(
                C.STR_OPTIONS,
                optionsClass
            )
                .addModifiers(KModifier.PRIVATE)
                .initializer(optionsBuilder.build())
                .build()
        )
    }

    /**
     * Declares and caches ByteString header constants (e.g. `H_FIELD_NAME = "\"field_name\":"`)
     * to avoid on-the-fly UTF-8 encoding during serialization.
     */
    private fun addCachedHeaderProperties(typeSpecBuilder: TypeSpec.Builder) {
        val allNames = getAllJsonNames(properties)
        for (name in allNames) {
            val cleanName = name.replace(
                C.STR_DOT,
                C.STR_UNDERSCORE
            ).uppercase()

            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_H_VAL_PREFIX + cleanName,
                    C.BYTE_STRING_CLASS,
                    KModifier.PRIVATE
                )
                    .initializer(
                        C.TEMPLATE_ENCODE_UTF8,
                        C.FMT_JSON_FIELD.format(name)
                    )
                    .build()
            )
        }
    }

    /**
     * Injects the enum options perfect hash lookup table if the target is an enum class.
     *
     * @param typeSpecBuilder The target class builder to inject the enum options property into.
     * @param readerClass The JSON reader class reference.
     */
    private fun addEnumOptions(typeSpecBuilder: TypeSpec.Builder, readerClass: ClassName) {
        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val enumOptionsBuilder = CodeBlock.builder()
            .add(C.TEMPLATE_OPTIONS_OF, optionsClass)

        val values = enumValues!!.values.toList()
        values.forEachIndexed { index, serialName ->
            if (index > 0) {
                enumOptionsBuilder.add(C.STR_COMMA_SPACE)
            }
            enumOptionsBuilder.add(C.STR_FORMAT_S, serialName)
        }
        enumOptionsBuilder.add(C.STR_PAREN)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(
                C.STR_ENUM_OPTIONS,
                optionsClass
            )
                .addModifiers(KModifier.PRIVATE)
                .initializer(enumOptionsBuilder.build())
                .build()
        )
    }

    /**
     * Builds the warmUp function that deserializes a minimal JSON structure to trigger
     * early class loading and JIT compilation/ART compilation optimizations.
     */
    private fun buildWarmUpMethod(): FunSpec {
        val warmupJson = generateMinimalJson()
        return FunSpec.builder(C.STR_WARM_UP)
            .addModifiers(KModifier.OVERRIDE)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow(C.STR_TRY)
                    .addStatement(
                        C.TEMPLATE_WARM_UP_READER_INIT,
                        C.STR_READER1,
                        streamingReaderClass,
                        warmupJson
                    )
                    .addStatement(C.TEMPLATE_WARM_UP_DESERIALIZE, C.STR_READER1)
                    .nextControlFlow(C.STR_CATCH_EXCEPTION)
                    .endControlFlow()
                    .beginControlFlow(C.STR_TRY)
                    .addStatement(
                        C.TEMPLATE_WARM_UP_READER_INIT,
                        C.STR_READER2,
                        flatReaderClass,
                        warmupJson
                    )
                    .addStatement(C.TEMPLATE_WARM_UP_DESERIALIZE, C.STR_READER2)
                    .nextControlFlow(C.STR_CATCH_EXCEPTION)
                    .endControlFlow()
                    .build()
            )
            .build()
    }

    /**
     * Generates a minimal valid JSON string representation of the target class
     * containing only required fields with default initial values. This is used
     * to feed the JIT warmup routines.
     */
    private fun generateMinimalJson(): String {
        if (isSealed || isEnum || isValue) {
            return C.STR_EMPTY_JSON
        }
        val sb = StringBuilder()
        sb.append(C.STR_CURLY_OPEN)
        val entries = mutableListOf<String>()
        properties.forEach { prop ->
            if (!prop.isNullable && !prop.hasDefaultValue) {
                val key = C.STR_DOUBLE_QUOTE + prop.jsonName + C.STR_DOUBLE_QUOTE
                val value = when {
                    prop.type.isPrimitiveInt() || prop.type.isPrimitiveLong() -> C.STR_ZERO
                    prop.type.isPrimitiveDouble() || prop.type.isPrimitiveFloat() -> C.STR_ZERO_D
                    prop.type.isPrimitiveBoolean() -> C.STR_FALSE
                    prop.type.isString() -> C.STR_EMPTY_STRING
                    prop.type.isList() -> C.STR_EMPTY_ARRAY
                    prop.type.isMap() -> C.STR_EMPTY_JSON
                    prop.type.isGhost() -> C.STR_EMPTY_JSON
                    else -> C.STR_NULL
                }
                entries.add(key + C.STR_COLON + value)
            }
        }
        sb.append(entries.joinToString(C.STR_COMMA))
        sb.append(C.STR_CURLY_CLOSE)
        return sb.toString()
    }

}
