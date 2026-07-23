import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}


gradlePlugin {
    website.set("https://github.com/juanchurtado1991/ghost-serializer")
    vcsUrl.set("https://github.com/juanchurtado1991/ghost-serializer.git")
    
    plugins {
        create("ghostPlugin") {
            id = "com.ghostserializer.ghost"
            implementationClass = "com.ghost.gradle.GhostPlugin"
            displayName = "Ghost Serialization Plugin"
            description = "Auto-configures Kotlin Symbol Processing (KSP) and dependencies for Ghost Serialization across Android, JVM, and Kotlin Multiplatform."
            tags.set(listOf("kotlin", "serialization", "ksp", "multiplatform", "android"))
        }
    }
}

dependencies {
    // Need this to access Kotlin Multiplatform and Android Gradle Plugin APIs in the plugin
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlin.gradle.plugin)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("kotlinVersion", libs.versions.kotlin.sdk.get())
    systemProperty("kspVersion", libs.versions.ksp.get())
    systemProperty("ghostVersion", libs.versions.publish.version.get())
    dependsOn(
        ":ghost-api:publishToMavenLocal",
        ":ghost-serialization:publishToMavenLocal",
        ":ghost-compiler:publishToMavenLocal",
    )
}
