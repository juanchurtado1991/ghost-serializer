plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":ghost-serialization"))
    implementation(project(":ghost-protobuf"))

    compileOnly(libs.spring.boot.starter.web)
    compileOnly(libs.spring.boot.starter.webflux)

    implementation(libs.okio)

    kspTest(project(":ghost-compiler"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.webflux)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.reactor.test)
}

ksp {
    arg("ghost.moduleName", "spring_starter_test")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
