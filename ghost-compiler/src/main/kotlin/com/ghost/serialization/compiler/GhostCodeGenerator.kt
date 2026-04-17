package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName

internal class GhostCodeGenerator(
    private val properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration
) {

    private val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
    private val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
            classDeclaration.modifiers.contains(Modifier.INLINE)
    private val isEnum = classDeclaration.classKind == com.google.devtools.ksp.symbol.ClassKind.ENUM_CLASS

    private val sealedSubclasses = if (isSealed) {
        classDeclaration.getSealedSubclasses().toList()
    } else {
        emptyList()
    }

    private val packageName = classDeclaration.packageName.asString()
    private val originalClassName = classDeclaration.toClassName()
    private val baseClassName = originalClassName.simpleNames.joinToString("_")

    private val discriminator = if (
        !isSealed &&
        !isValue &&
        classDeclaration.parentDeclaration is KSClassDeclaration &&
        (classDeclaration.parentDeclaration as KSClassDeclaration).modifiers.contains(Modifier.SEALED)
    ) {
        classDeclaration.simpleName.asString()
    } else {
        null
    }

    private val enumValues = properties.firstOrNull { it.isEnum }?.enumValues

    private val serializerInterface = ClassName(PKG_CONTRACT, STR_GHOST_SERIALIZER)
    private val writerClass = ClassName(PKG_WRITER, STR_GHOST_JSON_WRITER)
    private val readerClass = ClassName(PKG_PARSER, STR_GHOST_JSON_READER)
    private val bufferedSink = ClassName(OKIO_PACKAGE, STR_BUFFERED_SINK)

    fun createSpec(): FileSpec {
        val serializerName = "${baseClassName}$STR_SERIALIZER_SUFFIX"
        return FileSpec.builder(packageName, serializerName)
            .addImport(
                PKG_PARSER,
                STR_IS_NEXT_NULL_VALUE,
                STR_CONSUME_NULL,
                STR_SKIP_VALUE,
                STR_READ_LIST,
                STR_NEXT_KEY,
                STR_NEXT_INT,
                STR_NEXT_LONG,
                STR_NEXT_DOUBLE,
                STR_CONSUME_KEY_SEPARATOR,
                STR_CONSUME_ARRAY_SEPARATOR,
                STR_NEXT_FLOAT,
                STR_PEEK_STRING_FIELD
            )
            .addImport(PKG_EXCEPTION, STR_GHOST_JSON_EXCEPTION)
            .addImport(OKIO_PACKAGE, STR_BYTESTRING_IMPORT)
            .addType(buildSerializerObject())
            .build()
    }

    private fun buildSerializerObject(): TypeSpec {
        val names = properties.map { it.jsonName }
        val (shift, multiplier) = findPerfectHash(names)

        val optionsClass = readerClass.peerClass(STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(STR_OPTIONS_OF_SEEDS, optionsClass, shift, multiplier)
            .indent()

        properties.forEachIndexed { index, prop ->
            val comma = if (index < properties.size - 1) STR_COMMA else STR_EMPTY
            optionsBuilder.add("$STR_FORMAT_S$comma$STR_NEWLINE", prop.jsonName)
        }
        optionsBuilder.unindent().add(STR_PAREN_CLOSE)

        val serializeEmitter = SerializeCodeEmitter(
            properties,
            originalClassName,
            writerClass,
            isSealed,
            isValue,
            isEnum,
            sealedSubclasses,
            discriminator
        )
        val deserializeEmitter = DeserializeCodeEmitter(
            properties, originalClassName, readerClass, isSealed, isValue, isEnum, sealedSubclasses
		)

        val serializerName = "${baseClassName}$STR_SERIALIZER_SUFFIX"
        val fileSpecBuilder = FileSpec.builder(packageName, serializerName)
            .addImport(readerClass.packageName, STR_OPTIONS_CLASS)
            .addImport(PKG_PARSER, STR_GHOST_JSON_READER)
            .addImport(PKG_WRITER, STR_GHOST_JSON_WRITER)
        
        val typeSpecBuilder = TypeSpec.objectBuilder(serializerName)
            .addKdoc(STR_KDOC_HIGH_PERF, originalClassName)
            .addKdoc(STR_KDOC_GENERATED)
            .addSuperinterface(
                serializerInterface
                    .parameterizedBy(originalClassName)
            )
            .addProperty(
                PropertySpec.builder(
                    STR_OPTIONS,
                    readerClass.peerClass(STR_OPTIONS_CLASS)
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(optionsBuilder.build())
                    .build()
            )

        if (isEnum && enumValues != null) {
            val enumOptionsBuilder = CodeBlock.builder()
                .add(STR_OPTIONS_OF, optionsClass)
                .indent()
            
            val values = enumValues.values.toList()
            values.forEachIndexed { index, serialName ->
                val comma = if (index < values.size - 1) STR_COMMA else STR_EMPTY
                enumOptionsBuilder.add("$STR_FORMAT_S$comma$STR_NEWLINE", serialName)
            }
            enumOptionsBuilder.unindent().add(STR_PAREN_CLOSE)

            typeSpecBuilder.addProperty(
                PropertySpec.builder(STR_ENUM_OPTIONS, readerClass.peerClass(STR_OPTIONS_CLASS))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(enumOptionsBuilder.build())
                    .build()
            )
        }

        return typeSpecBuilder
            .addFunction(serializeEmitter.build())
            .addFunction(deserializeEmitter.build())
            .addFunction(
                com.squareup.kotlinpoet.FunSpec.builder(STR_WARM_UP)
                    .addModifiers(KModifier.OVERRIDE)
                    .addCode(STR_WARM_UP_BODY, readerClass)
                    .build()
            )
            .build()
    }

    private fun findPerfectHash(names: List<String>): Pair<Int, Int> {
        if (names.isEmpty()) return 0 to 31
        val firstBytes = names.map { it.firstOrNull()?.code?.toByte() ?: 0 }
        val lengths = names.map { it.length }
        val rawBytes = names.map { it.encodeToByteArray() }
        
        // Brute force search for a collision-free multiplier and shift
        for (m in 31..2000 step 2) {
            for (s in 0..16) {
                val seen = mutableSetOf<Int>()
                var collision = false
                val dispatch = IntArray(1024) { -1 }
                for (i in rawBytes.indices) {
                    val bytes = rawBytes[i]
                    if (bytes.isNotEmpty()) {
                        val h = (((bytes[0].toInt() and 0xFF) * m + bytes.size) shr s) and 1023
                        if (dispatch[h] == -1) {
                            dispatch[h] = i
                        } else {
                            collision = true
                            break
                        }
                    }
                }
                if (!collision) return s to m
            }
        }
        return 0 to 31 // Fallback to default if not found
    }

    companion object {
        private const val PKG_PARSER = "com.ghost.serialization.core.parser"
        private const val PKG_WRITER = "com.ghost.serialization.core.writer"
        private const val PKG_CONTRACT = "com.ghost.serialization.core.contract"
        private const val PKG_EXCEPTION = "com.ghost.serialization.core.exception"
        private const val OKIO_PACKAGE = "okio"
        private const val STR_GHOST_SERIALIZER = "GhostSerializer"
        private const val STR_GHOST_JSON_WRITER = "GhostJsonWriter"
        private const val STR_GHOST_JSON_READER = "GhostJsonReader"
        private const val STR_BUFFERED_SINK = "BufferedSink"
        private const val STR_SERIALIZER_SUFFIX = "Serializer"
        private const val STR_IS_NEXT_NULL_VALUE = "isNextNullValue"
        private const val STR_CONSUME_NULL = "consumeNull"
        private const val STR_SKIP_VALUE = "skipValue"
        private const val STR_READ_LIST = "readList"
        private const val STR_NEXT_KEY = "nextKey"
        private const val STR_NEXT_INT = "nextInt"
        private const val STR_NEXT_LONG = "nextLong"
        private const val STR_NEXT_DOUBLE = "nextDouble"
        private const val STR_SELECT_NAME = "selectName"
        private const val STR_CONSUME_KEY_SEPARATOR = "consumeKeySeparator"
        private const val STR_CONSUME_ARRAY_SEPARATOR = "consumeArraySeparator"
        private const val STR_NEXT_FLOAT = "nextFloat"
        private const val STR_PEEK_STRING_FIELD = "peekStringField"
        private const val STR_GHOST_JSON_EXCEPTION = "GhostJsonException"
        private const val STR_BYTESTRING_IMPORT = "ByteString.Companion.encodeUtf8"
        private const val STR_OPTIONS_OF = "%T.of(\n"
        private const val STR_OPTIONS_OF_SEEDS = "%T.of(%L, %L,\n"
        private const val STR_COMMA = ","
        private const val STR_EMPTY = ""
        private const val STR_FORMAT_S = "%S"
        private const val STR_NEWLINE = "\n"
        private const val STR_PAREN_CLOSE = ")"
        private const val STR_KDOC_HIGH_PERF = "High-performance serializer for [%T].\n"
        private const val STR_KDOC_GENERATED = "Generated by GhostSerialization. Do not modify manually.\n"
        private const val STR_OPTIONS = "OPTIONS"
        private const val STR_OPTIONS_CLASS = "Options"
        private const val STR_ENUM_OPTIONS = "ENUM_OPTIONS"
        private const val STR_WARM_UP = "warmUp"
        private val STR_WARM_UP_BODY = """
            |try {
            |  val reader = %T("{}".encodeToByteArray())
            |  deserialize(reader)
            |} catch (e: Exception) {}
        """.trimMargin()
    }
}
