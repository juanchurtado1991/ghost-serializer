package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
import com.ghost.serialization.compiler.GhostGeneratorConstants as C

internal class GhostCodeGenerator(
    private val properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration
) {

    private val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
    private val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
            classDeclaration.modifiers.contains(Modifier.INLINE)

    private val isEnum = classDeclaration.classKind == ClassKind.ENUM_CLASS

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

    private val finalTypeName: String =
        customTypeName.ifEmpty { classDeclaration.simpleName.asString() }

    private val enumValues = properties.firstOrNull { it.isEnum }?.enumValues

    private val serializerInterface = ClassName(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER)
    private val streamingWriterClass = ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_WRITER)
    private val flatWriterClass = ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_FLAT_WRITER)
    private val readerClass = ClassName(C.PKG_PARSER, C.STR_GHOST_JSON_READER)

    fun createSpec(): FileSpec {
        val serializerName = baseClassName + C.STR_SERIALIZER_SUFFIX
        return FileSpec.builder(packageName, serializerName)
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
                AnnotationSpec.builder(ClassName(C.PKG_KOTLIN, C.STR_OPT_IN))
                    .addMember("%T::class", ClassName(C.PKG_GHOST, C.STR_INTERNAL_GHOST_API))
                    .build()
            )
            .addImport(
                C.PKG_PARSER,
                C.STR_BEGIN_OBJECT,
                C.STR_END_OBJECT,
                C.STR_BEGIN_ARRAY,
                C.STR_END_ARRAY,
                C.STR_HAS_NEXT,
                C.STR_SKIP_VALUE,
                C.STR_READ_LIST,
                C.STR_READ_MAP,
                C.STR_NEXT_KEY,
                C.STR_NEXT_INT,
                C.STR_NEXT_LONG,
                C.STR_NEXT_DOUBLE,
                C.STR_NEXT_STRING,
                C.STR_NEXT_BOOLEAN,
                C.STR_SELECT_NAME_AND_CONSUME,
                C.STR_SELECT_STRING,
                C.STR_CONSUME_KEY_SEPARATOR,
                C.STR_CONSUME_ARRAY_SEPARATOR,
                C.STR_NEXT_FLOAT,
                C.STR_PEEK_STRING_FIELD,
                C.STR_IS_NEXT_NULL_VALUE,
                C.STR_CONSUME_NULL,
                C.STR_IGNORE,
                C.DECODE_RESILIENT
            )
            .addImport(C.PKG_EXCEPTION, C.STR_GHOST_JSON_EXCEPTION)
            .addImport(C.OKIO_PACKAGE, C.STR_BYTESTRING_IMPORT)
            .addType(buildSerializerObject(serializerName))
            .build()
    }

    private fun buildSerializerObject(serializerName: String): TypeSpec {
        val names = properties.map { it.jsonName }
        val (shift, multiplier) = findPerfectHash(names)

        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(C.STR_OPTIONS_OF_SEEDS, optionsClass, shift, multiplier)
            .indent()

        properties.forEachIndexed { index, prop ->
            val comma = if (index < properties.size - 1) C.STR_COMMA else C.STR_EMPTY
            optionsBuilder.add(C.STR_FORMAT_S + comma + C.STR_NEWLINE, prop.jsonName)
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
            properties, originalClassName, readerClass, isSealed, isValue, isEnum, sealedSubclasses,
            sealedDiscriminatorKey
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

        if (isEnum && enumValues != null) {
            val enumOptionsBuilder = CodeBlock.builder()
                .add(C.STR_OPTIONS_OF, optionsClass)
                .indent()

            val values = enumValues.values.toList()
            values.forEachIndexed { index, serialName ->
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

        deserializeEmitter.build(typeSpecBuilder)

        serializeEmitter.injectContextualSerializers(typeSpecBuilder)
        serializeEmitter.injectHeaderConstants(typeSpecBuilder)

        return typeSpecBuilder
            .addFunction(serializeEmitter.build(streamingWriterClass))
            .addFunction(serializeEmitter.build(flatWriterClass))
            .addFunction(
                FunSpec.builder(C.STR_WARM_UP)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode(C.STR_WARM_UP_BODY, readerClass)
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
                        if (bytes.size >= 2) key = key or ((bytes[1].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_8)
                        if (bytes.size >= 3) key = key or ((bytes[2].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_16)
                        if (bytes.size >= 4) key = key or ((bytes[3].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_24)

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
}
