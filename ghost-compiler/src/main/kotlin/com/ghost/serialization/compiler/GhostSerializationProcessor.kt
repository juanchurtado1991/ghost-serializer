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
        val moduleName = options["ghost.moduleName"]
            ?.replace(STR_DASH, STR_UNDERSCORE)
            ?.replace(STR_DOT, STR_UNDERSCORE)
            ?: STR_DEFAULT

        "${REGISTRY_CLASS_NAME}_$moduleName"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(ANNOTATION_NAME)
        val validClasses = symbols.filterIsInstance<KSClassDeclaration>().toList()
        val unableToProcess = symbols.filterNot { it is KSClassDeclaration }

        validClasses.forEach { classDeclaration -> processClass(classDeclaration) }
        if (validClasses.isNotEmpty()) {
            generateModuleRegistry()
            generateProGuardRules()
            generateServiceFile()
            if (!generateMoshiAdapters) {
                logger.info("$LOG_PREFIX$STR_LOG_MOSHI_SKIPPED")
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
            
            if (processedFiles.contains(fullFileName)) {
                return
            }
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
                "${classDeclaration.toClassName().simpleNames.joinToString("_")}$STR_SERIALIZER_SUFFIX"
            )
            classToSerializer[classDeclaration.toClassName()] = serializerClassName

            if (classDeclaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.SEALED)) {
                classDeclaration.getSealedSubclasses().forEach { subclass ->
                    classToSerializer[subclass.toClassName()] = serializerClassName
                }
            }
            classDeclaration.containingFile?.let { originatingFiles.add(it) }
            logger.info("$LOG_PREFIX$STR_LOG_OPTIMIZED$className")
        } catch (e: Exception) {
            logger.error("$LOG_PREFIX$STR_LOG_CRITICAL$className$STR_COLON_SPACE${e.stackTraceToString()}")
        }
    }

    /**
     * Generates a static registry with O(1) lazy lookup.
     * Replaces reflection and avoids global Map allocation at startup.
     */
    private fun generateModuleRegistry() {
        val serializerType = ClassName(STR_CONTRACT_PKG, STR_GHOST_SERIALIZER)
        val kClassType = ClassName(STR_REFLECT_PKG, STR_KCLASS)
        val t = TypeVariableName(STR_TYPE_T, Any::class)

        val mapType = ClassName(STR_COLLECTIONS_PKG, STR_MAP)
            .parameterizedBy(kClassType.parameterizedBy(STAR), serializerType.parameterizedBy(STAR))

        val mapBuilder = CodeBlock.builder().add(STR_MAP_OF)
        val entries = classToSerializer.entries.toList()
        entries.forEachIndexed { index, entry ->
            mapBuilder.add(STR_MAP_ENTRY, entry.key, entry.value)
            if (index < entries.size - 1) mapBuilder.add(STR_COMMA_NEWLINE)
        }
        mapBuilder.add(STR_NEWLINE_PAREN)

        val serializersProperty = PropertySpec.builder(STR_PROP_SERIALIZERS, mapType)
            .addModifiers(KModifier.PRIVATE)
            .initializer(mapBuilder.build())
            .build()

        val getMethod = FunSpec.builder(STR_FUN_GET_SERIALIZER)
            .addTypeVariable(t)
            .addParameter(STR_PARAM_CLAZZ, KClass::class.asClassName().parameterizedBy(t))
            .returns(serializerType.parameterizedBy(t).copy(nullable = true))
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class).addMember(STR_FORMAT_S, STR_UNCHECKED_CAST)
                    .build()
            )
            .addStatement(STR_RETURN_SERIALIZERS, serializerType.parameterizedBy(t))
        val registryInterface = ClassName(STR_CONTRACT_PKG, STR_GHOST_REGISTRY)

        val prewarmMethod = FunSpec.builder(STR_FUN_PREWARM)
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("%L.ignore()", STR_SERIALIZERS_SIZE)
            .build()

        val registeredCountMethod = FunSpec.builder(STR_FUN_REG_COUNT)
            .addModifiers(KModifier.OVERRIDE)
            .returns(Int::class)
            .addStatement(STR_RETURN_SERIALIZERS_SIZE)
            .build()

        val getAllSerializersMethod = FunSpec.builder(STR_FUN_GET_ALL_SERIALIZERS)
            .addModifiers(KModifier.OVERRIDE)
            .returns(mapType)
            .addStatement(STR_RETURN_ALL_SERIALIZERS)
            .build()

        val registrySpec = TypeSpec.classBuilder(registryClassName)
            .addKdoc(STR_KDOC_REGISTRY)
            .addSuperinterface(registryInterface)
            .addProperty(serializersProperty)
            .addFunction(getMethod.addModifiers(KModifier.OVERRIDE).build())
            .addFunction(prewarmMethod)
            .addFunction(registeredCountMethod)
            .addFunction(getAllSerializersMethod)
            .addType(
                TypeSpec.companionObjectBuilder()
                    .addProperty(
                        PropertySpec.builder(
                            STR_INSTANCE,
                            ClassName(PACKAGE_NAME, registryClassName)
                        )
                            .initializer(
                                STR_INIT_INSTANCE,
                                ClassName(PACKAGE_NAME, registryClassName)
                            )
                            .build()
                    )
                    .build()
            )
            .build()

        FileSpec.builder(PACKAGE_NAME, registryClassName)
            .addImport("com.ghost.serialization.parser", "ignore")
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
        val rules = """
            # GhostSerialization Robust Keep Rules
            -keep class $PACKAGE_NAME.$registryClassName {
                public static ** INSTANCE;
                public *** getSerializer(...);
            }
            -keep class * implements com.ghost.serialization.contract.GhostSerializer {
                *;
            }
        """.trimIndent()

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true),
                packageName = STR_META_INF_PROGUARD,
                fileName = STR_GHOST_SERIALIZATION_FILE,
                extensionName = STR_EXT_PRO
            ).use { it.write(rules.toByteArray()) }
        } catch (e: Exception) {
            logger.warn("$LOG_PREFIX$STR_LOG_PROGUARD_WARN${e.message}")
        }
    }

    /**
     * Generates a ServiceLoader entry so the Core module can automatically
     * discover this registry at runtime.
     */
    private fun generateServiceFile() {
        val serviceName = STR_SERVICE_NAME
        val implementationName = "$PACKAGE_NAME$STR_DOT$registryClassName"

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(
                    aggregating = true,
                    *originatingFiles.toTypedArray()
                ),
                packageName = STR_META_INF_SERVICES,
                fileName = serviceName,
                extensionName = STR_EMPTY
            ).use { it.write(implementationName.toByteArray()) }
        } catch (e: Exception) {
            logger.warn("$LOG_PREFIX$STR_LOG_SERVICE_WARN${e.message}")
        }
    }

    companion object {
        private const val STR_SUFFIX_N = "n"
        private const val STR_DEFAULT = "Default"
        private const val STR_UNDERSCORE = "_"
        private const val STR_DOT = "."
        private const val STR_DASH = "-"
        private const val STR_LOG_MOSHI_SKIPPED =
            " ghost.generateMoshiAdapters=false: Moshi JsonAdapter generation skipped. Binary footprint reduced to 1 class per model."
        private const val STR_LOG_OPTIMIZED = " Successfully optimized: "
        private const val STR_LOG_CRITICAL = " Critical error processing "
        private const val STR_COLON_SPACE = ": "
        private const val STR_CONTRACT_PKG = "com.ghost.serialization.contract"
        private const val STR_GHOST_SERIALIZER = "GhostSerializer"
        private const val STR_REFLECT_PKG = "kotlin.reflect"
        private const val STR_KCLASS = "KClass"
        private const val STR_TYPE_T = "T"
        private const val STR_COLLECTIONS_PKG = "kotlin.collections"
        private const val STR_MAP = "Map"
        private const val STR_MAP_OF = "mapOf(\n"
        private const val STR_SERIALIZER_SUFFIX = "Serializer"
        private const val STR_MAP_ENTRY = "    %T::class to %T"
        private const val STR_COMMA_NEWLINE = ",\n"
        private const val STR_NEWLINE_PAREN = "\n)"
        private const val STR_PROP_SERIALIZERS = "serializers"
        private const val STR_FUN_GET_SERIALIZER = "getSerializer"
        private const val STR_PARAM_CLAZZ = "clazz"
        private const val STR_UNCHECKED_CAST = "UNCHECKED_CAST"
        private const val STR_RETURN_SERIALIZERS = "return serializers[clazz] as? %T"
        private const val STR_GHOST_REGISTRY = "GhostRegistry"
        private const val STR_FUN_PREWARM = "prewarm"
        private const val STR_SERIALIZERS_SIZE = "serializers.size"
        private const val STR_FUN_REG_COUNT = "registeredCount"
        private const val STR_RETURN_SERIALIZERS_SIZE = "return serializers.size"
        private const val STR_FUN_GET_ALL_SERIALIZERS = "getAllSerializers"
        private const val STR_RETURN_ALL_SERIALIZERS = "return serializers"
        private const val STR_KDOC_REGISTRY =
            "Generated Registry for GhostSerialization.\nProvides ultra-fast, O(1), and R8-safe serializer lookups."
        private const val STR_INSTANCE = "INSTANCE"
        private const val STR_INIT_INSTANCE = "%T()"
        private const val STR_META_INF_PROGUARD = "META-INF.proguard"
        private const val STR_GHOST_SERIALIZATION_FILE = "ghost-serialization"
        private const val STR_EXT_PRO = "pro"
        private const val STR_LOG_PROGUARD_WARN = " Could not generate ProGuard rules: "
        private const val STR_SERVICE_NAME = "com.ghost.serialization.contract.GhostRegistry"
        private const val STR_META_INF_SERVICES = "META-INF.services"
        private const val STR_EMPTY = ""
        private const val STR_LOG_SERVICE_WARN = " Could not generate ServiceLoader file: "
        private const val STR_FORMAT_S = "%S"
        private const val ANNOTATION_NAME = "com.ghost.serialization.annotations.GhostSerialization"
        private const val PACKAGE_NAME = "com.ghost.serialization.generated"
        private const val REGISTRY_CLASS_NAME = "GhostModuleRegistry"
        private const val LOG_PREFIX = ">>> [GhostSerialization]"
        const val OPTION_GENERATE_MOSHI_ADAPTERS = "ghost.generateMoshiAdapters"
    }
}