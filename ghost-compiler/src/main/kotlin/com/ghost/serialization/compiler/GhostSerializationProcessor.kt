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
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Main KSP Processor for Ghost Serialization.
 * It analyzes classes annotated with @GhostSerialization, generates their respective
 * serializers, and builds a global registry to avoid reflection in runtime.
 */
class GhostSerializationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    private val generateMoshiAdapters: Boolean =
        options[OPTION_GENERATE_MOSHI_ADAPTERS]?.toBoolean() ?: false

    private val processedClasses = mutableListOf<ClassName>()
    private val originatingFiles = mutableSetOf<com.google.devtools.ksp.symbol.KSFile>()
    private val analyzer = GhostAnalyzer(logger)

    private val registryClassName: String by lazy {
        val suffix = processedClasses.firstOrNull()?.packageName?.hashCode()?.let { 
            if (it < 0) "n${-it}" else "$it" 
        } ?: "Default"
        "${REGISTRY_CLASS_NAME}_$suffix"
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
                logger.info("$LOG_PREFIX ghost.generateMoshiAdapters=false: Moshi JsonAdapter generation skipped. Binary footprint reduced to 1 class per model.")
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

            fileGenerator.createSpec().writeTo(
                codeGenerator = codeGenerator,
                dependencies = Dependencies(
                    aggregating = false,
                    classDeclaration.containingFile!!
                )
            )

            processedClasses.add(classDeclaration.toClassName())
            classDeclaration.containingFile?.let { originatingFiles.add(it) }
            logger.info("$LOG_PREFIX Successfully optimized: $className")
        } catch (e: Exception) {
            logger.error("$LOG_PREFIX Critical error processing $className: ${e.stackTraceToString()}")
        }
    }

    /**
     * Generates a static registry with O(1) lazy lookup.
     * Replaces reflection and avoids global Map allocation at startup.
     */
    private fun generateModuleRegistry() {
        val serializerType = ClassName("com.ghost.serialization.core.contract", "GhostSerializer")
        val kClassType = ClassName("kotlin.reflect", "KClass")
        val t = TypeVariableName("T", Any::class)

        // Generate the private Map property
        val mapType = ClassName("kotlin.collections", "Map")
            .parameterizedBy(kClassType.parameterizedBy(STAR), serializerType.parameterizedBy(STAR))
        
        val mapBuilder = CodeBlock.builder().add("mapOf(\n")
        processedClasses.forEachIndexed { index, className ->
            val serializerName = ClassName(className.packageName, "${className.simpleName}Serializer")
            mapBuilder.add("    %T::class to %T", className, serializerName)
            if (index < processedClasses.size - 1) mapBuilder.add(",\n")
        }
        mapBuilder.add("\n)")

        val serializersProperty = PropertySpec.builder("serializers", mapType)
            .addModifiers(KModifier.PRIVATE)
            .initializer(mapBuilder.build())
            .build()

        val getMethod = FunSpec.builder("getSerializer")
            .addTypeVariable(t)
            .addParameter("clazz", KClass::class.asClassName().parameterizedBy(t))
            .returns(serializerType.parameterizedBy(t).copy(nullable = true))
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "UNCHECKED_CAST").build())
            .addStatement("return serializers[clazz] as? %T", serializerType.parameterizedBy(t))
        val registryInterface = ClassName("com.ghost.serialization.core.contract", "GhostRegistry")

        val prewarmMethod = FunSpec.builder("prewarm")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("serializers.size")
            .build()

        val registeredCountMethod = FunSpec.builder("registeredCount")
            .addModifiers(KModifier.OVERRIDE)
            .returns(Int::class)
            .addStatement("return serializers.size")
            .build()

        val registrySpec = TypeSpec.classBuilder(registryClassName)
            .addKdoc("Generated Registry for GhostSerialization.\nProvides ultra-fast, O(1), and R8-safe serializer lookups.")
            .addSuperinterface(registryInterface)
            .addProperty(serializersProperty)
            .addFunction(getMethod.addModifiers(KModifier.OVERRIDE).build())
            .addFunction(prewarmMethod)
            .addFunction(registeredCountMethod)
            .addType(TypeSpec.companionObjectBuilder()
                .addProperty(PropertySpec.builder("INSTANCE", ClassName(PACKAGE_NAME, registryClassName))
                    .initializer("%T()", ClassName(PACKAGE_NAME, registryClassName))
                    .build())
                .build())
            .build()

        FileSpec.builder(PACKAGE_NAME, registryClassName)
            .addType(registrySpec)
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = true, *originatingFiles.toTypedArray()))
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
            -keep class * implements com.ghost.serialization.core.contract.GhostSerializer {
                *;
            }
        """.trimIndent()

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true),
                packageName = "META-INF.proguard",
                fileName = "ghost-serialization",
                extensionName = "pro"
            ).use { it.write(rules.toByteArray()) }
        } catch (e: Exception) {
            logger.warn("$LOG_PREFIX Could not generate ProGuard rules: ${e.message}")
        }
    }

    /**
     * Generates a ServiceLoader entry so the Core module can automatically
     * discover this registry at runtime.
     */
    private fun generateServiceFile() {
        val serviceName = "com.ghost.serialization.core.contract.GhostRegistry"
        val implementationName = "$PACKAGE_NAME.$registryClassName"
        
        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true, *originatingFiles.toTypedArray()),
                packageName = "META-INF.services",
                fileName = serviceName,
                extensionName = ""
            ).use { it.write(implementationName.toByteArray()) }
        } catch (e: Exception) {
            logger.warn("$LOG_PREFIX Could not generate ServiceLoader file: ${e.message}")
        }
    }

    companion object {
        private const val ANNOTATION_NAME = "com.ghost.serialization.annotations.GhostSerialization"
        private const val PACKAGE_NAME = "com.ghost.serialization.generated"
        private const val REGISTRY_CLASS_NAME = "GhostModuleRegistry"
        private const val LOG_PREFIX = ">>> [GhostSerialization]"
        const val OPTION_GENERATE_MOSHI_ADAPTERS = "ghost.generateMoshiAdapters"
    }
}