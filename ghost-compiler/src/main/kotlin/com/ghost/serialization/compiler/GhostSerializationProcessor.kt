package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main KSP Processor for Ghost Serialization.
 *
 * It analyzes classes annotated with `@GhostSerialization`, generates their respective
 * specialized serializers, and builds a global registry mapping classes to serializers
 * to avoid reflection at runtime.
 *
 * @property codeGenerator The KSP [CodeGenerator] used to create new serializer and registry files.
 * @property logger The [KSPLogger] used to report compilation errors, warnings, and messages.
 * @property options Map of key-value pairs representing processor options passed from build scripts.
 */
class GhostSerializationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    /**
     * Map tracking class names to their generated companion serializers for the module registry.
     */
    private val classToSerializer = mutableMapOf<ClassName, ClassName>()

    /**
     * Origin files corresponding to processed declarations, used to define KSP incremental compilation dependencies.
     */
    private val originatingFiles = mutableSetOf<KSFile>()

    /**
     * Tracks processed file names to avoid double-processing declarations.
     */
    private val processedFiles = mutableSetOf<String>()

    /**
     * Analyzer that reads declarations and constructs property metadata models.
     */
    private val analyzer = GhostAnalyzer(logger)

    /**
     * Lazily resolves the output name of the module-level registry class (e.g. `GhostRegistry_module_name`).
     */
    private val registryClassName: String by lazy {
        // Use the module name provided by KSP or fallback to a stable suffix
        var moduleName = options[C.OPTION_MODULE_NAME]
            ?.replace(C.STR_DASH, C.STR_UNDERSCORE)
            ?.replace(C.STR_DOT, C.STR_UNDERSCORE)
            ?: C.STR_DEFAULT_NAME

        // Append _Test if we are in a test source set to avoid collisions
        if (moduleName == C.STR_DEFAULT_NAME) {
            val isTest = originatingFiles.any { 
                val path = it.filePath
                path.contains(C.STR_SRC_TEST) ||
                        path.contains(C.STR_SRC_ANDROID_TEST) ||
                        path.contains(C.STR_SRC_TEST_KSP)
            }
            if (isTest) {
                moduleName += C.STR_TEST_SUFFIX
            }
        }

        C.STR_REGISTRY_PREFIX + C.STR_UNDERSCORE + moduleName
    }

    /**
     * Entry point of the processor phase.
     * Searches for `@GhostSerialization` annotated classes, generates their serializers,
     * and compiles the final module registry if any class was successfully processed.
     *
     * @param resolver KSP [Resolver] used to query symbols and types.
     * @return List of symbols that couldn't be processed in this round.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(C.STR_ANNOTATION_SERIALIZATION)
        val validClasses = symbols.filterIsInstance<KSClassDeclaration>().toList()
        val unableToProcess = symbols.filterNot { it is KSClassDeclaration }

        validClasses.forEach { classDeclaration -> processClass(classDeclaration) }

        if (validClasses.isNotEmpty()) {
            generateModuleRegistry()
            generateProGuardRules()
            generateServiceFile()
        }

        return unableToProcess.toList()
    }

    /**
     * Analyzes, compiles code specs, and writes the serializer companion class file for a target class.
     *
     * @param classDeclaration KSP class declaration of the target serializable model.
     */
    private fun processClass(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.simpleName.asString()
        try {
            val propertiesModel = analyzer.analyze(classDeclaration)
            val serializerClassName = generateSerializer(classDeclaration, propertiesModel) ?: return

            registerSerializer(classDeclaration, serializerClassName)

            logger.info(
                "${
                    C.STR_LOG_PREFIX
                }${
                    C.STR_LOG_OPTIMIZED
                }$className"
            )
        } catch (e: Exception) {
            logger.error(
                "${C.STR_LOG_PREFIX}${C.STR_LOG_CRITICAL}$className${C.STR_COLON_SPACE}${e.message ?: e.toString()}",
                classDeclaration
            )
        }
    }

    /**
     * Generates and writes the serializer class file for the target class declaration.
     *
     * @param classDeclaration KSP class declaration of the target serializable model.
     * @param propertiesModel The properties metadata model parsed from the declaration.
     * @return The [ClassName] of the generated serializer class, or null if it was already processed.
     */
    private fun generateSerializer(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>
    ): ClassName? {
        val textChannel = options[C.OPTION_TEXT_CHANNEL] != C.STR_FALSE
        val fileGenerator = GhostCodeGenerator(
            classDeclaration = classDeclaration,
            properties = propertiesModel,
            textChannel = textChannel
        )

        val fileSpec = fileGenerator.createSpec()
        val packageName = classDeclaration.packageName.asString()
        val fullFileName = "$packageName.${fileSpec.name}"

        if (processedFiles.contains(fullFileName)) {
            return null
        }
        processedFiles.add(fullFileName)

        fileSpec.writeTo(
            codeGenerator = codeGenerator,
            dependencies = Dependencies(
                aggregating = false,
                classDeclaration.containingFile!!
            )
        )

        return ClassName(
            packageName,
            classDeclaration
                .toClassName()
                .simpleNames
                .joinToString(C.STR_UNDERSCORE)
                    + C.STR_SERIALIZER_SUFFIX
        )
    }

    /**
     * Registers the generated serializer class name mapping for a given model, tracking sealed subclasses
     * and recording originating files for incremental compilation.
     *
     * @param classDeclaration KSP class declaration of the target serializable model.
     * @param serializerClassName The [ClassName] of the generated serializer.
     */
    private fun registerSerializer(
        classDeclaration: KSClassDeclaration,
        serializerClassName: ClassName
    ) {
        classToSerializer[classDeclaration.toClassName()] = serializerClassName

        if (classDeclaration.modifiers.contains(Modifier.SEALED)) {
            classDeclaration.getSealedSubclasses().forEach { subclass ->
                classToSerializer[subclass.toClassName()] = serializerClassName
            }
        }
        classDeclaration.containingFile?.let { originatingFiles.add(it) }
    }

    /**
     * Generates a registry containing a mapping of serializable classes to their generated serializers.
     * Splitting the structure into chunks if there are many models to avoid JVM method limits.
     */
    private fun generateModuleRegistry() {
        val serializerType = ClassName(C.STR_CONTRACT_PKG, C.STR_GHOST_SERIALIZER)
        val kClassType = ClassName(C.STR_REFLECT_PKG, C.STR_KCLASS)
        val type = TypeVariableName(C.STR_TYPE_T, Any::class)
        val mapType = ClassName(C.STR_COLLECTIONS_PKG, C.STR_MAP)
            .parameterizedBy(
                kClassType.parameterizedBy(STAR),
                serializerType.parameterizedBy(STAR)
            )

        val entries = classToSerializer.entries.toList().sortedBy { it.key.canonicalName }
        val registrySpec = TypeSpec.classBuilder(registryClassName)
            .addKdoc(C.STR_KDOC_REGISTRY)
            .addSuperinterface(ClassName(C.STR_CONTRACT_PKG, C.STR_GHOST_REGISTRY))

        val chunks = entries.chunked(C.REGISTRY_CHUNK_SIZE)

        // 1. Generate full serializers map (Lazy + Fragmented)
        generateSerializersMapProperty(registrySpec, chunks, entries, mapType)

        // 2. Generate getSerializer method (Fragmented when)
        generateGetSerializerMethod(registrySpec, chunks, entries, serializerType, type)

        // 3. Generate Shard Methods if fragmented
        generateShardMethods(registrySpec, chunks, mapType, serializerType)

        // 4. Generate Metadata Methods & Companion
        generateMetadataMethodsAndCompanion(registrySpec, entries.size, mapType)

        // Write the spec to file
        writeRegistryFile(registrySpec.build())
    }

    /**
     * Generates the lazily-initialized full serializers map property for the registry.
     *
     * @param registrySpec The type spec builder for the module registry.
     * @param chunks Chunked lists of serializable class entry mappings.
     * @param entries All serializable class entry mappings.
     * @param mapType The parameterized type description of the mapping.
     */
    private fun generateSerializersMapProperty(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        entries: List<Map.Entry<ClassName, ClassName>>,
        mapType: TypeName
    ) {
        val allSerializersDelegate = CodeBlock.builder()
            .add(C.STR_LAZY_START)
            .indent()

        if (chunks.size > 1) {
            chunks.forEachIndexed { index, _ ->
                allSerializersDelegate.add(C.TEMPLATE_GET_SHARD_MAP_CALL, index)
                if (index < chunks.size - 1) {
                    allSerializersDelegate.add(C.STR_PLUS_SPACED)
                }
            }
        } else {
            allSerializersDelegate.add(buildMapBlock(entries))
        }

        allSerializersDelegate.unindent().add(C.STR_NEWLINE_CLOSE_CURLY)

        registrySpec.addProperty(
            PropertySpec.builder(C.STR_PROP_SERIALIZERS_MAP, mapType)
                .addModifiers(KModifier.PRIVATE)
                .delegate(allSerializersDelegate.build())
                .build()
        )
    }

    /**
     * Generates the polymorphic `getSerializer` method routing requests to matches or shards.
     *
     * @param registrySpec The type spec builder for the module registry.
     * @param chunks Chunked lists of serializable class entry mappings.
     * @param entries All serializable class entry mappings.
     * @param serializerType The parameterized serializer type description.
     * @param type The type variable representation for return type casting.
     */
    private fun generateGetSerializerMethod(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        entries: List<Map.Entry<ClassName, ClassName>>,
        serializerType: ClassName,
        type: TypeVariableName
    ) {
        val getMethodBuilder = FunSpec.builder(C.STR_FUN_GET_SERIALIZER)
            .addTypeVariable(type)
            .addParameter(
                C.STR_PARAM_CLAZZ,
                KClass::class.asClassName().parameterizedBy(type)
            )
            .returns(serializerType.parameterizedBy(type).copy(nullable = true))
            .addModifiers(KModifier.OVERRIDE)
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember(C.MARKER, C.STR_UNCHECKED_CAST)
                    .build()
            )

        val getCode = CodeBlock.builder()
        if (chunks.size > 1) {
            for (index in chunks.indices) {
                getCode.addStatement(
                    C.TEMPLATE_GET_SHARD_CALL,
                    index,
                    serializerType.parameterizedBy(type).copy(nullable = true)
                )
            }
            getCode.addStatement(C.STR_RETURN_NULL)
        } else {
            getCode.add(buildWhenBlock(entries, serializerType, type))
        }
        getMethodBuilder.addCode(getCode.build())
        registrySpec.addFunction(getMethodBuilder.build())
    }

    /**
     * Generates private helper lookup/mapping shard methods if the registry size warrants fragmentation.
     *
     * @param registrySpec The type spec builder for the module registry.
     * @param chunks Chunked lists of serializable class entry mappings.
     * @param mapType The parameterized type description of the mapping.
     * @param serializerType The parameterized serializer type description.
     */
    private fun generateShardMethods(
        registrySpec: TypeSpec.Builder,
        chunks: List<List<Map.Entry<ClassName, ClassName>>>,
        mapType: TypeName,
        serializerType: ClassName
    ) {
        if (chunks.size > 1) {
            chunks.forEachIndexed { i, chunk ->
                registrySpec.addFunction(
                    FunSpec.builder(C.TEMPLATE_SHARD_MAP_NAME.format(i))
                        .addModifiers(KModifier.PRIVATE)
                        .returns(mapType)
                        .addCode(C.STR_RETURN_L, buildMapBlock(chunk))
                        .build()
                )

                // Shard Lookup
                registrySpec.addFunction(
                    FunSpec.builder(C.TEMPLATE_SHARD_NAME.format(i))
                        .addModifiers(KModifier.PRIVATE)
                        .addParameter(
                            C.STR_PARAM_CLAZZ,
                            KClass::class.asClassName().parameterizedBy(STAR)
                        )
                        .returns(serializerType.parameterizedBy(STAR).copy(nullable = true))
                        .addCode(buildWhenBlock(chunk, serializerType, STAR))
                        .build()
                )
            }
        }
    }

    /**
     * Generates metadata info methods (prewarm, registry size count, and global companion instance).
     *
     * @param registrySpec The type spec builder for the module registry.
     * @param entriesCount The total number of serializable class entry mappings.
     * @param mapType The parameterized type description of the mapping.
     */
    private fun generateMetadataMethodsAndCompanion(
        registrySpec: TypeSpec.Builder,
        entriesCount: Int,
        mapType: TypeName
    ) {
        registrySpec.addFunction(
            FunSpec.builder(C.STR_FUN_PREWARM)
                .addModifiers(KModifier.OVERRIDE)
                .addStatement(C.STR_SERIALIZERS_SIZE)
                .build()
        )
        registrySpec.addFunction(
            FunSpec.builder(C.STR_FUN_REG_COUNT)
                .addModifiers(KModifier.OVERRIDE)
                .returns(Int::class)
                .addStatement(C.STR_RETURN_L, entriesCount)
                .build()
        )
        registrySpec.addFunction(
            FunSpec.builder(C.STR_FUN_GET_ALL_SERIALIZERS)
                .addModifiers(KModifier.OVERRIDE)
                .returns(mapType)
                .addStatement(C.STR_RETURN_SERIALIZERS)
                .build()
        )
        registrySpec.addType(
            TypeSpec.companionObjectBuilder()
                .addProperty(
                    PropertySpec.builder(
                        C.STR_INSTANCE,
                        ClassName(C.STR_GENERATED_PKG, registryClassName)
                    )
                        .initializer(
                            C.STR_INIT_INSTANCE,
                            ClassName(C.STR_GENERATED_PKG, registryClassName)
                        )
                        .addAnnotation(JvmField::class)
                        .build()
                )
                .build()
        )
    }

    /**
     * Writes the completed registry type specification to a file.
     *
     * @param registrySpec The built registry type specification.
     */
    private fun writeRegistryFile(registrySpec: TypeSpec) {
        FileSpec.builder(C.STR_GENERATED_PKG, registryClassName)
            .addType(registrySpec)
            .build()
            .writeTo(
                codeGenerator,
                Dependencies(aggregating = true, *originatingFiles.toTypedArray())
            )
    }

    /**
     * Generates a KotlinPoet [CodeBlock] mapping class types to their serializer instances.
     *
     * @param entries Serializable class entry mappings.
     * @return Pre-compiled registry map code block.
     */
    private fun buildMapBlock(
        entries: List<Map.Entry<ClassName, ClassName>>
    ): CodeBlock {
        val builder = CodeBlock.builder().add(C.STR_MAP_OF)
        entries.forEachIndexed { index, entry ->
            builder.add(
                C.STR_MAP_ENTRY,
                entry.key,
                entry.value
            )
            if (index < entries.size - 1) {
                builder.add(C.STR_COMMA_NEWLINE)
            }
        }
        builder.add(C.STR_PAREN_CLOSE)
        return builder.build()
    }

    /**
     * Generates a high-performance Kotlin `when (clazz)` lookup expression.
     *
     * @param entries Registry entry mappings.
     * @param serializerType Serializer class name representation.
     * @param type Generic type variable for mapping return types safely.
     * @return Generated routing when code block.
     */
    private fun buildWhenBlock(
        entries: List<Map.Entry<ClassName, ClassName>>,
        serializerType: ClassName,
        type: TypeName
    ): CodeBlock {
        val builder = CodeBlock
            .builder()
            .add(C.STR_WHEN_CLAZZ_START)

        entries.forEach { entry ->
            builder.add(
                C.STR_WHEN_ENTRY,
                entry.key,
                entry.value
            )
        }

        builder.add(C.STR_WHEN_ELSE_NULL)
        builder.add(
            C.STR_WHEN_CLOSE_CAST,
            serializerType
                .parameterizedBy(type)
                .copy(nullable = true)
        )
        return builder.build()
    }

    /**
     * Generates ProGuard/R8 keep rules to ensure the Registry and Serializers
     * are not obfuscated or removed during the shrinking phase.
     */
    private fun generateProGuardRules() {
        val rules = C.TEMPLATE_PROGUARD_KEEP
            .trimIndent()
            .format(C.STR_GENERATED_PKG, registryClassName)

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true),
                packageName = C.STR_META_INF_PROGUARD,
                fileName = C.STR_GHOST_SERIALIZATION_FILE,
                extensionName = C.STR_EXT_PRO
            ).use { it.write(rules.toByteArray()) }
        } catch (e: Exception) {
            logger.warn(
                "${
                    C.STR_LOG_PREFIX
                }${
                    C.STR_LOG_PROGUARD_WARN
                }${
                    e.message
                }"
            )
        }
    }

    /**
     * Generates a ServiceLoader entry so the Core module can automatically
     * discover this registry at runtime.
     */
    private fun generateServiceFile() {
        val serviceName = C.STR_SERVICE_REGISTRY
        val implementationName = "${
            C.STR_GENERATED_PKG
        }${
            C.STR_DOT
        }$registryClassName"

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(
                    aggregating = true,
                    *originatingFiles.toTypedArray()
                ),
                packageName = C.STR_META_INF_SERVICES,
                fileName = serviceName,
                extensionName = C.STR_EMPTY
            ).use { it.write(implementationName.toByteArray()) }
        } catch (e: Exception) {
            logger.warn(
                "${
                    C.STR_LOG_PREFIX
                }${
                    C.STR_LOG_SERVICE_WARN
                }${e.message}"
            )
        }
    }

}