plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
        // Symbol owners are only read from IrGenerationExtension.generate, after IR construction.
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    // Provided by the consumer's Kotlin compiler at build time; never bundled.
    compileOnly(libs.kotlin.compiler.embeddable)

    testImplementation(libs.kotlin.compiler.embeddable)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.compile.testing)
    testImplementation(libs.kotlin.compile.testing.ksp)
    // End-to-end check that the plugin finds what the real KSP processor generates.
    testImplementation(project(":ghost-compiler"))
    testImplementation(project(":ghost-serialization"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
