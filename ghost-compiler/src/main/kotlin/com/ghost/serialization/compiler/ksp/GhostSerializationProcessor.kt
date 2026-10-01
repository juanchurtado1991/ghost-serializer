package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.analysis.EnvelopeAnalyzer
import com.ghost.serialization.compiler.analysis.GhostAnalyzer
import com.ghost.serialization.compiler.analysis.TextChannelPlanner
import com.ghost.serialization.compiler.codegen.GhostCodeGenerator
import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer
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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC


/**
 * KSP processor for Ghost Serialization: analyzes `@GhostSerialization`-annotated classes,
 * generates their serializers, and builds a per-module registry to avoid reflection at runtime.
 */
class GhostSerializationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap()
) : SymbolProcessor {

    private val classToSerializer = mutableMapOf<ClassName, ClassName>()

    /** Origin files of processed declarations, for KSP incremental compilation dependencies. */
    private val originatingFiles = mutableSetOf<KSFile>()

    private val processedFiles = mutableSetOf<String>()

    private val analyzer = GhostAnalyzer(logger = logger)
    private val envelopeAnalyzer = EnvelopeAnalyzer(logger = logger)
    private val yamlEligibility = GhostYamlEligibility(logger = logger)

    /** Output name of the module-level registry class, e.g. `GhostRegistry_module_name`. */
    private val registryClassName: String by lazy {
        var moduleName = options[PC.OPTION_MODULE_NAME]
            ?.replace(PC.STR_DASH, CC.STR_UNDERSCORE)
            ?.replace(CC.STR_DOT, CC.STR_UNDERSCORE)
            ?: PC.STR_DEFAULT_NAME

        // Append _Test when in a test source set, to avoid colliding with the main registry.
        if (moduleName == PC.STR_DEFAULT_NAME) {
            val isTest = TestSourceSetDetection.isTestCompilation(
                options = options,
                filePaths = originatingFiles.map { it.filePath },
            )
            if (isTest) {
                moduleName += PC.STR_TEST_SUFFIX
            }
        }

        PC.STR_REGISTRY_PREFIX + CC.STR_UNDERSCORE + moduleName
    }

    /** @return Symbols that could not be processed in this round (deferred to KSP's next round). */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = (resolver.getSymbolsWithAnnotation(CC.STR_ANNOTATION_SERIALIZATION) +
                resolver.getSymbolsWithAnnotation(CC.STR_ANNOTATION_PROTO_SERIALIZATION)).toSet()
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
                    "${PC.STR_LOG_PREFIX}${PC.STR_LOG_CRITICAL}${
                        classDeclaration.simpleName.asString()
                    }${PC.STR_COLON_SPACE}${e.message ?: e.toString()}",
                    classDeclaration,
                )
                null
            }
        }

        val textChannelByClass = TextChannelPlanner.plan(
            analyzed = analyzed,
            moduleTextChannelOverride = when (options[PC.OPTION_TEXT_CHANNEL]) {
                CC.STR_TRUE -> true
                CC.STR_FALSE -> false
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

        yamlEligibility.reportOrphanGhostYamlSerialization(resolver = resolver)

        if (validClasses.isNotEmpty()) {
            GhostModuleRegistryGenerator(
                codeGenerator = codeGenerator,
                registryClassName = registryClassName,
                originatingFiles = originatingFiles
            )
                .generate(classToSerializer = classToSerializer)
            val buildTooling = GhostBuildToolingWriter(
                codeGenerator = codeGenerator, logger = logger, registryClassName = registryClassName, originatingFiles = originatingFiles
            )
            buildTooling.generateProGuardRules()
            buildTooling.generateServiceFile()
        }

        return unableToProcess.toList()
    }

    /** @return The generated serializer's [ClassName], or null if this file was already processed. */
    private fun generateSerializer(
        classDeclaration: KSClassDeclaration,
        propertiesModel: List<GhostPropertyModel>,
        textChannel: Boolean,
        resolver: Resolver,
    ): ClassName? {
        val envelopeModel = envelopeAnalyzer.analyze(classDeclaration, propertiesModel)
        val hasYaml = yamlEligibility.shouldGenerateYaml(
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
                .joinToString(CC.STR_UNDERSCORE)
                    + CC.STR_SERIALIZER_SUFFIX
        )
    }

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

            registerSerializer(classDeclaration = classDeclaration, serializerClassName = serializerClassName)

            logger.info(
                "${
                    PC.STR_LOG_PREFIX
                }${
                    PC.STR_LOG_OPTIMIZED
                }$className"
            )
        } catch (e: Exception) {
            logger.error(
                "${PC.STR_LOG_PREFIX}${PC.STR_LOG_CRITICAL}$className${PC.STR_COLON_SPACE}${e.message ?: e.toString()}",
                classDeclaration
            )
        }
    }

    /** Registers the serializer for [classDeclaration] and its sealed subclasses, if any. */
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
}
