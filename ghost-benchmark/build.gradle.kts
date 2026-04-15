plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.ghost.benchmark.GhostBenchmarkKt")
}

tasks.named<JavaExec>("run") {
    dependsOn(":ghost-core:compileTestKotlinJvm", ":ghost-integration-test:testClasses")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":ghost-api"))
    implementation(project(":ghost-core"))
    implementation(libs.gson)
    implementation(libs.moshi)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    
    // JUnit for Automated Safety Audit (Separated according to Senior Audit)
    implementation(libs.junit.launcher)
    implementation("org.junit.platform:junit-platform-engine:1.11.3")
    runtimeOnly(libs.junit.engine)
    runtimeOnly("org.junit.vintage:junit-vintage-engine:5.11.3")
    runtimeOnly("org.jetbrains.kotlin:kotlin-test-junit5:2.1.0")
    runtimeOnly(libs.kotlin.test)
    
    // Include test classes for auto-detection (Safe Root-Relative paths)
    runtimeOnly(files("${rootDir.absolutePath}/ghost-core/build/classes/kotlin/jvm/test"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/classes/kotlin/test"))
    runtimeOnly(files("${rootDir.absolutePath}/ghost-integration-test/build/classes/kotlin/main"))
    
    ksp(project(":ghost-compiler"))
    ksp(libs.moshi.kotlin.codegen)
}
