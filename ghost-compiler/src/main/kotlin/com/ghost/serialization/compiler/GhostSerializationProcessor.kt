package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main KSP Processor for Ghost Serialization.
 * It analyzes classes annotated with @GhostSerialization, generates their respective
 * serializers, and builds a global registry to avoid reflection in runtime.
 */
class GhostSerializationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    private val generateMoshiAdapters: Boolean = options[OPTION_GENERATE_MOSHI_ADAPTERS]
        ?.toBoolean()
        ?: false

    private val classToSerializer = mutableMapOf<ClassName, ClassName>()
    private val originatingFiles = mutableSetOf<com.google.devtools.ksp.symbol.KSFile>()
    private val processedFiles = mutableSetOf<String>()
    private val analyzer = GhostAnalyzer(logger)

    private val registryClassName: String by lazy {
        // Use the module name provided by KSP or fallback to a stable suffix
        val moduleName = options[C.OPTION_MODULE_NAME]
            ?.replace(C.STR_DASH, C.STR_UNDERSCORE)
            ?.replace(C.STR_DOT, C.STR_UNDERSCORE)
            ?: C.STR_DEFAULT_NAME

        C.STR_REGISTRY_PREFIX + C.STR_UNDERSCORE + moduleName
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(C.STR_ANNOTATION_SERIALIZATION)
        val validClasses = symbols.filterIsInstance<KSClassDeclaration>().toList()
        val unableToProcess = symbols.filterNot { it is KSClassDeclaration }

        validClasses.forEach { classDeclaration -> processClass(classDeclaration) }
        if (validClasses.isNotEmpty()) {
            generateModuleRegistry()
            generateProGuardRules()
            generateServiceFile()
            if (!generateMoshiAdapters) {
                logger.info("${C.STR_LOG_PREFIX}${C.STR_LOG_MOSHI_SKIPPED}")
            }
        }

        return unableToProcess.toList()
    }

    private fun processClass(classDeclaration: KSClassDeclaration) {
        val className = classDeclaration.simpleName.asString()
        try {
            val propertiesModel = analyzer.analyze(classDeclaration)

            val fileGenerator = GhostCodeGenerator(
                classDeclaration = classDeclaration,
                properties = propertiesModel
            )

            val fileSpec = fileGenerator.createSpec()
            val fullFileName = "${classDeclaration.packageName.asString()}.${fileSpec.name}"

            if (processedFiles.contains(fullFileName)) return
            processedFiles.add(fullFileName)

            fileSpec.writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    aggregating = false,
                    classDeclaration.containingFile!!
                )
            )

            val serializerClassName = ClassName(
                classDeclaration.packageName.asString(),
                classDeclaration.toClassName().simpleNames.joinToString(C.STR_UNDERSCORE) + C.STR_SERIALIZER_SUFFIX
            )
            classToSerializer[classDeclaration.toClassName()] = serializerClassName

            if (classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.SEALED)) {
                classDeclaration.getSealedSubclasses().forEach { subclass ->
                    classToSerializer[subclass.toClassName()] = serializerClassName
                }
            }
            classDeclaration.containingFile?.let { originatingFiles.add(it) }
            logger.info("${C.STR_LOG_PREFIX}${C.STR_LOG_OPTIMIZED}$className")
        } catch (e: Exception) {
            logger.error("${C.STR_LOG_PREFIX}${C.STR_LOG_CRITICAL}$className${C.STR_COLON_SPACE}${e.stackTraceToString()}")
        }
    }

    /**
     * Generates a registry with:
     * - getSerializer as a `when` chain so the JVM only initializes serializers for the branch taken
     *   (fast cold start vs eager `mapOf` that touches every companion on registry construction).
     * - A lazy `allSerializers` map for getAllSerializers / prewarm only when the full graph is needed.
     */
    private fun generateModuleRegistry() {
        val serializerType = ClassName(C.STR_CONTRACT_PKG, C.STR_GHOST_SERIALIZER)
        val kClassType = ClassName(C.STR_REFLECT_PKG, C.STR_KCLASS)
        val type = TypeVariableName(C.STR_TYPE_T, Any::class)

        val mapType = ClassName(
            C.STR_COLLECTIONS_PKG,
            C.STR_MAP
        )
            .parameterizedBy(
                kClassType.parameterizedBy(STAR),
                serializerType.parameterizedBy(STAR)
            )

        val entries = classToSerializer.entries.toList()
            .sortedBy { it.key.canonicalName }

        val mapBuilder = CodeBlock.builder().add(C.STR_MAP_OF)
        entries.forEachIndexed { index, entry ->
            mapBuilder.add(C.STR_MAP_ENTRY, entry.key, entry.value)
            if (index < entries.size - 1) mapBuilder.add(C.STR_COMMA_NEWLINE)
        }
        mapBuilder.add(C.STR_NEWLINE_PAREN)

        val allSerializersDelegate = CodeBlock
            .builder()
            .add(C.STR_LAZY_START)
            .indent()
            .add(mapBuilder.build())
            .unindent()
            .add(C.STR_CURLY_CLOSE)
            .build()

        val allSerializersProperty = PropertySpec
            .builder(C.STR_PROP_SERIALIZERS_MAP, mapType)
            .addModifiers(KModifier.PRIVATE)
            .delegate(allSerializersDelegate)
            .build()

        val whenBody = CodeBlock.builder()
            .add(C.STR_WHEN_CLAZZ_START)
        entries.forEach { entry ->
            whenBody.add(C.STR_WHEN_ENTRY, entry.key, entry.value)
        }
        whenBody.add(C.STR_WHEN_ELSE_NULL)
        whenBody.add(
            C.STR_WHEN_CLOSE_CAST,
            serializerType
                .parameterizedBy(type)
                .copy(nullable = true)
        )

        val getMethod = FunSpec.builder(C.STR_FUN_GET_SERIALIZER)
            .addTypeVariable(type)
            .addParameter(
                C.STR_PARAM_CLAZZ,
                KClass::class.asClassName().parameterizedBy(type)
            )
            .returns(
                serializerType
                    .parameterizedBy(type)
                    .copy(nullable = true)
            )
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember(C.STR_FORMAT_S, C.STR_UNCHECKED_CAST)
                    .build()
            )
            .addCode(whenBody.build())

        val registryInterface = ClassName(
            C.STR_CONTRACT_PKG,
            C.STR_GHOST_REGISTRY
        )

        val prewarmMethod = FunSpec.builder(C.STR_FUN_PREWARM)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement(C.STR_IGNORE_CALL, C.STR_SERIALIZERS_SIZE)
            .build()

        val registeredCountMethod = FunSpec.builder(C.STR_FUN_REG_COUNT)
            .addModifiers(KModifier.OVERRIDE)
            .returns(Int::class)
            .addStatement(C.STR_RETURN_L, entries.size)
            .build()

        val getAllSerializersMethod = FunSpec.builder(C.STR_FUN_GET_ALL_SERIALIZERS)
            .addModifiers(KModifier.OVERRIDE)
            .returns(mapType)
            .addStatement(C.STR_RETURN_SERIALIZERS)
            .build()

        val registrySpec = TypeSpec.classBuilder(registryClassName)
            .addKdoc(C.STR_KDOC_REGISTRY)
            .addSuperinterface(registryInterface)
            .addProperty(allSerializersProperty)
            .addFunction(
                getMethod
                    .addModifiers(KModifier.OVERRIDE)
                    .build()
            )
            .addFunction(prewarmMethod)
            .addFunction(registeredCountMethod)
            .addFunction(getAllSerializersMethod)
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder(
                            C.STR_INSTANCE,
                            ClassName(
                                C.STR_GENERATED_PKG,
                                registryClassName
                            )
                        )
                            .initializer(
                                C.STR_INIT_INSTANCE,
                                ClassName(
                                    C.STR_GENERATED_PKG,
                                    registryClassName
                                )
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        FileSpec.builder(C.STR_GENERATED_PKG, registryClassName)
            .addImport(C.PKG_PARSER, C.STR_IGNORE)
            .addType(registrySpec)
            .build()
            .writeTo(
                codeGenerator,
                Dependencies(aggregating = true, *originatingFiles.toTypedArray())
            )
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
            logger.warn("${C.STR_LOG_PREFIX}${C.STR_LOG_PROGUARD_WARN}${e.message}")
        }
    }

    /**
     * Generates a ServiceLoader entry so the Core module can automatically
     * discover this registry at runtime.
     */
    private fun generateServiceFile() {
        val serviceName = C.STR_SERVICE_REGISTRY
        val implementationName = "${C.STR_GENERATED_PKG}${C.STR_DOT}$registryClassName"

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
            logger.warn("${C.STR_LOG_PREFIX}${C.STR_LOG_SERVICE_WARN}${e.message}")
        }
    }

    companion object {
        const val OPTION_GENERATE_MOSHI_ADAPTERS = C.OPTION_MOSHI
    }
}