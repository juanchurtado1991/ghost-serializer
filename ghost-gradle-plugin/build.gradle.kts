plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

apply(from = "../publish.gradle.kts")

gradlePlugin {
    website.set("https://github.com/juanchurtado1991/GhostSerialization")
    vcsUrl.set("https://github.com/juanchurtado1991/GhostSerialization.git")
    
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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("kotlinVersion", libs.versions.kotlin.get())
}
