package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.analysis.EnvelopeAnalyzer
import com.ghost.serialization.compiler.analysis.GhostAnalyzer
import com.ghost.serialization.compiler.analysis.TextChannelPlanner
import com.ghost.serialization.compiler.analysis.containsYamlIncompatibleType
import com.ghost.serialization.compiler.analysis.isByteArray
import com.ghost.serialization.compiler.analysis.isRawJson
import com.ghost.serialization.compiler.codegen.GhostCodeGenerator
import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer
import com.ghost.serialization.compiler.model.GhostEnvelopeModel
import com.ghost.serialization.compiler.model.GhostPropertyModel
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
import kotlin.reflect.KClass
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Main KSP processor for Ghost Serialization: analyzes `@GhostSerialization`-annotated classes,
 * generates their serializers, and builds a per-module registry mapping classes to serializers
 * to avoid reflection at runtime.
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

    private val analyzer = GhostAnalyzer(logger)
    private val envelopeAnalyzer = EnvelopeAnalyzer(logger)

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
            val isTest = TestSourceSetDetection.isTestCompilation(
                options = options,
                filePaths = originatingFiles.map { it.filePath },
            )
            if (isTest) {
                moduleName += C.STR_TEST_SUFFIX
            }
        }

        C.STR_REGISTRY_PREFIX + C.STR_UNDERSCORE + moduleName
    }

    /**
     * Entry point of the processor phase. Searches for `@GhostSerialization`-annotated classes,
     * generates their serializers, and compiles the module registry.
     *
     * @return Symbols that could not be processed in this round (deferred to KSP's next round).
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = (resolver.getSymbolsWithAnnotation(C.STR_ANNOTATION_SERIALIZATION) +
                resolver.getSymbolsWithAnnotation(C.STR_ANNOTATION_PROTO_SERIALIZATION)).toSet()
        val validClasses = symbols.filterIsInstance<KSClassDeclaration>().toList()
        val unableToProcess = symbols.filterNot { it is KSClassDeclaration }

        val analyzed = validClasses.mapNotNull { classDeclaration ->
            try {
                TextChannelPlanner.AnalyzedClass(
                    declaration = classDeclaration,
                    properties = analyzer.analyze(classDeclaration),
                )
            } catch (e: Exception) {
                logger.error(
                    "${C.STR_LOG_PREFIX}${C.STR_LOG_CRITICAL}${
                        classDeclaration.simpleName.asString()
                    }${C.STR_COLON_SPACE}${e.message ?: e.toString()}",
                    classDeclaration,
                )
                null
            }
        }

        val textChannelByClass = TextChannelPlanner.plan(
            analyzed = analyzed,
            moduleTextChannelOverride = when (options[C.OPTION_TEXT_CHANNEL]) {
                C.STR_TRUE -> true
                C.STR_FALSE -> false
                else -> null
            },
        )

        analyzed.forEach { entry ->
            processClass(
                classDeclaration = entry.declaration,
                propertiesModel = entry.properties,
                textChannel = textChannelByClass[entry.declaration] == true,
                resolver = resolver,
            )
        }

        reportOrphanGhostYamlSerialization(resolver)

        if (validClasses.isNotEmpty()) {
            generateModuleRegistry()
            generateProGuardRules()
            generateServiceFile()
        }

        return unableToProcess.toList()
    }

    /**
     * Compiles and writes the serializer companion file for a target class.
     */
    private fun processClass(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
        textChannel: Boolean,
        resolver: Resolver,
    ) {
        val className = classDeclaration.simpleName.asString()
        try {
            val serializerClassName = generateSerializer(
                classDeclaration = classDeclaration,
                propertiesModel = propertiesModel,
                textChannel = textChannel,
                resolver = resolver,
            ) ?: return

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
     * @return The generated serializer's [ClassName], or null if this file was already processed.
     */
    private fun generateSerializer(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
        textChannel: Boolean,
        resolver: Resolver,
    ): ClassName? {
        val envelopeModel = envelopeAnalyzer.analyze(classDeclaration, propertiesModel)
        val hasYaml = shouldGenerateYaml(
            resolver = resolver,
            classDeclaration = classDeclaration,
            propertiesModel = propertiesModel,
            envelopeModel = envelopeModel,
        )
        val fileGenerator = GhostCodeGenerator(
            classDeclaration = classDeclaration,
            properties = propertiesModel,
            textChannel = textChannel,
            envelopeModel = envelopeModel,
            hasYaml = hasYaml,
        )

        val fileSpec = fileGenerator.createSpec()
        val packageName = classDeclaration.packageName.asString()
        val fullFileName = "$packageName.${fileSpec.name}"

        if (processedFiles.contains(fullFileName)) {
            return null
        }
        processedFiles.add(fullFileName)

        GeneratedSourceTrimmer.write(
            fileSpec = fileSpec,
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

    private fun shouldGenerateYaml(
        resolver: Resolver,
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
        envelopeModel: GhostEnvelopeModel?,
    ): Boolean {
        val hasYamlAnnotation = classDeclaration.annotations.any {
            it.shortName.asString() == C.ANNOTATION_GHOST_YAML_SERIALIZATION
        }
        if (!hasYamlAnnotation) {
            return false
        }
        if (resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(C.STR_YAML_SERIALIZER_FQN)
            ) == null
        ) {
            return false
        }
        val hasGhostSerialization = classDeclaration.annotations.any {
            it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION
        }
        val hasProtoSerialization = classDeclaration.annotations.any {
            it.shortName.asString() == C.ANNOTATION_GHOST_PROTO_SERIALIZATION
        }
        if (!hasGhostSerialization && !hasProtoSerialization) {
            logger.error(C.STR_ERR_YAML_ORPHAN, classDeclaration)
            return false
        }
        if (classHasResilient(classDeclaration, propertiesModel)) {
            logger.error(C.STR_ERR_YAML_RESILIENT, classDeclaration)
            return false
        }
        if (classDeclaration.modifiers.contains(Modifier.SEALED)) {
            logger.error(C.STR_ERR_YAML_SEALED, classDeclaration)
            return false
        }
        if (envelopeModel != null) {
            logger.error(C.STR_ERR_YAML_ENVELOPE, classDeclaration)
            return false
        }
        val isInferred = classDeclaration.annotations
            .find { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }
            ?.arguments
            ?.find { it.name?.asString() == C.ARG_INFERRED }
            ?.value as? Boolean
            ?: false
        if (isInferred) {
            logger.error(C.STR_ERR_YAML_INFERRED, classDeclaration)
            return false
        }
        propertiesModel.forEach { prop ->
            if (propertyDisablesYamlCodegen(prop)) {
                logger.error(
                    yamlIncompatibilityReason(prop) ?: C.STR_ERR_YAML_NESTED_GHOST,
                    classDeclaration,
                )
            }
        }
        return propertiesModel.none { propertyDisablesYamlCodegen(it) }
    }

    private fun reportOrphanGhostYamlSerialization(resolver: Resolver) {
        resolver.getSymbolsWithAnnotation(C.STR_ANNOTATION_YAML_SERIALIZATION)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                val hasGhostSerialization = classDeclaration.annotations.any {
                    it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION
                }
                val hasProtoSerialization = classDeclaration.annotations.any {
                    it.shortName.asString() == C.ANNOTATION_GHOST_PROTO_SERIALIZATION
                }
                if (!hasGhostSerialization && !hasProtoSerialization) {
                    logger.error(C.STR_ERR_YAML_ORPHAN, classDeclaration)
                }
            }
    }

    private fun classHasResilient(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
    ): Boolean {
        if (classDeclaration.annotations.any { it.shortName.asString() == C.GHOST_RESILIENT }) {
            return true
        }
        return propertiesModel.any { it.isResilient }
    }

    private fun yamlIncompatibilityReason(prop: GhostPropertyModel): String? {
        if (prop.isContextual || prop.customDecoder != null || prop.customEncoder != null) {
            return C.STR_ERR_YAML_CUSTOM_CODEC
        }
        if (prop.wrappedSourceKeys != null || prop.flattenPath != null || prop.wrapPath != null) {
            return C.STR_ERR_YAML_STRUCTURAL
        }
        if (prop.type.isRawJson()) {
            return C.STR_ERR_YAML_RAW_JSON
        }
        if (!prop.isProto && prop.type.isByteArray()) {
            return C.STR_ERR_YAML_BYTE_ARRAY
        }
        if (prop.isGhost) {
            return C.STR_ERR_YAML_NESTED_GHOST
        }
        return null
    }

    private fun propertyDisablesYamlCodegen(prop: GhostPropertyModel): Boolean {
        if (prop.isContextual || prop.customDecoder != null || prop.customEncoder != null) {
            return true
        }
        if (prop.wrappedSourceKeys != null || prop.flattenPath != null || prop.wrapPath != null) {
            return true
        }
        if (prop.isSealedClass) {
            return true
        }
        if (prop.isGhost) {
            return true
        }
        if (prop.listInnerIsGhost || prop.mapValueIsGhost) {
            return true
        }
        if (prop.type.containsYamlIncompatibleType(prop.isProto)) {
            return true
        }
        val valueClassProperty = prop.valueClassProperty
        if (valueClassProperty != null &&
            valueClassProperty.type.containsYamlIncompatibleType(prop.isProto)
        ) {
            return true
        }
        return false
    }

    /**
     * Registers the generated serializer for [classDeclaration], also mapping any sealed
     * subclasses to the same serializer, and records the originating file for incremental
     * compilation.
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
        val serializerType = ClassName(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER)
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
            .addSuperinterface(ClassName(C.PKG_CONTRACT, C.STR_GHOST_REGISTRY))

        val chunks = entries.chunked(C.REGISTRY_CHUNK_SIZE)

        generateSerializersMapProperty(registrySpec, chunks, entries, mapType)
        generateGetSerializerMethod(registrySpec, chunks, entries, serializerType, type)
        generateShardMethods(registrySpec, chunks, mapType, serializerType)
        generateMetadataMethodsAndCompanion(registrySpec, entries.size, mapType)

        writeRegistryFile(registrySpec.build())
    }

    /**
     * Generates the lazily-initialized full serializers map property for the registry.
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
                    .addMember(C.STR_FORMAT_S, C.STR_UNCHECKED_CAST)
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
     */
    private fun writeRegistryFile(registrySpec: TypeSpec) {
        GeneratedSourceTrimmer.write(
            fileSpec = FileSpec.builder(C.STR_GENERATED_PKG, registryClassName)
                .addType(registrySpec)
                .build(),
            codeGenerator = codeGenerator,
            dependencies = Dependencies(aggregating = true, *originatingFiles.toTypedArray()),
        )
    }

    /**
     * Generates a KotlinPoet [CodeBlock] mapping class types to their serializer instances.
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
     * Generates a `when (clazz)` lookup expression mapping classes to serializer instances.
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
