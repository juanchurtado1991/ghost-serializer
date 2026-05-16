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

internal class GhostCodeGenerator(
    private val properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration
) {
    private val fullPaths = properties.map {
        it.flattenPath ?: (it.wrapPath?.let { p -> p + it.jsonName } ?: listOf(it.jsonName))
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
    private val baseClassName = originalClassName.simpleNames.joinToString(C.STR_UNDERSCORE)

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
        ?.value as? String ?: ""

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

    private val readerClass = ClassName(
        C.PKG_PARSER,
        C.STR_GHOST_JSON_READER
    )

    fun createSpec(): FileSpec {
        val serializerName = baseClassName + C.STR_SERIALIZER_SUFFIX
        val fileBuilder = FileSpec.builder(packageName, serializerName)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember(
                        C.STR_SUPPRESS_FORMAT,
                        C.LINT_UNUSED_VARIABLE,
                        C.LINT_UNUSED_EXPRESSION,
                        C.LINT_UNCHECKED_CAST,
                        C.LINT_USELESS_CAST,
                        C.LINT_UNNECESSARY_NOT_NULL_ASSERTION,
                        C.LINT_UNUSED,
                        C.LINT_NAME_SHADOWING,
                        C.LINT_UNUSED_RESULT
                    )
                    .build()
            )
            .addAnnotation(
                AnnotationSpec.builder(
                    ClassName(C.PKG_KOTLIN, C.STR_OPT_IN)
                )
                    .addMember(
                        C.MARKER_CLASS,
                        ClassName(C.PKG_GHOST, C.STR_INTERNAL_GHOST_API)
                    )
                    .build()
            )

        // Core imports always used
        fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_BEGIN_OBJECT_NAME,
            C.STR_END_OBJECT_NAME,
            C.STR_SELECT_NAME_AND_CONSUME_NAME,
            C.STR_SKIP_VALUE_NAME,
            C.STR_IGNORE
        )

        var hasNullable = properties.any { it.isNullable }
        val allTypes = properties.flatMap { prop -> 
            val types = mutableListOf<String>()
            fun collectTypes(type: KSType) {
                types.add(type.toString())
                if (type.isMarkedNullable) hasNullable = true
                for (arg in type.arguments) {
                    val resolved = arg.type?.resolve()
                    if (resolved != null) collectTypes(resolved)
                }
            }
            collectTypes(prop.type)
            // Also check value class inner property if any
            prop.valueClassProperty?.let { collectTypes(it.type) }
            
            // CRITICAL: If this property represents an inferred polymorphism root (sealed class),
            // we must also scan all possible subclass properties because the parent serializer
            // will need them to build the decision tree.
            prop.inferredSubclasses.forEach { sub ->
                sub.properties.forEach { subProp ->
                    collectTypes(subProp.type)
                    subProp.valueClassProperty?.let { collectTypes(it.type) }
                    if (subProp.isNullable) hasNullable = true
                }
            }
            types
        }

        val hasList =
            properties.any { it.isList } || allTypes.any { it.contains(C.STR_LIST) || it.contains(C.STR_SET) }
        val hasMap = properties.any { it.isMap } || allTypes.any { it.contains(C.STR_MAP) }

        if (hasList) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_READ_LIST, C.STR_BEGIN_ARRAY, C.STR_END_ARRAY)
        }
        if (hasMap) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_READ_MAP, C.STR_NEXT_KEY)
        }
        if (hasNullable) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_CONSUME_NULL_NAME,
                C.STR_IS_NEXT_NULL_VALUE_NAME
            )
        }
        if (properties.any { it.flattenPath != null } || isSealed) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_PEEK_STRING_FIELD)
        }
        if (isEnum) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_SELECT_STRING)
        }
        if (properties.any { it.isResilient }) {
            fileBuilder.addImport(C.PKG_PARSER, C.DECODE_RESILIENT)
        }

        val allTypeStrings = allTypes.joinToString()
        if (allTypeStrings.contains(C.STR_INT)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_INT_NAME
        )
        if (allTypeStrings.contains(C.STR_LONG_TYPE)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_LONG_NAME
        )
        if (allTypeStrings.contains(C.STR_STRING)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_STRING_NAME
        )
        if (allTypeStrings.contains(C.STR_DOUBLE)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_DOUBLE_NAME
        )
        if (allTypeStrings.contains(C.STR_FLOAT)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_FLOAT_NAME
        )
        if (allTypeStrings.contains(C.STR_BOOLEAN)) fileBuilder.addImport(
            C.PKG_PARSER,
            C.STR_NEXT_BOOLEAN_NAME
        )

        return fileBuilder
            .addImport(C.PKG_EXCEPTION, C.STR_GHOST_JSON_EXCEPTION)
            .addImport(C.OKIO_PACKAGE, C.STR_BYTESTRING_IMPORT)
            .addType(buildSerializerObject(serializerName))
            .build()
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

    private fun buildSerializerObject(serializerName: String): TypeSpec {
        val names = if (isSealed && isInferred) {
            properties.firstOrNull()?.inferredSubclasses?.flatMap { it.properties }
                ?.map { it.jsonName }?.distinct() ?: emptyList()
        } else {
            properties.map {
                it.flattenPath?.firstOrNull() ?: it.wrapPath?.firstOrNull() ?: it.jsonName
            }.distinct()
        }
        val (shift, multiplier) = findPerfectHash(names)

        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(C.STR_OPTIONS_OF_SEEDS, optionsClass, shift, multiplier)
            .indent()

        names.forEachIndexed { index, name ->
            val comma = if (index < names.size - 1) C.STR_COMMA else C.STR_EMPTY
            optionsBuilder.add(C.STR_FORMAT_S + comma + C.STR_NEWLINE, name)
        }

        optionsBuilder.unindent().add(C.STR_PAREN_CLOSE)

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

        val deserializeEmitter = DeserializeCodeEmitter(
            properties,
            originalClassName,
            readerClass,
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
            .addProperty(
                PropertySpec.builder(
                    C.STR_OPTIONS,
                    readerClass.peerClass(C.STR_OPTIONS_CLASS)
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(optionsBuilder.build())
                    .build()
            )

        // Cache all unique headers
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

            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_H_COMMA + cleanName,
                    C.BYTE_STRING_CLASS,
                    KModifier.PRIVATE
                )
                    .initializer(
                        C.TEMPLATE_ENCODE_UTF8,
                        C.FMT_JSON_FIELD_COMMA.format(name)
                    )
                    .build()
            )
        }

        if (isEnum && enumValues != null) {
            val enumOptionsBuilder = CodeBlock.builder()
                .add(C.STR_OPTIONS_OF, optionsClass)
                .indent()

            val values = enumValues.values.toList()

            val indices = values.indices
            for (index in indices) {
                val serialName = values[index]
                val comma = if (index < values.size - 1) C.STR_COMMA else C.STR_EMPTY
                enumOptionsBuilder.add(C.STR_FORMAT_S + comma + C.STR_NEWLINE, serialName)
            }

            enumOptionsBuilder.unindent().add(C.STR_PAREN_CLOSE)

            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_ENUM_OPTIONS,
                    readerClass.peerClass(C.STR_OPTIONS_CLASS)
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(enumOptionsBuilder.build())
                    .build()
            )
        }

        // Generate nested OPTIONS for GhostFlatten
        generateNestedOptions(typeSpecBuilder, properties, readerClass)

        deserializeEmitter.build(typeSpecBuilder)

        serializeEmitter.injectContextualSerializers(typeSpecBuilder)

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(streamingWriterClass, typeSpecBuilder))
            .addFunction(serializeEmitter.build(flatWriterClass, typeSpecBuilder))
            .addFunction(
                FunSpec.builder(C.STR_WARM_UP)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode(C.STR_WARM_UP_BODY, readerClass, C.STR_EMPTY_OBJ)
                    .build()
            )
            .build()
    }

    @Suppress("ReplaceSizeCheckWithIsNotEmpty")
    private fun findPerfectHash(names: List<String>): Pair<Int, Int> {
        if (names.isEmpty()) return 0 to C.HASH_MULTIPLIER_START
        val rawBytes = names.map { it.encodeToByteArray() }

        // Brute force search for a collision-free multiplier and shift for 4-byte hashing
        for (multiplier in C.HASH_MULTIPLIER_START..C.HASH_MULTIPLIER_LIMIT step C.HASH_MULTIPLIER_STEP) {
            for (shift in 0..C.HASH_SHIFT_LIMIT) {
                val dispatch = IntArray(C.HASH_TABLE_SIZE) { -1 }
                var collision = false
                for (index in rawBytes.indices) {
                    val bytes = rawBytes[index]
                    if (bytes.isNotEmpty()) {
                        var key = 0
                        if (bytes.size >= 1) key = key or (bytes[0].toInt() and C.BYTE_MASK)
                        if (bytes.size >= 2) key =
                            key or ((bytes[1].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_8)
                        if (bytes.size >= 3) key =
                            key or ((bytes[2].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_16)
                        if (bytes.size >= 4) key =
                            key or ((bytes[3].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_24)

                        val hash = ((key * multiplier + bytes.size) shr shift) and C.HASH_MASK
                        if (dispatch[hash] == -1) {
                            dispatch[hash] = index
                        } else {
                            collision = true
                            break
                        }
                    }
                }
                if (!collision) return shift to multiplier
            }
        }
        return 0 to C.HASH_MULTIPLIER_START // Fallback
    }

    private fun generateNestedOptions(
        typeSpecBuilder: TypeSpec.Builder,
        properties: List<GhostPropertyModel>,
        readerClass: ClassName
    ) {
        val rootNodes = mutableMapOf<String, FlattenNode>()

        properties.forEach { prop ->
            val path = prop.flattenPath ?: prop.wrapPath ?: return@forEach
            var currentMap = rootNodes
            path.forEachIndexed { index, segment ->
                val isLast = index == path.size - 1
                val node = currentMap.getOrPut(segment) { FlattenNode(segment) }
                if (isLast) {
                    node.properties.add(prop)
                } else {
                    currentMap = node.children
                }
            }
        }

        // We only care about nodes that have children (sub-objects) or multiple flattened properties
        // Actually, any node in flattenPath that is NOT the top level needs its own Options if it has children.
        // But the TOP level options are already handled by the main OPTIONS property?
        // No, the main OPTIONS property contains the FIRST segment of any flattened path.

        rootNodes.values.forEach { node ->
            emitNestedOptionsRecursive(typeSpecBuilder, node, readerClass, "", listOf(node.segment))
        }
    }

    private fun emitNestedOptionsRecursive(
        typeSpecBuilder: TypeSpec.Builder,
        node: FlattenNode,
        readerClass: ClassName,
        parentPrefix: String,
        currentPath: List<String>
    ) {
        if (node.children.isEmpty() && node.properties.isEmpty()) return

        val currentPrefix =
            if (parentPrefix.isEmpty()) node.segment else parentPrefix + C.STR_UNDERSCORE + node.segment
        val optionsName = C.STR_OPTIONS_PREFIX + currentPrefix.uppercase()

        val depth = currentPath.size
        val names = properties.filter { prop ->
            val path = fullPaths[this.properties.indexOf(prop)]
            path.size > depth && path.subList(0, depth) == currentPath
        }.map { prop ->
            val path = fullPaths[this.properties.indexOf(prop)]
            path[depth]
        }.distinct()

        val (shift, multiplier) = findPerfectHash(names)

        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(C.STR_OPTIONS_OF_SEEDS, optionsClass, shift, multiplier)
            .indent()

        names.forEachIndexed { index, name ->
            val comma = if (index < names.size - 1) C.STR_COMMA else C.STR_EMPTY
            optionsBuilder.add(C.STR_FORMAT_S + comma + C.STR_NEWLINE, name)
        }
        optionsBuilder.unindent().add(C.STR_PAREN_CLOSE)

        typeSpecBuilder.addProperty(
            PropertySpec.builder(optionsName, optionsClass)
                .addModifiers(KModifier.PRIVATE)
                .initializer(optionsBuilder.build())
                .build()
        )

        node.children.values.forEach { child ->
            emitNestedOptionsRecursive(
                typeSpecBuilder,
                child,
                readerClass,
                currentPrefix,
                currentPath + child.segment
            )
        }
    }

    private class FlattenNode(val segment: String) {
        val children = mutableMapOf<String, FlattenNode>()
        val properties = mutableListOf<GhostPropertyModel>()
    }
}
